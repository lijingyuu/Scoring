package com.scoring.backend.service.impl;

import cn.hutool.core.codec.Base64;
import cn.hutool.core.collection.CollUtil;
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
import com.scoring.backend.domain.dto.UpdateTournamentTeamReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TournamentQualificationOverride;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.DivisionDetailVO;
import com.scoring.backend.domain.vo.DivisionSummaryVO;
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
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentFavoriteMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.service.TournamentService;
import com.scoring.backend.service.tournament.TournamentAccessGuard;
import com.scoring.backend.service.tournament.TournamentCreationFactory;
import com.scoring.backend.service.tournament.TournamentRankingService;
import com.scoring.backend.service.tournament.TournamentRefereeService;
import com.scoring.backend.service.tournament.TournamentTeamEditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(TournamentServiceImpl.class);

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

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentFavoriteMapper tournamentFavoriteMapper;
    private final TournamentDivisionMapper tournamentDivisionMapper;
    private final TournamentRankingConfigMapper tournamentRankingConfigMapper;
    private final TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final BracketEngine bracketEngine;
    private final RoundRobinEngine roundRobinEngine;
    private final GroupStandingEngine groupStandingEngine;
    private final TournamentAccessGuard accessGuard;
    private final TournamentRefereeService refereeService;
    private final TournamentTeamEditService teamEditService;
    private final TournamentRankingService rankingService;
    private final TournamentCreationFactory creationFactory;

    public TournamentServiceImpl(TournamentMapper tournamentMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 TournamentFavoriteMapper tournamentFavoriteMapper,
                                 TournamentDivisionMapper tournamentDivisionMapper,
                                 TournamentRankingConfigMapper tournamentRankingConfigMapper,
                                 TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper,
                                 TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                 TournamentRoundRuleMapper tournamentRoundRuleMapper,
                                 TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                 TeamMatchItemMapper teamMatchItemMapper,
                                 BracketEngine bracketEngine,
                                 RoundRobinEngine roundRobinEngine,
                                 GroupStandingEngine groupStandingEngine,
                                  TournamentAccessGuard accessGuard,
                                  TournamentRefereeService refereeService,
                                  TournamentTeamEditService teamEditService,
                                  TournamentRankingService rankingService,
                                  TournamentCreationFactory creationFactory) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentFavoriteMapper = tournamentFavoriteMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
        this.tournamentRankingConfigMapper = tournamentRankingConfigMapper;
        this.tournamentQualificationOverrideMapper = tournamentQualificationOverrideMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
        this.groupStandingEngine = groupStandingEngine;
        this.accessGuard = accessGuard;
        this.refereeService = refereeService;
        this.teamEditService = teamEditService;
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

        TournamentDivision defaultDivision = resolveDefaultDivision(tournamentId);
        TournamentDetailVO vo = new TournamentDetailVO();
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setDivisionId(defaultDivision.getId());
        vo.setDivisionName(defaultDivision.getName());
        vo.setDivisions(buildDivisionSummaries(tournamentId));
        vo.setTournamentType(defaultDivision.getTournamentType());
        vo.setKnockoutSlots(defaultDivision.getKnockoutSlots());
        vo.setKnockoutRounds(defaultDivision.getKnockoutRounds());
        vo.setQualifiersPerGroup(defaultDivision.getQualifiersPerGroup());
        vo.setRoundRobinRounds(defaultDivision.getRoundRobinRounds());
        vo.setBestOf(defaultDivision.getBestOf());
        vo.setGamesToWin(defaultDivision.getGamesToWin());
        vo.setPointsToWin(defaultDivision.getPointsToWin());
        vo.setDecidingPointsToWin(defaultDivision.getDecidingPointsToWin());
        vo.setEnableDeuce(defaultDivision.getEnableDeuce());
        vo.setCapPoint(defaultDivision.getCapPoint());
        fillThirdPlaceRule(vo, defaultDivision);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(defaultDivision.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(defaultDivision.getId()));
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
        return buildBracket(tournament, resolveDefaultDivision(tournamentId), currentUserId);
    }

    @Override
    public TournamentBracketVO getDivisionBracket(String tournamentId, String divisionId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        return buildBracket(tournament, requireDivision(tournamentId, divisionId), currentUserId);
    }

    private TournamentBracketVO buildBracket(Tournament tournament, TournamentDivision division, String currentUserId) {
        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("division_id", division.getId())
                        .orderByAsc("create_time", "id")
        );
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("division_id", division.getId())
                        .eq("stage_type", STAGE_KNOCKOUT)
                        .orderByAsc("round_num", "match_index")
        );

        TournamentBracketVO vo = new TournamentBracketVO();
        fillBracketCommonFields(vo, tournament, division);
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
        return buildGroups(tournament, resolveDefaultDivision(tournamentId), currentUserId);
    }

    @Override
    public TournamentGroupsVO getDivisionGroups(String tournamentId, String divisionId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        return buildGroups(tournament, requireDivision(tournamentId, divisionId), currentUserId);
    }

    private TournamentGroupsVO buildGroups(Tournament tournament, TournamentDivision division, String currentUserId) {
        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("division_id", division.getId())
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
        List<MatchRecord> matches = TYPE_ROUND_ROBIN == division.getTournamentType()
                ? rankingService.loadAllTournamentMatches(division.getId())
                : rankingService.loadGroupMatches(division.getId());

        List<TournamentGroupsVO.GroupVO> groups;
        if (TYPE_ROUND_ROBIN == division.getTournamentType()) {
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
        fillGroupsCommonFields(vo, tournament, division);
        fillMatchAccess(vo, tournament, currentUserId);
        vo.setGroups(groups);
        return vo;
    }

    @Override
    public GroupStandingsVO getGroupStandings(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.getGroupStandings(tournament, resolveDefaultDivision(tournamentId), currentUserId);
    }

    @Override
    public GroupStandingsVO getDivisionGroupStandings(String tournamentId, String divisionId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.getGroupStandings(tournament, requireDivision(tournamentId, divisionId), currentUserId);
    }

    @Override
    public TournamentRankingConfigVO getRankingConfig(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.getRankingConfig(tournament, resolveDefaultDivision(tournamentId), currentUserId);
    }

    @Override
    public TournamentRankingConfigVO getDivisionRankingConfig(String tournamentId, String divisionId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.getRankingConfig(tournament, requireDivision(tournamentId, divisionId), currentUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentRankingConfigVO updateRankingConfig(String userId,
                                                         String tournamentId,
                                                         UpdateTournamentRankingConfigReq req) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.updateRankingConfig(userId, tournament, resolveDefaultDivision(tournamentId), req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TournamentRankingConfigVO updateDivisionRankingConfig(String userId,
                                                                  String tournamentId,
                                                                  String divisionId,
                                                                  UpdateTournamentRankingConfigReq req) {
        Tournament tournament = requireTournament(tournamentId);
        return rankingService.updateRankingConfig(userId, tournament, requireDivision(tournamentId, divisionId), req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateQualificationOverrides(String userId,
                                              String tournamentId,
                                              UpdateQualificationOverridesReq req) {
        Tournament tournament = requireTournament(tournamentId);
        rankingService.updateQualificationOverrides(userId, tournament, resolveDefaultDivision(tournamentId), req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateDivisionQualificationOverrides(String userId,
                                                       String tournamentId,
                                                       String divisionId,
                                                       UpdateQualificationOverridesReq req) {
        Tournament tournament = requireTournament(tournamentId);
        rankingService.updateQualificationOverrides(userId, tournament, requireDivision(tournamentId, divisionId), req);
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
        vo.setCreator(StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId()));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setTeams(participants.stream().map(this::toTeamVO).toList());
        return vo;
    }

    @Override
    public void updateTeam(String userId, String tournamentId, String participantId, UpdateTournamentTeamReq req) {
        teamEditService.updateTeam(userId, tournamentId, participantId, req);
    }

    @Override
    public KnockoutPreviewVO previewKnockout(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        return previewKnockoutForDivision(userId, tournament, resolveDefaultDivision(tournamentId));
    }

    @Override
    public KnockoutPreviewVO previewDivisionKnockout(String userId, String tournamentId, String divisionId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        return previewKnockoutForDivision(userId, tournament, requireDivision(tournamentId, divisionId));
    }

    private KnockoutPreviewVO previewKnockoutForDivision(String userId, Tournament tournament, TournamentDivision division) {
        if (!StrUtil.equals(userId, tournament.getCreatorUserId()) && !hasRefereeGrant(userId, tournament.getId())) {
            throw new IllegalArgumentException("只有创建者或已认证裁判可以预览淘汰赛");
        }
        requireNotArchived(tournament);
        if (TYPE_GROUP != division.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can preview knockout");
        }
        if (Boolean.TRUE.equals(division.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        GroupedKnockoutContext context = loadGroupedKnockoutContext(tournament, division);
        return buildKnockoutPreviewVO(tournament, division, context);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateKnockout(String userId, String tournamentId, GenerateKnockoutReq req) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        generateKnockoutForDivision(userId, tournament, resolveDefaultDivision(tournamentId), req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void generateDivisionKnockout(String userId, String tournamentId, String divisionId, GenerateKnockoutReq req) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        generateKnockoutForDivision(userId, tournament, requireDivision(tournamentId, divisionId), req);
    }

    private void generateKnockoutForDivision(String userId, Tournament tournament, TournamentDivision division, GenerateKnockoutReq req) {
        if (!StrUtil.equals(userId, tournament.getCreatorUserId()) && !hasRefereeGrant(userId, tournament.getId())) {
            throw new IllegalArgumentException("只有创建者或已认证裁判可以生成淘汰赛");
        }
        requireNotArchived(tournament);
        if (TYPE_GROUP != division.getTournamentType()) {
            throw new IllegalArgumentException("only group plus knockout tournaments can generate knockout");
        }
        TournamentDivision lockedDivision = tournamentDivisionMapper.selectByIdForUpdate(division.getId());
        if (lockedDivision == null) {
            throw new IllegalArgumentException("组别不存在: " + division.getId());
        }
        if (Boolean.TRUE.equals(lockedDivision.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        GroupedKnockoutContext context = loadGroupedKnockoutContext(tournament, lockedDivision);
        List<String> slots = resolveKnockoutSlots(context, req);
        List<MatchRecord> knockoutMatches = creationFactory.appendThirdPlaceMatch(lockedDivision,
                bracketEngine.generateKnockoutBracketBySlots(tournament.getId(), lockedDivision.getId(), slots));
        for (MatchRecord match : knockoutMatches) {
            matchRecordMapper.insert(match);
        }

        TournamentDivision divisionUpdate = new TournamentDivision();
        divisionUpdate.setId(lockedDivision.getId());
        divisionUpdate.setCurrentStage(STAGE_KNOCKOUT);
        divisionUpdate.setKnockoutGenerated(true);
        divisionUpdate.setStatus(1);
        tournamentDivisionMapper.updateById(divisionUpdate);

        Tournament update = new Tournament();
        update.setId(tournament.getId());
        update.setStatus(1);
        mirrorDivisionStateToTournament(tournament.getId(), divisionUpdate);
        tournamentMapper.updateById(update);
    }

    /** 单组别镜像写（回滚保险）：仅当赛事只有一个组别时，把组别进度状态同步到 tournament 行。 */
    private void mirrorDivisionStateToTournament(String tournamentId, TournamentDivision divisionUpdate) {
        Long divisionCount = tournamentDivisionMapper.selectCount(new QueryWrapper<TournamentDivision>()
                .eq("tournament_id", tournamentId));
        Tournament mirror = new Tournament();
        mirror.setId(tournamentId);
        mirror.setStatus(divisionUpdate.getStatus());
        if (divisionCount != null && divisionCount <= 1) {
            mirror.setCurrentStage(divisionUpdate.getCurrentStage());
            mirror.setKnockoutGenerated(divisionUpdate.getKnockoutGenerated());
        }
        tournamentMapper.updateById(mirror);
    }

    private GroupedKnockoutContext loadGroupedKnockoutContext(Tournament tournament, TournamentDivision division) {
        List<Player> players = rankingService.loadPlayers(division.getId());
        List<MatchRecord> groupMatches = rankingService.loadGroupMatches(division.getId());
        GroupStandingsVO standingsVO = rankingService.buildStandingsVO(tournament, division, players, groupMatches);
        if (!Boolean.TRUE.equals(standingsVO.getAllGroupMatchesFinished())) {
            throw new IllegalStateException("group matches are not finished");
        }
        if (Boolean.TRUE.equals(standingsVO.getHasUnresolvedTie())) {
            throw new IllegalArgumentException("group ranking has unresolved tie");
        }

        BracketEngine.KnockoutPlan plan = bracketEngine.buildGroupedKnockoutPlan(standingsVO);
        if (plan.slots().size() != safeInt(division.getKnockoutSlots())) {
            throw new IllegalStateException("qualifier count does not match knockout slots");
        }
        return new GroupedKnockoutContext(players, standingsVO, plan);
    }

    private KnockoutPreviewVO buildKnockoutPreviewVO(Tournament tournament, TournamentDivision division, GroupedKnockoutContext context) {
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
        vo.setKnockoutSlots(division.getKnockoutSlots());
        vo.setQualifiersPerGroup(division.getQualifiersPerGroup());
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

    private void fillBracketCommonFields(TournamentBracketVO vo, Tournament tournament, TournamentDivision division) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setDivisionId(division.getId());
        vo.setDivisionName(division.getName());
        vo.setTournamentType(division.getTournamentType());
        vo.setGroupSize(division.getGroupSize());
        vo.setKnockoutSlots(division.getKnockoutSlots());
        vo.setKnockoutRounds(division.getKnockoutRounds());
        vo.setQualifiersPerGroup(division.getQualifiersPerGroup());
        vo.setRoundRobinRounds(division.getRoundRobinRounds());
        vo.setCurrentStage(division.getCurrentStage());
        vo.setKnockoutGenerated(division.getKnockoutGenerated());
        vo.setArchived(Boolean.TRUE.equals(tournament.getArchived()));
        vo.setBestOf(division.getBestOf());
        vo.setGamesToWin(division.getGamesToWin());
        vo.setPointsToWin(division.getPointsToWin());
        vo.setDecidingPointsToWin(division.getDecidingPointsToWin());
        vo.setEnableDeuce(division.getEnableDeuce());
        vo.setCapPoint(division.getCapPoint());
        fillThirdPlaceRule(vo, division);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(division.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(division.getId()));
    }

    private void fillGroupsCommonFields(TournamentGroupsVO vo, Tournament tournament, TournamentDivision division) {
        vo.setId(tournament.getId());
        vo.setName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setStatus(tournament.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTeamMatchTemplate(safeTeamMatchTemplate(tournament));
        vo.setTeamMatchItems(resolveTeamMatchItems(tournament));
        vo.setDivisionId(division.getId());
        vo.setDivisionName(division.getName());
        vo.setTournamentType(division.getTournamentType());
        vo.setGroupSize(division.getGroupSize());
        vo.setKnockoutSlots(division.getKnockoutSlots());
        vo.setKnockoutRounds(division.getKnockoutRounds());
        vo.setQualifiersPerGroup(division.getQualifiersPerGroup());
        vo.setRoundRobinRounds(division.getRoundRobinRounds());
        vo.setCurrentStage(division.getCurrentStage());
        vo.setKnockoutGenerated(division.getKnockoutGenerated());
        vo.setArchived(Boolean.TRUE.equals(tournament.getArchived()));
        vo.setBestOf(division.getBestOf());
        vo.setGamesToWin(division.getGamesToWin());
        vo.setPointsToWin(division.getPointsToWin());
        vo.setDecidingPointsToWin(division.getDecidingPointsToWin());
        vo.setEnableDeuce(division.getEnableDeuce());
        vo.setCapPoint(division.getCapPoint());
        fillThirdPlaceRule(vo, division);
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(division.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(division.getId()));
    }

    private void fillThirdPlaceRule(TournamentDetailVO vo, TournamentDivision division) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(division.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(division.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(division.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(division.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(division.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(division.getThirdPlaceCapPoint());
    }

    private void fillThirdPlaceRule(TournamentBracketVO vo, TournamentDivision division) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(division.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(division.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(division.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(division.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(division.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(division.getThirdPlaceCapPoint());
    }

    private void fillThirdPlaceRule(TournamentGroupsVO vo, TournamentDivision division) {
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(division.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(division.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(division.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(division.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(division.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(division.getThirdPlaceCapPoint());
    }

    private List<TournamentRoundRule> loadRoundRules(String divisionId) {
        return tournamentRoundRuleMapper.selectList(new QueryWrapper<TournamentRoundRule>()
                .eq("division_id", divisionId)
                .orderByAsc("stage_type", "round_num"));
    }

    // ======================== 组别 ========================

    @Override
    public List<DivisionSummaryVO> listDivisions(String tournamentId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        return buildDivisionSummaries(tournamentId);
    }

    @Override
    public DivisionDetailVO getDivisionDetail(String tournamentId, String divisionId, String currentUserId) {
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        TournamentDivision division = requireDivision(tournamentId, divisionId);
        boolean isCreator = StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId());

        DivisionDetailVO vo = new DivisionDetailVO();
        vo.setTournamentId(tournament.getId());
        vo.setTournamentName(tournament.getName());
        vo.setDivisionId(division.getId());
        vo.setDivisionName(division.getName());
        vo.setSortOrder(division.getSortOrder());
        vo.setStatus(division.getStatus());
        vo.setSportType(safeSportType(tournament));
        vo.setParticipantType(safeParticipantType(tournament));
        vo.setTournamentType(division.getTournamentType());
        vo.setKnockoutSlots(division.getKnockoutSlots());
        vo.setKnockoutRounds(division.getKnockoutRounds());
        vo.setQualifiersPerGroup(division.getQualifiersPerGroup());
        vo.setRoundRobinRounds(division.getRoundRobinRounds());
        vo.setCurrentStage(division.getCurrentStage());
        vo.setKnockoutGenerated(division.getKnockoutGenerated());
        vo.setBestOf(division.getBestOf());
        vo.setGamesToWin(division.getGamesToWin());
        vo.setPointsToWin(division.getPointsToWin());
        vo.setDecidingPointsToWin(division.getDecidingPointsToWin());
        vo.setEnableDeuce(division.getEnableDeuce());
        vo.setCapPoint(division.getCapPoint());
        vo.setThirdPlaceEnabled(Boolean.TRUE.equals(division.getThirdPlaceEnabled()));
        vo.setThirdPlaceBestOf(division.getThirdPlaceBestOf());
        vo.setThirdPlaceGamesToWin(division.getThirdPlaceGamesToWin());
        vo.setThirdPlacePointsToWin(division.getThirdPlacePointsToWin());
        vo.setThirdPlaceDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin());
        vo.setThirdPlaceEnableDeuce(division.getThirdPlaceEnableDeuce());
        vo.setThirdPlaceCapPoint(division.getThirdPlaceCapPoint());
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(division.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(division.getId()));
        vo.setPlayers(rankingService.loadPlayers(division.getId()));
        vo.setCreator(isCreator);
        return vo;
    }

    private List<DivisionSummaryVO> buildDivisionSummaries(String tournamentId) {
        List<TournamentDivision> divisions = listDivisionEntities(tournamentId);
        List<DivisionSummaryVO> summaries = new ArrayList<>();
        for (TournamentDivision division : divisions) {
            DivisionSummaryVO summary = new DivisionSummaryVO();
            summary.setDivisionId(division.getId());
            summary.setName(division.getName());
            summary.setSortOrder(division.getSortOrder());
            summary.setStatus(division.getStatus());
            summary.setTournamentType(division.getTournamentType());
            summary.setKnockoutGenerated(division.getKnockoutGenerated());
            summary.setCurrentStage(division.getCurrentStage());
            summary.setPlayerCount(playerMapper.selectCount(new QueryWrapper<Player>()
                    .eq("division_id", division.getId())).intValue());
            summaries.add(summary);
        }
        return summaries;
    }

    private List<TournamentDivision> listDivisionEntities(String tournamentId) {
        return tournamentDivisionMapper.selectList(new QueryWrapper<TournamentDivision>()
                .eq("tournament_id", tournamentId)
                .orderByAsc("sort_order", "id"));
    }

    /**
     * 兼容层：旧赛事级接口按 sort_order 取第一个组别。
     * 多组别赛事命中旧接口时只作用于第 1 个组别（过渡行为，新前端上线一个版本后下线），
     * 因此这里打一条 debug 日志便于排查"为什么操作落到别的组别"。
     */
    private TournamentDivision resolveDefaultDivision(String tournamentId) {
        List<TournamentDivision> divisions = listDivisionEntities(tournamentId);
        if (CollUtil.isEmpty(divisions)) {
            throw new IllegalStateException("tournament has no division: " + tournamentId);
        }
        if (divisions.size() > 1) {
            log.debug("legacy tournament-scoped API resolved to first division: tournamentId={}, divisionId={}, divisionCount={}",
                    tournamentId, divisions.get(0).getId(), divisions.size());
        }
        return divisions.get(0);
    }

    private TournamentDivision requireDivision(String tournamentId, String divisionId) {
        if (StrUtil.isBlank(divisionId)) {
            throw new IllegalArgumentException("division id is required");
        }
        TournamentDivision division = tournamentDivisionMapper.selectById(divisionId);
        if (division == null || !StrUtil.equals(division.getTournamentId(), tournamentId)) {
            throw new IllegalArgumentException("division not found: " + divisionId);
        }
        return division;
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
        team.setSeedRank(participant == null ? null : participant.getSeedRank());
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
        return refereeService.authenticateReferee(userId, tournamentId, req);
    }

    @Override
    public List<TournamentRefereeVO> listReferees(String userId, String tournamentId) {
        return refereeService.listReferees(userId, tournamentId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeReferee(String userId, String tournamentId, String refereeUserId) {
        refereeService.removeReferee(userId, tournamentId, refereeUserId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateRefereePassword(String userId, String tournamentId, UpdateTournamentRefereePasswordReq req) {
        refereeService.updateRefereePassword(userId, tournamentId, req);
    }

    @Override
    public boolean canOperateVolleyballMatch(String userId, String tournamentId) {
        return refereeService.canOperateVolleyballMatch(userId, tournamentId);
    }

    private void fillMatchAccess(TournamentMatchAccessVO vo, Tournament tournament, String currentUserId) {
        refereeService.fillMatchAccess(vo, tournament, currentUserId);
    }

    private void requireCreatorOrReferee(String userId, String tournamentId) {
        accessGuard.requireCreatorOrReferee(userId, tournamentId);
    }

    private boolean hasRefereeGrant(String userId, String tournamentId) {
        return accessGuard.hasRefereeGrant(userId, tournamentId);
    }

}
