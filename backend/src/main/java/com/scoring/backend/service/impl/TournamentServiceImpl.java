package com.scoring.backend.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentMatchAccessVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.domain.vo.TournamentTeamsVO;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentFavoriteMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.TournamentService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl implements TournamentService {

    private static final int SPORT_BADMINTON = 0;
    private static final int SPORT_VOLLEYBALL = 1;
    private static final int TYPE_KNOCKOUT = 0;
    private static final int TYPE_GROUP = 1;
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;

    private static final int DEFAULT_BEST_OF = 3;
    private static final int DEFAULT_GAMES_TO_WIN = 2;
    private static final int DEFAULT_POINTS_TO_WIN = 21;
    private static final boolean DEFAULT_ENABLE_DEUCE = true;
    private static final int DEFAULT_CAP_POINT = 30;
    private static final String REFEREE_PASSWORD_PATTERN = "^\\d{6}$";
    private static final String REFEREE_HASH_SALT = "tournament_referee_password";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentFavoriteMapper tournamentFavoriteMapper;
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final UserMapper userMapper;
    private final BracketEngine bracketEngine;
    private final RoundRobinEngine roundRobinEngine;

    public TournamentServiceImpl(TournamentMapper tournamentMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 TournamentFavoriteMapper tournamentFavoriteMapper,
                                 TournamentRefereeConfigMapper tournamentRefereeConfigMapper,
                                 TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                 TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                 UserMapper userMapper,
                                 BracketEngine bracketEngine,
                                 RoundRobinEngine roundRobinEngine) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentFavoriteMapper = tournamentFavoriteMapper;
        this.tournamentRefereeConfigMapper = tournamentRefereeConfigMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.userMapper = userMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTournament(String creatorUserId, CreateTournamentReq req) {
        if (StrUtil.isBlank(creatorUserId)) {
            throw new IllegalArgumentException("请先登录");
        }
        requireCompletedProfile(creatorUserId);
        if (req == null || StrUtil.isBlank(req.getName())) {
            throw new IllegalArgumentException("赛事名称不能为空");
        }
        if (isVolleyballRequest(req)) {
            return createVolleyballTournament(creatorUserId, req);
        }
        if (CollUtil.isEmpty(req.getPlayers())) {
            throw new IllegalArgumentException("选手列表不能为空");
        }

        List<CreateTournamentReq.PlayerEntry> entries = req.getPlayers().stream()
                .filter(p -> p != null && StrUtil.isNotBlank(p.getName()))
                .map(p -> {
                    CreateTournamentReq.PlayerEntry clean = new CreateTournamentReq.PlayerEntry();
                    clean.setName(p.getName().trim());
                    clean.setSeed(p.getSeed());
                    return clean;
                })
                .collect(Collectors.toList());

        if (entries.size() < 2) {
            throw new IllegalArgumentException("至少需要2名选手");
        }

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_BADMINTON);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        applyRule(tournament, req.getRule());
        applyTournamentType(tournament, req, entries.size());
        tournamentMapper.insert(tournament);

        saveRefereeConfigIfPresent(tournament.getId(), req.getRefereePassword());

        List<Player> players = buildPlayers(tournament.getId(), entries);
        if (TYPE_GROUP == tournament.getTournamentType()) {
            int groupCount = tournament.getKnockoutSlots() / tournament.getQualifiersPerGroup();
            assignGroups(players, groupCount);
        }

        for (Player player : players) {
            playerMapper.insert(player);
        }

        List<MatchRecord> matches = TYPE_GROUP == tournament.getTournamentType()
                ? roundRobinEngine.generateGroupMatches(tournament.getId(), players)
                : bracketEngine.generateKnockoutBracket(tournament.getId(), players);

        for (MatchRecord matchRecord : matches) {
            matchRecordMapper.insert(matchRecord);
        }

        if (CollUtil.isNotEmpty(matches)) {
            Tournament update = new Tournament();
            update.setId(tournament.getId());
            update.setStatus(1);
            tournamentMapper.updateById(update);
        }

        return tournament.getId();
    }

    private boolean isVolleyballRequest(CreateTournamentReq req) {
        if (req == null) {
            return false;
        }
        boolean hasTeams = CollUtil.isNotEmpty(req.getTeams());
        boolean noPlayers = CollUtil.isEmpty(req.getPlayers());
        return Integer.valueOf(SPORT_VOLLEYBALL).equals(req.getSportType()) || (hasTeams && noPlayers);
    }

    private String createVolleyballTournament(String creatorUserId, CreateTournamentReq req) {
        List<CreateTournamentReq.TeamEntry> teams = normalizeTeams(req.getTeams());
        if (teams.size() < 2) {
            throw new IllegalArgumentException("至少需要2支队伍");
        }

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_VOLLEYBALL);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        applyVolleyballRule(tournament, req.getRule());
        applyTournamentType(tournament, req, teams.size());
        tournamentMapper.insert(tournament);

        saveRefereeConfigIfPresent(tournament.getId(), req.getRefereePassword());

        List<Player> participants = buildTeamParticipants(tournament.getId(), teams);
        if (TYPE_GROUP == tournament.getTournamentType()) {
            int groupCount = tournament.getKnockoutSlots() / tournament.getQualifiersPerGroup();
            assignGroups(participants, groupCount);
        }
        for (Player participant : participants) {
            playerMapper.insert(participant);
        }

        for (int i = 0; i < teams.size(); i++) {
            insertTeamMembers(tournament.getId(), participants.get(i).getId(), teams.get(i).getMembers());
        }

        List<MatchRecord> matches = TYPE_GROUP == tournament.getTournamentType()
                ? roundRobinEngine.generateGroupMatches(tournament.getId(), participants)
                : bracketEngine.generateKnockoutBracket(tournament.getId(), participants);
        for (MatchRecord matchRecord : matches) {
            matchRecordMapper.insert(matchRecord);
        }

        if (CollUtil.isNotEmpty(matches)) {
            Tournament update = new Tournament();
            update.setId(tournament.getId());
            update.setStatus(1);
            tournamentMapper.updateById(update);
        }

        return tournament.getId();
    }

    private List<CreateTournamentReq.TeamEntry> normalizeTeams(List<CreateTournamentReq.TeamEntry> rawTeams) {
        if (CollUtil.isEmpty(rawTeams)) {
            throw new IllegalArgumentException("队伍列表不能为空");
        }

        List<CreateTournamentReq.TeamEntry> teams = new ArrayList<>();
        for (CreateTournamentReq.TeamEntry team : rawTeams) {
            if (team == null || StrUtil.isBlank(team.getName())) {
                continue;
            }
            CreateTournamentReq.TeamEntry cleanTeam = new CreateTournamentReq.TeamEntry();
            cleanTeam.setName(team.getName().trim());
            cleanTeam.setSeed(team.getSeed());
            cleanTeam.setMembers(normalizeMembers(team.getMembers(), cleanTeam.getName()));
            teams.add(cleanTeam);
        }
        return teams;
    }

    private List<CreateTournamentReq.TeamMemberEntry> normalizeMembers(List<CreateTournamentReq.TeamMemberEntry> rawMembers, String teamName) {
        List<CreateTournamentReq.TeamMemberEntry> members = new ArrayList<>();
        if (rawMembers != null) {
            for (CreateTournamentReq.TeamMemberEntry member : rawMembers) {
                if (member == null || StrUtil.isBlank(member.getName())) {
                    continue;
                }
                CreateTournamentReq.TeamMemberEntry cleanMember = new CreateTournamentReq.TeamMemberEntry();
                cleanMember.setName(member.getName().trim());
                cleanMember.setJerseyNumber(member.getJerseyNumber());
                cleanMember.setLibero(Boolean.TRUE.equals(member.getLibero()));
                cleanMember.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
                members.add(cleanMember);
            }
        }
        validateTeamMembers(teamName, members);
        return members;
    }

    private void validateTeamMembers(String teamName, List<CreateTournamentReq.TeamMemberEntry> members) {
        if (members.size() < 6 || members.size() > 12) {
            throw new IllegalArgumentException(teamName + " 需要6到12名队员");
        }

        int captainCount = 0;
        Set<Integer> jerseyNumbers = new HashSet<>();
        for (CreateTournamentReq.TeamMemberEntry member : members) {
            Integer jerseyNumber = member.getJerseyNumber();
            if (jerseyNumber == null || jerseyNumber <= 0) {
                throw new IllegalArgumentException(teamName + " 存在无效球衣号码");
            }
            if (!jerseyNumbers.add(jerseyNumber)) {
                throw new IllegalArgumentException(teamName + " 存在重复球衣号码");
            }
            if (Boolean.TRUE.equals(member.getCaptain())) {
                captainCount++;
            }
        }
        if (captainCount != 1) {
            throw new IllegalArgumentException(teamName + " 必须指定1名队长");
        }
    }

    private List<Player> buildTeamParticipants(String tournamentId, List<CreateTournamentReq.TeamEntry> teams) {
        List<Player> participants = new ArrayList<>();
        for (CreateTournamentReq.TeamEntry team : teams) {
            Player participant = new Player();
            participant.setTournamentId(tournamentId);
            participant.setName(team.getName());
            participant.setSeedRank(team.getSeed());
            participants.add(participant);
        }
        return participants;
    }

    private void insertTeamMembers(String tournamentId, String participantId, List<CreateTournamentReq.TeamMemberEntry> members) {
        for (int i = 0; i < members.size(); i++) {
            CreateTournamentReq.TeamMemberEntry member = members.get(i);
            TournamentTeamMember entity = new TournamentTeamMember();
            entity.setTournamentId(tournamentId);
            entity.setParticipantId(participantId);
            entity.setName(member.getName());
            entity.setJerseyNumber(member.getJerseyNumber());
            entity.setLibero(Boolean.TRUE.equals(member.getLibero()));
            entity.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
            entity.setDisplayOrder(i + 1);
            tournamentTeamMemberMapper.insert(entity);
        }
    }

    @Override
    public List<Tournament> listTournaments(String currentUserId, String keyword) {
        LambdaQueryWrapper<Tournament> wrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(keyword)) {
            String cleanKeyword = keyword.trim();
            wrapper.and(w -> w.like(Tournament::getName, cleanKeyword).or().like(Tournament::getLocation, cleanKeyword));
            wrapper.orderByDesc(Tournament::getCreateTime);
        } else {
            wrapper.orderByDesc(Tournament::getFavoriteCount).orderByDesc(Tournament::getCreateTime);
        }
        List<Tournament> tournaments = tournamentMapper.selectList(wrapper);
        decorateTournamentFlags(tournaments, currentUserId);
        return tournaments;
    }

    @Override
    public TournamentDetailVO getTournamentDetail(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        TournamentDetailVO vo = new TournamentDetailVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        vo.setFavoriteCount(tournament.getFavoriteCount());
        vo.setCreatorUserId(tournament.getCreatorUserId());
        vo.setCreateTime(tournament.getCreateTime() == null ? null : tournament.getCreateTime().format(DATETIME_FORMATTER));
        vo.setCreator(StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId()));
        vo.setFavorite(isFavorited(currentUserId, tournament.getId()));
        fillMatchAccess(vo, tournament, currentUserId);
        return vo;
    }

    @Override
    public List<Tournament> listFavoriteTournaments(String userId) {
        List<TournamentFavorite> favorites = tournamentFavoriteMapper.selectList(
                new LambdaQueryWrapper<TournamentFavorite>()
                        .eq(TournamentFavorite::getUserId, userId)
                        .orderByDesc(TournamentFavorite::getCreateTime)
        );
        if (CollUtil.isEmpty(favorites)) {
            return List.of();
        }
        List<String> tournamentIds = favorites.stream().map(TournamentFavorite::getTournamentId).collect(Collectors.toList());
        List<Tournament> tournaments = tournamentMapper.selectList(new LambdaQueryWrapper<Tournament>().in(Tournament::getId, tournamentIds));
        Map<String, Tournament> tournamentMap = tournaments.stream().collect(Collectors.toMap(Tournament::getId, item -> item));
        List<Tournament> ordered = tournamentIds.stream()
                .map(tournamentMap::get)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
        decorateTournamentFlags(ordered, userId);
        return ordered;
    }

    @Override
    public List<Tournament> listCreatedTournaments(String userId) {
        List<Tournament> tournaments = tournamentMapper.selectList(
                new LambdaQueryWrapper<Tournament>()
                        .eq(Tournament::getCreatorUserId, userId)
                        .orderByDesc(Tournament::getCreateTime)
        );
        decorateTournamentFlags(tournaments, userId);
        return tournaments;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoriteTournament(String userId, String tournamentId) {
        requireCompletedProfile(userId);
        requireTournament(tournamentId);
        TournamentFavorite existing = tournamentFavoriteMapper.selectOne(
                new LambdaQueryWrapper<TournamentFavorite>()
                        .eq(TournamentFavorite::getUserId, userId)
                        .eq(TournamentFavorite::getTournamentId, tournamentId)
        );
        if (existing != null) {
            return;
        }
        TournamentFavorite favorite = new TournamentFavorite();
        favorite.setUserId(userId);
        favorite.setTournamentId(tournamentId);
        tournamentFavoriteMapper.insert(favorite);
        tournamentMapper.increaseFavoriteCount(tournamentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unfavoriteTournament(String userId, String tournamentId) {
        requireCompletedProfile(userId);
        requireTournament(tournamentId);
        int deleted = tournamentFavoriteMapper.delete(
                new LambdaQueryWrapper<TournamentFavorite>()
                        .eq(TournamentFavorite::getUserId, userId)
                        .eq(TournamentFavorite::getTournamentId, tournamentId)
        );
        if (deleted > 0) {
            tournamentMapper.decreaseFavoriteCount(tournamentId);
        }
    }

    @Override
    public TournamentBracketVO getBracket(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("create_time", "id")
        );
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_KNOCKOUT)
                        .orderByAsc("round_num", "match_index")
        );

        TournamentBracketVO vo = new TournamentBracketVO();
        fillBracketCommonFields(vo, tournament);
        fillMatchAccess(vo, tournament, currentUserId);
        attachTeamMembersIfNeeded(tournament, players);
        vo.setPlayers(players);
        vo.setMatches(matches);
        return vo;
    }

    @Override
    public TournamentGroupsVO getGroups(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);

        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_GROUP)
                        .orderByAsc("group_no", "round_num", "match_index")
        );

        Map<Integer, List<Player>> playersByGroup = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));
        Map<Integer, List<MatchRecord>> matchesByGroup = matches.stream()
                .filter(match -> match.getGroupNo() != null)
                .collect(Collectors.groupingBy(MatchRecord::getGroupNo));

        List<TournamentGroupsVO.GroupVO> groups = playersByGroup.keySet().stream()
                .sorted()
                .map(groupNo -> {
                    TournamentGroupsVO.GroupVO group = new TournamentGroupsVO.GroupVO();
                    group.setGroupNo(groupNo);
                    List<Player> groupPlayers = playersByGroup.getOrDefault(groupNo, List.of());
                    attachTeamMembersIfNeeded(tournament, groupPlayers);
                    group.setPlayers(groupPlayers);
                    group.setMatches(matchesByGroup.getOrDefault(groupNo, List.of()));
                    return group;
                })
                .collect(Collectors.toList());

        TournamentGroupsVO vo = new TournamentGroupsVO();
        fillGroupsCommonFields(vo, tournament);
        fillMatchAccess(vo, tournament, currentUserId);
        vo.setGroups(groups);
        return vo;
    }

    @Override
    public GroupStandingsVO getGroupStandings(String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> matches = loadGroupMatches(tournamentId);
        return buildStandingsVO(tournament, players, matches);
    }

    @Override
    public TournamentTeamsVO getTeams(String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (!Integer.valueOf(SPORT_VOLLEYBALL).equals(safeSportType(tournament))) {
            throw new IllegalArgumentException("仅排球赛事支持查看队伍");
        }

        List<Player> participants = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("create_time", "id")
        );
        attachTeamMembersIfNeeded(tournament, participants);

        TournamentTeamsVO vo = new TournamentTeamsVO();
        vo.setTournamentId(tournamentId);
        vo.setSportType(safeSportType(tournament));
        vo.setTeams(participants.stream().map(this::toTeamVO).toList());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateKnockout(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以生成淘汰赛");
        }
        if (TYPE_GROUP != tournament.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can generate knockout");
        }
        if (Boolean.TRUE.equals(tournament.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> groupMatches = loadGroupMatches(tournamentId);
        GroupStandingsVO standingsVO = buildStandingsVO(tournament, players, groupMatches);
        if (!Boolean.TRUE.equals(standingsVO.getAllGroupMatchesFinished())) {
            throw new IllegalStateException("group matches are not finished");
        }
        if (Boolean.TRUE.equals(standingsVO.getHasUnresolvedTie())) {
            throw new IllegalStateException("group ranking has unresolved tie");
        }

        List<GroupRank> qualifiers = collectQualifiers(standingsVO);
        if (qualifiers.size() != tournament.getKnockoutSlots()) {
            throw new IllegalStateException("qualifier count does not match knockout slots");
        }

        List<String> slots = buildKnockoutSlots(qualifiers, tournament.getQualifiersPerGroup());
        List<MatchRecord> knockoutMatches = bracketEngine.generateKnockoutBracketBySlots(tournamentId, slots);
        for (MatchRecord match : knockoutMatches) {
            matchRecordMapper.insert(match);
        }

        Tournament update = new Tournament();
        update.setId(tournamentId);
        update.setCurrentStage(STAGE_KNOCKOUT);
        update.setKnockoutGenerated(true);
        update.setStatus(1);
        tournamentMapper.updateById(update);
    }

    private void fillBracketCommonFields(TournamentBracketVO vo, Tournament tournament) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
    }

    private void fillGroupsCommonFields(TournamentGroupsVO vo, Tournament tournament) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
    }

    private void decorateTournamentFlags(List<Tournament> tournaments, String currentUserId) {
        if (CollUtil.isEmpty(tournaments)) {
            return;
        }
        Set<String> favoriteIds = loadFavoriteTournamentIds(currentUserId, tournaments);
        for (Tournament tournament : tournaments) {
            tournament.setFavorite(Boolean.TRUE.equals(favoriteIds.contains(tournament.getId())));
            tournament.setCreator(StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId()));
        }
    }

    private Set<String> loadFavoriteTournamentIds(String currentUserId, List<Tournament> tournaments) {
        if (StrUtil.isBlank(currentUserId) || CollUtil.isEmpty(tournaments)) {
            return Set.of();
        }
        List<String> tournamentIds = tournaments.stream().map(Tournament::getId).collect(Collectors.toList());
        return tournamentFavoriteMapper.selectList(
                new LambdaQueryWrapper<TournamentFavorite>()
                        .eq(TournamentFavorite::getUserId, currentUserId)
                        .in(TournamentFavorite::getTournamentId, tournamentIds)
        ).stream().map(TournamentFavorite::getTournamentId).collect(Collectors.toSet());
    }

    private boolean isFavorited(String currentUserId, String tournamentId) {
        if (StrUtil.isBlank(currentUserId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }
        return tournamentFavoriteMapper.selectCount(
                new LambdaQueryWrapper<TournamentFavorite>()
                        .eq(TournamentFavorite::getUserId, currentUserId)
                        .eq(TournamentFavorite::getTournamentId, tournamentId)
        ) > 0;
    }

    private Tournament requireTournament(String tournamentId) {
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId不能为空");
        }
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        return tournament;
    }

    private void requireCompletedProfile(String userId) {
        if (StrUtil.isBlank(userId)) {
            throw new IllegalArgumentException("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!Boolean.TRUE.equals(user.getProfileCompleted())) {
            throw new IllegalArgumentException("请先完善资料后再操作");
        }
    }

    private List<Player> buildPlayers(String tournamentId, List<CreateTournamentReq.PlayerEntry> entries) {
        List<Player> players = new ArrayList<>();
        for (CreateTournamentReq.PlayerEntry entry : entries) {
            Player player = new Player();
            player.setTournamentId(tournamentId);
            player.setName(entry.getName());
            player.setSeedRank(entry.getSeed());
            players.add(player);
        }
        return players;
    }

    private void attachTeamMembersIfNeeded(Tournament tournament, List<Player> players) {
        if (!Integer.valueOf(SPORT_VOLLEYBALL).equals(safeSportType(tournament)) || CollUtil.isEmpty(players)) {
            return;
        }
        List<String> participantIds = players.stream()
                .map(Player::getId)
                .filter(id -> StrUtil.isNotBlank(id))
                .collect(Collectors.toList());
        if (CollUtil.isEmpty(participantIds)) {
            return;
        }

        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("tournament_id", tournament.getId())
                        .in("participant_id", participantIds)
                        .orderByAsc("participant_id", "display_order", "id")
        );
        Map<String, List<TournamentTeamMember>> membersByParticipant = members.stream()
                .collect(Collectors.groupingBy(TournamentTeamMember::getParticipantId));
        for (Player player : players) {
            player.setMembers(sortTeamMembers(membersByParticipant.getOrDefault(player.getId(), List.of())));
        }
    }

    private List<TournamentTeamMember> sortTeamMembers(List<TournamentTeamMember> members) {
        return (members == null ? List.<TournamentTeamMember>of() : members).stream()
                .sorted(Comparator
                        .comparing((TournamentTeamMember item) -> !Boolean.TRUE.equals(item.getCaptain()))
                        .thenComparing(item -> item.getJerseyNumber() == null ? Integer.MAX_VALUE : item.getJerseyNumber())
                        .thenComparing(item -> StrUtil.blankToDefault(item.getName(), ""))
                        .thenComparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder())
                        .thenComparing(item -> StrUtil.blankToDefault(item.getId(), "")))
                .toList();
    }

    private TournamentTeamsVO.TeamVO toTeamVO(Player participant) {
        TournamentTeamsVO.TeamVO team = new TournamentTeamsVO.TeamVO();
        team.setId(participant == null ? "" : participant.getId());
        team.setName(participant == null ? "" : participant.getName());
        List<TournamentTeamMember> members = sortTeamMembers(participant == null ? List.of() : participant.getMembers());
        team.setMemberCount(members.size());
        team.setCaptainName(members.stream()
                .filter(item -> Boolean.TRUE.equals(item.getCaptain()))
                .map(TournamentTeamMember::getName)
                .findFirst()
                .orElse("-"));
        team.setMembers(members.stream().map(this::toTeamMemberVO).toList());
        return team;
    }

    private TournamentTeamsVO.MemberVO toTeamMemberVO(TournamentTeamMember member) {
        TournamentTeamsVO.MemberVO vo = new TournamentTeamsVO.MemberVO();
        vo.setId(member.getId());
        vo.setName(member.getName());
        vo.setJerseyNumber(member.getJerseyNumber());
        vo.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
        vo.setLibero(Boolean.TRUE.equals(member.getLibero()));
        return vo;
    }

    private Integer safeSportType(Tournament tournament) {
        return tournament.getSportType() == null ? SPORT_BADMINTON : tournament.getSportType();
    }

    private void applyVolleyballRule(Tournament tournament, CreateTournamentReq.RuleConfig rule) {
        int bestOf = rule == null || rule.getBestOf() == null ? DEFAULT_BEST_OF : rule.getBestOf();
        int gamesToWin = rule == null || rule.getGamesToWin() == null ? DEFAULT_GAMES_TO_WIN : rule.getGamesToWin();
        if (bestOf != 3 && bestOf != 5) {
            throw new IllegalArgumentException("排球只支持三局两胜或五局三胜");
        }
        if (gamesToWin != bestOf / 2 + 1) {
            throw new IllegalArgumentException("gamesToWin does not match bestOf");
        }

        tournament.setBestOf(bestOf);
        tournament.setGamesToWin(gamesToWin);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(99);
    }

    private void applyRule(Tournament tournament, CreateTournamentReq.RuleConfig rule) {
        int bestOf = rule == null || rule.getBestOf() == null ? DEFAULT_BEST_OF : rule.getBestOf();
        int gamesToWin = rule == null || rule.getGamesToWin() == null ? DEFAULT_GAMES_TO_WIN : rule.getGamesToWin();
        int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_POINTS_TO_WIN : rule.getPointsToWin();
        boolean enableDeuce = rule == null || rule.getEnableDeuce() == null ? DEFAULT_ENABLE_DEUCE : rule.getEnableDeuce();
        int capPoint = rule == null || rule.getCapPoint() == null ? DEFAULT_CAP_POINT : rule.getCapPoint();

        if (bestOf != 1 && bestOf != 3 && bestOf != 5) {
            throw new IllegalArgumentException("bestOf must be 1, 3 or 5");
        }
        if (gamesToWin < 1 || gamesToWin > bestOf || gamesToWin != bestOf / 2 + 1) {
            throw new IllegalArgumentException("gamesToWin does not match bestOf");
        }
        if (pointsToWin < 1 || pointsToWin > 99) {
            throw new IllegalArgumentException("pointsToWin must be between 1 and 99");
        }
        if (capPoint <= pointsToWin || capPoint > 99) {
            throw new IllegalArgumentException("capPoint must be greater than pointsToWin and no more than 99");
        }

        tournament.setBestOf(bestOf);
        tournament.setGamesToWin(gamesToWin);
        tournament.setPointsToWin(pointsToWin);
        tournament.setEnableDeuce(enableDeuce);
        tournament.setCapPoint(capPoint);
    }

    private List<Player> loadPlayers(String tournamentId) {
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
    }

    private List<MatchRecord> loadGroupMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_GROUP)
                        .orderByAsc("group_no", "round_num", "match_index")
        );
    }

    private GroupStandingsVO buildStandingsVO(Tournament tournament, List<Player> players, List<MatchRecord> matches) {
        Map<String, Player> playerMap = players.stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, player -> player));
        Map<Integer, List<Player>> playersByGroup = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));
        Map<Integer, List<MatchRecord>> matchesByGroup = matches.stream()
                .filter(match -> match.getGroupNo() != null)
                .collect(Collectors.groupingBy(MatchRecord::getGroupNo));

        boolean allFinished = matches.stream().allMatch(match -> Integer.valueOf(2).equals(match.getStatus()));
        List<GroupStandingsVO.GroupVO> groups = new ArrayList<>();
        boolean hasUnresolvedTie = false;

        for (Integer groupNo : playersByGroup.keySet().stream().sorted().collect(Collectors.toList())) {
            List<Standing> standings = buildGroupStandings(
                    playersByGroup.getOrDefault(groupNo, List.of()),
                    matchesByGroup.getOrDefault(groupNo, List.of()),
                    playerMap,
                    tournament.getQualifiersPerGroup()
            );
            if (standings.stream().anyMatch(standing -> standing.tieUnresolved)) {
                hasUnresolvedTie = true;
            }

            GroupStandingsVO.GroupVO group = new GroupStandingsVO.GroupVO();
            group.setGroupNo(groupNo);
            group.setStandings(standings.stream().map(this::toStandingVO).collect(Collectors.toList()));
            groups.add(group);
        }

        GroupStandingsVO vo = new GroupStandingsVO();
        vo.setId(tournament.getId());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setAllGroupMatchesFinished(allFinished);
        vo.setHasUnresolvedTie(hasUnresolvedTie);
        vo.setGroups(groups);
        return vo;
    }

    private List<Standing> buildGroupStandings(List<Player> players,
                                               List<MatchRecord> matches,
                                               Map<String, Player> playerMap,
                                               Integer qualifiersPerGroup) {
        Map<String, Standing> standingMap = new HashMap<>();
        for (Player player : players) {
            Standing standing = new Standing();
            standing.playerId = player.getId();
            standing.playerName = player.getName();
            standing.seedRank = player.getSeedRank();
            standingMap.put(player.getId(), standing);
        }

        Map<String, String> h2hWinner = new HashMap<>();
        for (MatchRecord match : matches) {
            if (!Integer.valueOf(2).equals(match.getStatus()) || StrUtil.isBlank(match.getWinnerId())) {
                continue;
            }
            Standing left = standingMap.get(match.getLeftPlayerId());
            Standing right = standingMap.get(match.getRightPlayerId());
            if (left == null || right == null) {
                continue;
            }

            boolean leftWon = match.getWinnerId().equals(match.getLeftPlayerId());
            if (leftWon) {
                left.matchWins++;
                right.matchLosses++;
            } else {
                right.matchWins++;
                left.matchLosses++;
            }
            left.gameWins += safeInt(match.getLeftGameWins());
            left.gameLosses += safeInt(match.getRightGameWins());
            right.gameWins += safeInt(match.getRightGameWins());
            right.gameLosses += safeInt(match.getLeftGameWins());
            applyPointStats(match, left, right);
            h2hWinner.put(pairKey(match.getLeftPlayerId(), match.getRightPlayerId()), match.getWinnerId());
        }

        List<Standing> standings = new ArrayList<>(standingMap.values());
        standings.sort((a, b) -> compareStanding(a, b, h2hWinner));
        markRanksAndTies(standings, qualifiersPerGroup == null ? 0 : qualifiersPerGroup, h2hWinner);
        return standings;
    }

    private void applyPointStats(MatchRecord match, Standing left, Standing right) {
        if (StrUtil.isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private int compareStanding(Standing a, Standing b, Map<String, String> h2hWinner) {
        int result = Integer.compare(b.matchWins, a.matchWins);
        if (result != 0) return result;
        result = Integer.compare(b.netGames(), a.netGames());
        if (result != 0) return result;
        result = Integer.compare(b.netPoints(), a.netPoints());
        if (result != 0) return result;

        String winner = h2hWinner.get(pairKey(a.playerId, b.playerId));
        if (winner != null) {
            if (winner.equals(a.playerId)) return -1;
            if (winner.equals(b.playerId)) return 1;
        }
        return a.playerName.compareTo(b.playerName);
    }

    private void markRanksAndTies(List<Standing> standings, int qualifiersPerGroup, Map<String, String> h2hWinner) {
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            standing.rank = i + 1;
            standing.qualified = i < qualifiersPerGroup;
        }

        Map<String, List<Standing>> tiedByStats = standings.stream()
                .collect(Collectors.groupingBy(standing -> standing.matchWins + ":" + standing.netGames() + ":" + standing.netPoints()));
        Set<String> unresolvedIds = new HashSet<>();
        for (List<Standing> tied : tiedByStats.values()) {
            if (tied.size() < 2) {
                continue;
            }
            boolean crossesLine = tied.stream().anyMatch(s -> s.rank <= qualifiersPerGroup)
                    && tied.stream().anyMatch(s -> s.rank > qualifiersPerGroup);
            if (!crossesLine) {
                continue;
            }
            if (tied.size() == 2 && hasHeadToHeadWinner(tied.get(0), tied.get(1), h2hWinner)) {
                continue;
            }
            tied.forEach(s -> unresolvedIds.add(s.playerId));
        }
        standings.forEach(standing -> standing.tieUnresolved = unresolvedIds.contains(standing.playerId));
    }

    private boolean hasHeadToHeadWinner(Standing left, Standing right, Map<String, String> h2hWinner) {
        String winner = h2hWinner.get(pairKey(left.playerId, right.playerId));
        return StrUtil.equals(winner, left.playerId) || StrUtil.equals(winner, right.playerId);
    }

    private GroupStandingsVO.StandingVO toStandingVO(Standing standing) {
        GroupStandingsVO.StandingVO vo = new GroupStandingsVO.StandingVO();
        vo.setPlayerId(standing.playerId);
        vo.setPlayerName(standing.playerName);
        vo.setSeedRank(standing.seedRank);
        vo.setRank(standing.rank);
        vo.setQualified(standing.qualified);
        vo.setTieUnresolved(standing.tieUnresolved);
        vo.setMatchWins(standing.matchWins);
        vo.setMatchLosses(standing.matchLosses);
        vo.setGameWins(standing.gameWins);
        vo.setGameLosses(standing.gameLosses);
        vo.setNetGames(standing.netGames());
        vo.setPointsFor(standing.pointsFor);
        vo.setPointsAgainst(standing.pointsAgainst);
        vo.setNetPoints(standing.netPoints());
        return vo;
    }

    private List<GroupRank> collectQualifiers(GroupStandingsVO standingsVO) {
        List<GroupRank> qualifiers = new ArrayList<>();
        for (GroupStandingsVO.GroupVO group : standingsVO.getGroups()) {
            for (GroupStandingsVO.StandingVO standing : group.getStandings()) {
                if (Boolean.TRUE.equals(standing.getQualified()) && !Boolean.TRUE.equals(standing.getTieUnresolved())) {
                    qualifiers.add(new GroupRank(group.getGroupNo(), standing.getRank(), standing.getPlayerId()));
                }
            }
        }
        return qualifiers;
    }

    private List<String> buildKnockoutSlots(List<GroupRank> qualifiers, int qualifiersPerGroup) {
        Map<Integer, List<GroupRank>> byRank = qualifiers.stream().collect(Collectors.groupingBy(GroupRank::rank));
        List<GroupRank> firsts = byRank.getOrDefault(1, List.of()).stream()
                .sorted(Comparator.comparingInt(GroupRank::groupNo))
                .collect(Collectors.toList());
        if (qualifiersPerGroup == 1) {
            return firsts.stream().map(GroupRank::playerId).collect(Collectors.toList());
        }

        List<GroupRank> seconds = new ArrayList<>(byRank.getOrDefault(2, List.of()).stream()
                .sorted(Comparator.comparingInt(GroupRank::groupNo).reversed())
                .collect(Collectors.toList()));
        List<String> slots = new ArrayList<>();
        for (GroupRank first : firsts) {
            int secondIndex = findOpponentIndex(seconds, first.groupNo());
            if (secondIndex < 0) {
                secondIndex = 0;
            }
            GroupRank second = seconds.remove(secondIndex);
            slots.add(first.playerId());
            slots.add(second.playerId());
        }
        return slots;
    }

    private int findOpponentIndex(List<GroupRank> seconds, Integer forbiddenGroupNo) {
        for (int i = 0; i < seconds.size(); i++) {
            if (!seconds.get(i).groupNo().equals(forbiddenGroupNo)) {
                return i;
            }
        }
        return -1;
    }

    private String pairKey(String a, String b) {
        if (a == null || b == null) {
            return "";
        }
        return a.compareTo(b) < 0 ? a + ":" + b : b + ":" + a;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private void applyTournamentType(Tournament tournament, CreateTournamentReq req, int playerCount) {
        int tournamentType = req.getTournamentType() == null ? TYPE_KNOCKOUT : req.getTournamentType();
        if (tournamentType != TYPE_KNOCKOUT && tournamentType != TYPE_GROUP) {
            throw new IllegalArgumentException("tournamentType must be 0 or 1");
        }

        tournament.setTournamentType(tournamentType);
        if (tournamentType == TYPE_KNOCKOUT) {
            tournament.setGroupSize(null);
            tournament.setKnockoutSlots(null);
            tournament.setQualifiersPerGroup(null);
            tournament.setCurrentStage(STAGE_KNOCKOUT);
            tournament.setKnockoutGenerated(true);
            return;
        }

        int knockoutSlots = req.getKnockoutSlots() == null ? 8 : req.getKnockoutSlots();
        int qualifiers = req.getQualifiersPerGroup() == null ? 2 : req.getQualifiersPerGroup();
        if (!isPowerOfTwo(knockoutSlots) || knockoutSlots < 2) {
            throw new IllegalArgumentException("knockoutSlots must be a power of two and at least 2");
        }
        if (knockoutSlots > playerCount) {
            throw new IllegalArgumentException("knockoutSlots must not exceed player count");
        }
        if (qualifiers != 1 && qualifiers != 2) {
            throw new IllegalArgumentException("qualifiersPerGroup must be 1 or 2");
        }
        if (knockoutSlots % qualifiers != 0) {
            throw new IllegalArgumentException("knockoutSlots must be divisible by qualifiersPerGroup");
        }
        int groupCount = knockoutSlots / qualifiers;
        int minGroupSize = playerCount / groupCount;
        if (groupCount < 1 || minGroupSize <= qualifiers) {
            throw new IllegalArgumentException("each group must have more players than qualifiers");
        }

        tournament.setGroupSize((int) Math.ceil(playerCount * 1.0 / groupCount));
        tournament.setKnockoutSlots(knockoutSlots);
        tournament.setQualifiersPerGroup(qualifiers);
        tournament.setCurrentStage(STAGE_GROUP);
        tournament.setKnockoutGenerated(false);
    }

    private boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }

    private void assignGroups(List<Player> players, int groupCount) {
        List<Player> ordered = players.stream()
                .sorted((a, b) -> {
                    Integer seedA = a.getSeedRank();
                    Integer seedB = b.getSeedRank();
                    if (seedA == null && seedB == null) return a.getName().compareTo(b.getName());
                    if (seedA == null) return 1;
                    if (seedB == null) return -1;
                    return seedA.compareTo(seedB);
                })
                .collect(Collectors.toList());

        int[] groupPositions = new int[groupCount];
        for (int i = 0; i < ordered.size(); i++) {
            int block = i / groupCount;
            int offset = i % groupCount;
            int groupIndex = block % 2 == 0 ? offset : groupCount - 1 - offset;
            Player player = ordered.get(i);
            player.setGroupNo(groupIndex + 1);
            player.setGroupPosition(++groupPositions[groupIndex]);
        }
    }

    // ======================== 裁判管理 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentRefereeAccessVO authenticateReferee(String userId, String tournamentId, TournamentRefereeAuthReq req) {
        requireCompletedProfile(userId);
        Tournament tournament = requireTournament(tournamentId);

        TournamentRefereeConfig config = tournamentRefereeConfigMapper.selectOne(
                new QueryWrapper<TournamentRefereeConfig>()
                        .eq("tournament_id", tournamentId)
        );

        if (config == null) {
            throw new IllegalArgumentException("该赛事未设置裁判密码");
        }

        if (!verifyPassword(req.getPassword(), config.getPasswordHash())) {
            throw new IllegalArgumentException("裁判密码错误");
        }

        // 检查是否已授权
        TournamentRefereeGrant existing = tournamentRefereeGrantMapper.selectOne(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        );

        if (existing == null) {
            TournamentRefereeGrant grant = new TournamentRefereeGrant();
            grant.setTournamentId(tournamentId);
            grant.setUserId(userId);
            tournamentRefereeGrantMapper.insert(grant);
        }

        TournamentRefereeAccessVO vo = new TournamentRefereeAccessVO();
        vo.setGranted(true);
        vo.setReferees(buildRefereeVOList(tournamentId));
        return vo;
    }

    @Override
    public List<TournamentRefereeVO> listReferees(String userId, String tournamentId) {
        requireTournament(tournamentId);
        requireCreatorOrReferee(userId, tournamentId);
        return buildRefereeVOList(tournamentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeReferee(String userId, String tournamentId, String refereeUserId) {
        Tournament tournament = requireTournament(tournamentId);

        // 只有创建者可以移除裁判
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以移除裁判");
        }

        // 不能移除创建者自己（虽然创建者不会出现在裁判列表中）
        if (StrUtil.equals(refereeUserId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("不能移除赛事创建者");
        }

        tournamentRefereeGrantMapper.delete(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", refereeUserId)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefereePassword(String userId, String tournamentId, UpdateTournamentRefereePasswordReq req) {
        Tournament tournament = requireTournament(tournamentId);

        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以修改裁判密码");
        }

        validateRefereePassword(req.getPassword());

        TournamentRefereeConfig config = tournamentRefereeConfigMapper.selectOne(
                new QueryWrapper<TournamentRefereeConfig>()
                        .eq("tournament_id", tournamentId)
        );

        if (config == null) {
            config = new TournamentRefereeConfig();
            config.setTournamentId(tournamentId);
            config.setPasswordHash(hashPassword(req.getPassword()));
            tournamentRefereeConfigMapper.insert(config);
        } else {
            config.setPasswordHash(hashPassword(req.getPassword()));
            tournamentRefereeConfigMapper.updateById(config);
        }
    }

    @Override
    public boolean canOperateVolleyballMatch(String userId, String tournamentId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            return false;
        }

        // 创建者永远可以操作
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return true;
        }

        // 检查是否为已授权裁判
        return tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        ) > 0;
    }

    // ======================== 裁判辅助方法 ========================

    private void saveRefereeConfigIfPresent(String tournamentId, String rawPassword) {
        if (StrUtil.isBlank(rawPassword)) {
            return;
        }
        validateRefereePassword(rawPassword);

        TournamentRefereeConfig config = new TournamentRefereeConfig();
        config.setTournamentId(tournamentId);
        config.setPasswordHash(hashPassword(rawPassword));
        tournamentRefereeConfigMapper.insert(config);
    }

    private void validateRefereePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("裁判密码不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("裁判密码必须为6位数字");
        }
    }

    private String hashPassword(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword + REFEREE_HASH_SALT);
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        return hashPassword(rawPassword).equals(storedHash);
    }

    private void fillMatchAccess(TournamentMatchAccessVO vo, Tournament tournament, String currentUserId) {
        if (vo == null || tournament == null) {
            return;
        }

        boolean isCreator = StrUtil.equals(currentUserId, tournament.getCreatorUserId());

        boolean isReferee = StrUtil.isNotBlank(currentUserId)
                && tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournament.getId())
                        .eq("user_id", currentUserId)
        ) > 0;

        vo.setRefereeGranted(isReferee);
        vo.setCanOperateMatches(isCreator || isReferee);
        vo.setCanManageReferees(isCreator);
    }

    private List<TournamentRefereeVO> buildRefereeVOList(String tournamentId) {
        List<TournamentRefereeGrant> grants = tournamentRefereeGrantMapper.selectList(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("create_time")
        );

        if (CollUtil.isEmpty(grants)) {
            return List.of();
        }

        List<String> userIds = grants.stream()
                .map(TournamentRefereeGrant::getUserId)
                .collect(Collectors.toList());
        List<User> users = userMapper.selectList(
                new QueryWrapper<User>().in("id", userIds)
        );
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return grants.stream().map(grant -> {
            TournamentRefereeVO vo = new TournamentRefereeVO();
            vo.setUserId(grant.getUserId());
            User user = userMap.get(grant.getUserId());
            vo.setNickname(user == null ? "" : user.getNickname());
            vo.setAvatarUrl(user == null ? "" : user.getAvatarUrl());
            vo.setGrantedAt(grant.getCreateTime() == null ? "" : grant.getCreateTime().format(DATETIME_FORMATTER));
            return vo;
        }).collect(Collectors.toList());
    }

    private void requireCreatorOrReferee(String userId, String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return;
        }
        if (tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        ) > 0) {
            return;
        }
        throw new IllegalArgumentException("仅创建者或裁判可查看");
    }

    private static class Standing {
        private String playerId;
        private String playerName;
        private Integer seedRank;
        private int rank;
        private boolean qualified;
        private boolean tieUnresolved;
        private int matchWins;
        private int matchLosses;
        private int gameWins;
        private int gameLosses;
        private int pointsFor;
        private int pointsAgainst;

        private int netGames() {
            return gameWins - gameLosses;
        }

        private int netPoints() {
            return pointsFor - pointsAgainst;
        }
    }

    private record GroupRank(Integer groupNo, Integer rank, String playerId) {
    }
}
