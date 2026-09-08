package com.scoring.backend.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.dto.GenerateKnockoutReq;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRankingConfigReq;
import com.scoring.backend.domain.dto.UpdateQualificationOverridesReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TournamentQualificationOverride;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.KnockoutPreviewVO;
import com.scoring.backend.domain.vo.TournamentMatchAccessVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TeamMatchItemVO;
import com.scoring.backend.domain.vo.TournamentRankingConfigVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.domain.vo.TournamentTeamsVO;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.engine.ranking.GroupStandingEngine;
import com.scoring.backend.engine.ranking.RankingConfig;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentFavoriteMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.TournamentService;
import com.scoring.backend.service.tournament.TournamentAccessGuard;
import com.scoring.backend.service.tournament.TournamentCreationFactory;
import com.scoring.backend.service.tournament.TournamentRankingService;
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
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class TournamentServiceImpl implements TournamentService {

    private static final int SPORT_BADMINTON = 0;
    private static final int SPORT_VOLLEYBALL = 1;
    private static final int PARTICIPANT_INDIVIDUAL = 0;
    private static final int PARTICIPANT_TEAM = 1;
    private static final int TEAM_MATCH_TEMPLATE_NONE = 0;
    private static final int TEAM_MATCH_TEMPLATE_SUDIRMAN_5 = 1;
    private static final int TEAM_MATCH_TEMPLATE_RELAY = 2;
    private static final int TYPE_KNOCKOUT = 0;
    private static final int TYPE_GROUP = 1;
    private static final int TYPE_ROUND_ROBIN = 2;
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;
    private static final int MATCH_ROLE_NORMAL = 0;
    private static final int MATCH_ROLE_THIRD_PLACE = 1;

    private static final int DEFAULT_BEST_OF = 3;
    private static final int DEFAULT_GAMES_TO_WIN = 2;
    private static final int DEFAULT_POINTS_TO_WIN = 21;
    private static final boolean DEFAULT_ENABLE_DEUCE = true;
    private static final int DEFAULT_CAP_POINT = 30;
    private static final int DEFAULT_VOLLEYBALL_POINTS_TO_WIN = 25;
    private static final int DEFAULT_VOLLEYBALL_DECIDING_POINTS_TO_WIN = 15;
    private static final int DEFAULT_VOLLEYBALL_CAP_POINT = 99;
    private static final int DEFAULT_RELAY_MEMBER_COUNT = 6;
    private static final int MAX_KNOCKOUT_ROUNDS = 10;
    private static final String REFEREE_PASSWORD_PATTERN = "^\\d{8}$";
    private static final String REFEREE_HASH_SALT = "tournament_referee_password";

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentFavoriteMapper tournamentFavoriteMapper;
    private final TournamentRankingConfigMapper tournamentRankingConfigMapper;
    private final TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final UserMapper userMapper;
    private final BracketEngine bracketEngine;
    private final RoundRobinEngine roundRobinEngine;
    private final GroupStandingEngine groupStandingEngine;
    private final TournamentAccessGuard accessGuard;
    private final TournamentRankingService rankingService;
    private final TournamentCreationFactory creationFactory;

    public TournamentServiceImpl(TournamentMapper tournamentMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 TournamentFavoriteMapper tournamentFavoriteMapper,
                                 TournamentRankingConfigMapper tournamentRankingConfigMapper,
                                 TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper,
                                 TournamentRefereeConfigMapper tournamentRefereeConfigMapper,
                                 TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                 TournamentRoundRuleMapper tournamentRoundRuleMapper,
                                 TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                 TeamMatchItemMapper teamMatchItemMapper,
                                 UserMapper userMapper,
                                 BracketEngine bracketEngine,
                                 RoundRobinEngine roundRobinEngine,
                                 GroupStandingEngine groupStandingEngine,
                                  TournamentAccessGuard accessGuard,
                                  TournamentRankingService rankingService,
                                  TournamentCreationFactory creationFactory) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentFavoriteMapper = tournamentFavoriteMapper;
        this.tournamentRankingConfigMapper = tournamentRankingConfigMapper;
        this.tournamentQualificationOverrideMapper = tournamentQualificationOverrideMapper;
        this.tournamentRefereeConfigMapper = tournamentRefereeConfigMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.userMapper = userMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
        this.groupStandingEngine = groupStandingEngine;
        this.accessGuard = accessGuard;
        this.rankingService = rankingService;
        this.creationFactory = creationFactory;
    }

    @Override
    public String createTournament(String creatorUserId, CreateTournamentReq req) {
        return creationFactory.createTournament(creatorUserId, req);
    }

    @Override
    public List<Tournament> listTournaments(String currentUserId, String keyword) {
        if (StrUtil.isBlank(keyword)) {
            return List.of();
        }
        String cleanKeyword = keyword.trim();
        LambdaQueryWrapper<Tournament> wrapper = new LambdaQueryWrapper<Tournament>()
                .eq(Tournament::getArchived, false)
                .and(w -> w.like(Tournament::getName, cleanKeyword).or().like(Tournament::getLocation, cleanKeyword))
                .orderByDesc(Tournament::getCreateTime);
        List<Tournament> tournaments = tournamentMapper.selectList(wrapper);
        decorateTournamentFlags(tournaments, currentUserId);
        return tournaments;
    }
    @Override
    public TournamentDetailVO getTournamentDetail(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        boolean isCreator = StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId());

        TournamentDetailVO vo = new TournamentDetailVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setKnockoutRounds(tournament.getKnockoutRounds());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setRoundRobinRounds(tournament.getRoundRobinRounds());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setDecidingPointsToWin(tournament.getDecidingPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        fillThirdPlaceRule(vo, tournament);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(tournament.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(tournament.getId()));
        vo.setFavoriteCount(tournament.getFavoriteCount());
        vo.setCreatorUserId(tournament.getCreatorUserId());
        vo.setCreateTime(tournament.getCreateTime() == null ? null : tournament.getCreateTime().format(DATETIME_FORMATTER));
        vo.setArchived(Boolean.TRUE.equals(tournament.getArchived()));
        vo.setCreator(isCreator);
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
        List<Tournament> tournaments = tournamentMapper.selectList(
                new LambdaQueryWrapper<Tournament>()
                        .in(Tournament::getId, tournamentIds)
                        .eq(Tournament::getArchived, false)
        );
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
                        .eq(Tournament::getArchived, false)
                        .orderByDesc(Tournament::getCreateTime)
        );
        decorateTournamentFlags(tournaments, userId);
        return tournaments;
    }

    @Override
    public List<Tournament> listArchivedTournaments(String userId) {
        List<Tournament> tournaments = tournamentMapper.selectList(
                new LambdaQueryWrapper<Tournament>()
                        .eq(Tournament::getCreatorUserId, userId)
                        .eq(Tournament::getArchived, true)
                        .orderByDesc(Tournament::getUpdateTime)
        );
        decorateTournamentFlags(tournaments, userId);
        return tournaments;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveTournament(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        requireCreator(userId, tournament);
        if (!Integer.valueOf(2).equals(tournament.getStatus())) {
            throw new IllegalStateException("only finished tournaments can be archived");
        }
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            return;
        }
        Tournament update = new Tournament();
        update.setId(tournamentId);
        update.setArchived(true);
        tournamentMapper.updateById(update);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unarchiveTournament(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        requireCreator(userId, tournament);
        if (!Boolean.TRUE.equals(tournament.getArchived())) {
            return;
        }
        Tournament update = new Tournament();
        update.setId(tournamentId);
        update.setArchived(false);
        tournamentMapper.updateById(update);
    }
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void favoriteTournament(String userId, String tournamentId) {
        requireCompletedProfile(userId);
        Tournament tournament = requireTournament(tournamentId);
        requireNotArchived(tournament);
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
        requireArchivedReadable(tournament, currentUserId);
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
        requireArchivedReadable(tournament, currentUserId);

        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
        List<MatchRecord> matches = TYPE_ROUND_ROBIN == tournament.getTournamentType()
                ? rankingService.loadAllTournamentMatches(tournamentId)
                : rankingService.loadGroupMatches(tournamentId);

        List<TournamentGroupsVO.GroupVO> groups;
        if (TYPE_ROUND_ROBIN == tournament.getTournamentType()) {
            attachTeamMembersIfNeeded(tournament, players);
            TournamentGroupsVO.GroupVO group = new TournamentGroupsVO.GroupVO();
            group.setGroupNo(1);
            group.setPlayers(players);
            group.setMatches(matches);
            groups = List.of(group);
        } else {
            Map<Integer, List<Player>> playersByGroup = players.stream()
                    .filter(player -> player.getGroupNo() != null)
                    .collect(Collectors.groupingBy(Player::getGroupNo));
            Map<Integer, List<MatchRecord>> matchesByGroup = matches.stream()
                    .filter(match -> match.getGroupNo() != null)
                    .collect(Collectors.groupingBy(MatchRecord::getGroupNo));

            groups = playersByGroup.keySet().stream()
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
        }

        TournamentGroupsVO vo = new TournamentGroupsVO();
        fillGroupsCommonFields(vo, tournament);
        fillMatchAccess(vo, tournament, currentUserId);
        vo.setGroups(groups);
        return vo;
    }

    @Override
    public GroupStandingsVO getGroupStandings(String tournamentId, String currentUserId) {
        return rankingService.getGroupStandings(tournamentId, currentUserId);
    }

    @Override
    public TournamentRankingConfigVO getRankingConfig(String tournamentId, String currentUserId) {
        return rankingService.getRankingConfig(tournamentId, currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentRankingConfigVO updateRankingConfig(String userId,
                                                         String tournamentId,
                                                         UpdateTournamentRankingConfigReq req) {
        return rankingService.updateRankingConfig(userId, tournamentId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQualificationOverrides(String userId,
                                              String tournamentId,
                                              UpdateQualificationOverridesReq req) {
        rankingService.updateQualificationOverrides(userId, tournamentId, req);
    }

    @Override
    public TournamentTeamsVO getTeams(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        if (!Integer.valueOf(PARTICIPANT_TEAM).equals(safeParticipantType(tournament))) {
            throw new IllegalArgumentException("\u4ec5\u56e2\u4f53\u8d5b\u652f\u6301\u67e5\u770b\u961f\u4f0d");
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
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setTeams(participants.stream().map(this::toTeamVO).toList());
        return vo;
    }

    @Override
    public KnockoutPreviewVO previewKnockout(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId()) && !hasRefereeGrant(userId, tournamentId)) {
            throw new IllegalArgumentException("只有创建者或已认证裁判可以预览淘汰赛");
        }
        requireNotArchived(tournament);
        if (TYPE_GROUP != tournament.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can preview knockout");
        }
        if (Boolean.TRUE.equals(tournament.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        GroupedKnockoutContext context = loadGroupedKnockoutContext(tournament);
        return buildKnockoutPreviewVO(tournament, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateKnockout(String userId, String tournamentId, GenerateKnockoutReq req) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId()) && !hasRefereeGrant(userId, tournamentId)) {
            throw new IllegalArgumentException("只有创建者或已认证裁判可以生成淘汰赛");
        }
        requireNotArchived(tournament);
        if (TYPE_GROUP != tournament.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can generate knockout");
        }
        if (Boolean.TRUE.equals(tournament.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        GroupedKnockoutContext context = loadGroupedKnockoutContext(tournament);
        List<String> slots = resolveKnockoutSlots(context, req);
        List<MatchRecord> knockoutMatches = creationFactory.appendThirdPlaceMatch(tournament,
                bracketEngine.generateKnockoutBracketBySlots(tournamentId, slots));
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

    private GroupedKnockoutContext loadGroupedKnockoutContext(Tournament tournament) {
        List<Player> players = rankingService.loadPlayers(tournament.getId());
        List<MatchRecord> groupMatches = rankingService.loadGroupMatches(tournament.getId());
        GroupStandingsVO standingsVO = rankingService.buildStandingsVO(tournament, players, groupMatches);
        if (!Boolean.TRUE.equals(standingsVO.getAllGroupMatchesFinished())) {
            throw new IllegalStateException("group matches are not finished");
        }
        if (Boolean.TRUE.equals(standingsVO.getHasUnresolvedTie())) {
            throw new IllegalArgumentException("group ranking has unresolved tie");
        }

        BracketEngine.KnockoutPlan plan = bracketEngine.buildGroupedKnockoutPlan(standingsVO);
        if (plan.slots().size() != safeInt(tournament.getKnockoutSlots())) {
            throw new IllegalStateException("qualifier count does not match knockout slots");
        }
        return new GroupedKnockoutContext(players, standingsVO, plan);
    }

    private KnockoutPreviewVO buildKnockoutPreviewVO(Tournament tournament, GroupedKnockoutContext context) {
        Map<String, Player> playerMap = context.players().stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, player -> player));
        List<GroupStandingsVO.GroupVO> standingGroups = context.standingsVO().getGroups() == null ? List.of() : context.standingsVO().getGroups();
        Map<String, GroupStandingsVO.StandingVO> standingMap = standingGroups.stream()
                .filter(group -> group != null && group.getStandings() != null)
                .flatMap(group -> group.getStandings().stream())
                .filter(standing -> standing != null && StrUtil.isNotBlank(standing.getPlayerId()))
                .collect(Collectors.toMap(GroupStandingsVO.StandingVO::getPlayerId, standing -> standing, (left, right) -> left));

        List<KnockoutPreviewVO.MatchVO> matches = new ArrayList<>();
        List<String> slots = context.plan().slots();
        for (int i = 0; i + 1 < slots.size(); i += 2) {
            KnockoutPreviewVO.MatchVO match = new KnockoutPreviewVO.MatchVO();
            match.setSlotIndex(i / 2);
            match.setLeftPlayer(buildPreviewParticipant(slots.get(i), playerMap, standingMap));
            match.setRightPlayer(buildPreviewParticipant(slots.get(i + 1), playerMap, standingMap));
            matches.add(match);
        }

        KnockoutPreviewVO vo = new KnockoutPreviewVO();
        vo.setId(tournament.getId());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setAllGroupMatchesFinished(context.standingsVO().getAllGroupMatchesFinished());
        vo.setHasUnresolvedTie(context.standingsVO().getHasUnresolvedTie());
        vo.setMatches(matches);
        return vo;
    }

    private List<String> resolveKnockoutSlots(GroupedKnockoutContext context, GenerateKnockoutReq req) {
        List<String> defaultSlots = context.plan().slots();
        if (req == null || CollUtil.isEmpty(req.getSlots())) {
            return defaultSlots;
        }

        List<String> slots = req.getSlots().stream()
                .filter(StrUtil::isNotBlank)
                .map(StrUtil::trim)
                .collect(Collectors.toList());
        if (slots.size() != defaultSlots.size()) {
            throw new IllegalArgumentException("slots size does not match knockout slots");
        }
        if (new HashSet<>(slots).size() != slots.size()) {
            throw new IllegalArgumentException("slots contain duplicate player");
        }
        Set<String> expected = new HashSet<>(defaultSlots);
        Set<String> actual = new HashSet<>(slots);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("slots do not match qualifiers");
        }
        return slots;
    }

    private KnockoutPreviewVO.ParticipantVO buildPreviewParticipant(String playerId,
                                                                    Map<String, Player> playerMap,
                                                                    Map<String, GroupStandingsVO.StandingVO> standingMap) {
        if (StrUtil.isBlank(playerId)) {
            return null;
        }
        Player player = playerMap.get(playerId);
        GroupStandingsVO.StandingVO standing = standingMap.get(playerId);
        KnockoutPreviewVO.ParticipantVO vo = new KnockoutPreviewVO.ParticipantVO();
        vo.setPlayerId(playerId);
        vo.setPlayerName(player == null ? null : player.getName());
        vo.setGroupNo(player == null ? null : player.getGroupNo());
        vo.setSeedRank(player == null ? null : player.getSeedRank());
        vo.setGroupRank(standing == null ? null : standing.getRank());
        return vo;
    }

    private record GroupedKnockoutContext(List<Player> players,
                                          GroupStandingsVO standingsVO,
                                          BracketEngine.KnockoutPlan plan) {
    }

    private void fillBracketCommonFields(TournamentBracketVO vo, Tournament tournament) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setKnockoutRounds(tournament.getKnockoutRounds());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setRoundRobinRounds(tournament.getRoundRobinRounds());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setArchived(Boolean.TRUE.equals(tournament.getArchived()));
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setDecidingPointsToWin(tournament.getDecidingPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        fillThirdPlaceRule(vo, tournament);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(tournament.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(tournament.getId()));
    }

    private void fillGroupsCommonFields(TournamentGroupsVO vo, Tournament tournament) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setTournamentType(tournament.getTournamentType());
        vo.setGroupSize(tournament.getGroupSize());
        vo.setKnockoutSlots(tournament.getKnockoutSlots());
        vo.setKnockoutRounds(tournament.getKnockoutRounds());
        vo.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        vo.setRoundRobinRounds(tournament.getRoundRobinRounds());
        vo.setCurrentStage(tournament.getCurrentStage());
        vo.setKnockoutGenerated(tournament.getKnockoutGenerated());
        vo.setArchived(Boolean.TRUE.equals(tournament.getArchived()));
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setDecidingPointsToWin(tournament.getDecidingPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        fillThirdPlaceRule(vo, tournament);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(tournament.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(tournament.getId()));
    }

    private void fillThirdPlaceRule(TournamentDetailVO vo, Tournament tournament) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(tournament.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(tournament.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(tournament.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(tournament.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(tournament.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(tournament.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(tournament.getThirdPlaceCapPoint());
    }

    private void fillThirdPlaceRule(TournamentBracketVO vo, Tournament tournament) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(tournament.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(tournament.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(tournament.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(tournament.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(tournament.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(tournament.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(tournament.getThirdPlaceCapPoint());
    }

    private void fillThirdPlaceRule(TournamentGroupsVO vo, Tournament tournament) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(tournament.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(tournament.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(tournament.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(tournament.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(tournament.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(tournament.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(tournament.getThirdPlaceCapPoint());
    }

    private List<TournamentRoundRule> loadRoundRules(String tournamentId) {
        return tournamentRoundRuleMapper.selectList(new QueryWrapper<TournamentRoundRule>()
                .eq("tournament_id", tournamentId)
                .orderByAsc("stage_type", "round_num"));
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
        return accessGuard.requireTournament(tournamentId);
    }

    private boolean isArchived(Tournament tournament) {
        return accessGuard.isArchived(tournament);
    }

    private void requireArchivedReadable(Tournament tournament, String currentUserId) {
        accessGuard.requireArchivedReadable(tournament, currentUserId);
    }

    private void requireNotArchived(Tournament tournament) {
        accessGuard.requireNotArchived(tournament);
    }

    private void requireCreator(String userId, Tournament tournament) {
        accessGuard.requireCreator(userId, tournament);
    }

    private void requireCompletedProfile(String userId) {
        accessGuard.requireCompletedProfile(userId);
    }


    private void attachTeamMembersIfNeeded(Tournament tournament, List<Player> players) {
        if (!Integer.valueOf(PARTICIPANT_TEAM).equals(safeParticipantType(tournament)) || CollUtil.isEmpty(players)) {
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
        // Badminton team members have no jersey number, so name and display order keep their list stable.
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

    private Integer safeParticipantType(Tournament tournament) {
        if (Integer.valueOf(SPORT_VOLLEYBALL).equals(safeSportType(tournament))) {
            return PARTICIPANT_TEAM;
        }
        if (hasTeamMembers(tournament) || hasTeamMatchItems(tournament)) {
            return PARTICIPANT_TEAM;
        }
        return tournament.getParticipantType() == null ? PARTICIPANT_INDIVIDUAL : tournament.getParticipantType();
    }

    private Integer safeTeamMatchTemplate(Tournament tournament) {
        Integer stored = tournament.getTeamMatchTemplate();
        if (stored != null && stored != TEAM_MATCH_TEMPLATE_NONE) {
            return stored;
        }
        Integer inferred = inferTeamMatchTemplate(tournament);
        if (inferred != null) {
            return inferred;
        }
        return tournament.getTeamMatchTemplate() == null ? TEAM_MATCH_TEMPLATE_NONE : tournament.getTeamMatchTemplate();
    }

    private boolean hasTeamMatchItems(Tournament tournament) {
        if (tournament == null || StrUtil.isBlank(tournament.getId())
                || !Integer.valueOf(SPORT_BADMINTON).equals(safeSportType(tournament))) {
            return false;
        }
        return teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>()
                .eq("tournament_id", tournament.getId())) > 0;
    }

    private boolean hasTeamMembers(Tournament tournament) {
        if (tournament == null || StrUtil.isBlank(tournament.getId())
                || !Integer.valueOf(SPORT_BADMINTON).equals(safeSportType(tournament))) {
            return false;
        }
        return tournamentTeamMemberMapper.selectCount(new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournament.getId())) > 0;
    }

    private Integer inferTeamMatchTemplate(Tournament tournament) {
        if (tournament == null || StrUtil.isBlank(tournament.getId())
                || !Integer.valueOf(SPORT_BADMINTON).equals(safeSportType(tournament))) {
            return null;
        }
        List<TeamMatchItem> items = teamMatchItemMapper.selectList(
                new QueryWrapper<TeamMatchItem>()
                        .eq("tournament_id", tournament.getId())
                        .last("LIMIT 6")
        );
        if (CollUtil.isEmpty(items)) {
            return null;
        }
        boolean relay = items.stream().anyMatch(item -> StrUtil.startWithIgnoreCase(item.getItemCode(), "R"));
        return relay ? TEAM_MATCH_TEMPLATE_RELAY : TEAM_MATCH_TEMPLATE_SUDIRMAN_5;
    }

    private List<TeamMatchItemVO> resolveTeamMatchItems(Tournament tournament) {
        if (!Integer.valueOf(SPORT_BADMINTON).equals(safeSportType(tournament))
                || !Integer.valueOf(PARTICIPANT_TEAM).equals(safeParticipantType(tournament))
                || (safeTeamMatchTemplate(tournament) != TEAM_MATCH_TEMPLATE_SUDIRMAN_5
                    && safeTeamMatchTemplate(tournament) != TEAM_MATCH_TEMPLATE_RELAY)) {
            return List.of();
        }
        if (safeTeamMatchTemplate(tournament) == TEAM_MATCH_TEMPLATE_RELAY) {
            return List.of();
        }
        return List.of(
                new TeamMatchItemVO(1, "MS", "\u7537\u5355", 1),
                new TeamMatchItemVO(2, "WS", "\u5973\u5355", 1),
                new TeamMatchItemVO(3, "MD", "\u7537\u53cc", 2),
                new TeamMatchItemVO(4, "WD", "\u5973\u53cc", 2),
                new TeamMatchItemVO(5, "XD", "\u6df7\u53cc", 2)
        );
    }





    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }





    // ======================== 裁判管理 ========================

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentRefereeAccessVO authenticateReferee(String userId, String tournamentId, TournamentRefereeAuthReq req) {
        requireCompletedProfile(userId);
        Tournament tournament = requireTournament(tournamentId);
        requireNotArchived(tournament);

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
        requireNotArchived(tournament);

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
        requireNotArchived(tournament);

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
        // This method is used as generic tournament match-operation access; the legacy name is kept for compatibility.
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null || isArchived(tournament)) {
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


    private void validateRefereePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("裁判密码不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("裁判密码必须为8位数字");
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
        if (isArchived(tournament)) {
            vo.setRefereeGranted(false);
            vo.setCanOperateMatches(false);
            vo.setCanManageReferees(false);
            return;
        }

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
        accessGuard.requireCreatorOrReferee(userId, tournamentId);
    }

    private boolean hasRefereeGrant(String userId, String tournamentId) {
        return accessGuard.hasRefereeGrant(userId, tournamentId);
    }

}
