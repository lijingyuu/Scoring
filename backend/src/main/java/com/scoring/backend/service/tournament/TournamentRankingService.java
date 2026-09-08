package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.dto.UpdateTournamentRankingConfigReq;
import com.scoring.backend.domain.dto.UpdateQualificationOverridesReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TournamentQualificationOverride;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.engine.ranking.RankingConfig;
import com.scoring.backend.domain.vo.TournamentRankingConfigVO;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.engine.ranking.GroupStandingEngine;
import com.scoring.backend.service.tournament.TournamentAccessGuard;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛事排名服务：排名配置（含模板解析/默认值）、小组积分榜计算、晋级资格覆盖。
 * 读模型计算为主；写操作仅限排名配置与资格覆盖两张表。
 */
@Service
public class TournamentRankingService {

    private static final int TYPE_GROUP = 1;
    private static final int TYPE_ROUND_ROBIN = 2;
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_TEAM_CHILD = 2;
    private static final int SPORT_BADMINTON = 0;
    private static final int SPORT_VOLLEYBALL = 1;
    private static final int PARTICIPANT_TEAM = 1;
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentRankingConfigMapper tournamentRankingConfigMapper;
    private final TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    private final GroupStandingEngine groupStandingEngine;
    private final TournamentAccessGuard accessGuard;

    public TournamentRankingService(            PlayerMapper playerMapper,
            MatchRecordMapper matchRecordMapper,
            TeamMatchItemMapper teamMatchItemMapper,
            TournamentRankingConfigMapper tournamentRankingConfigMapper,
            TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper,
            GroupStandingEngine groupStandingEngine,
            TournamentAccessGuard accessGuard) {
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentRankingConfigMapper = tournamentRankingConfigMapper;
        this.tournamentQualificationOverrideMapper = tournamentQualificationOverrideMapper;
        this.groupStandingEngine = groupStandingEngine;
        this.accessGuard = accessGuard;
    }

    public GroupStandingsVO getGroupStandings(String tournamentId, String currentUserId) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireArchivedReadable(tournament, currentUserId);
        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> matches = TYPE_ROUND_ROBIN == tournament.getTournamentType()
                ? loadAllTournamentMatches(tournamentId)
                : loadGroupMatches(tournamentId);
        return buildStandingsVO(tournament, players, matches);
    }

    public TournamentRankingConfigVO getRankingConfig(String tournamentId, String currentUserId) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireArchivedReadable(tournament, currentUserId);
        return toRankingConfigVO(tournament, loadRankingConfigEntity(tournamentId),
                hasFinishedRankingMatch(tournament), currentUserId);
    }

        @Transactional(rollbackFor = Exception.class)
    public TournamentRankingConfigVO updateRankingConfig(String userId,
                                                         String tournamentId,
                                                         UpdateTournamentRankingConfigReq req) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireNotArchived(tournament);
        accessGuard.requireCreator(userId, tournament);
        if (tournament.getTournamentType() == null
                || (tournament.getTournamentType() != TYPE_GROUP
                && tournament.getTournamentType() != TYPE_ROUND_ROBIN)) {
            throw new IllegalArgumentException("only group stage tournaments support ranking config");
        }
        RankingConfig rankingConfig = parseRankingConfig(req);
        TournamentRankingConfig entity = loadRankingConfigEntity(tournamentId);
        if (entity == null) {
            entity = new TournamentRankingConfig();
            entity.setTournamentId(tournamentId);
            entity.setConfigVersion(1);
        }
        entity.setConfigJson(rankingConfig.toJson());
        if (entity.getConfigVersion() == null) {
            entity.setConfigVersion(1);
        }
        if (entity.getId() == null) {
            tournamentRankingConfigMapper.insert(entity);
        } else {
            tournamentRankingConfigMapper.updateById(entity);
        }
        clearQualificationOverrides(tournamentId);
        return toRankingConfigVO(tournament, entity, hasFinishedRankingMatch(tournament), userId);
    }

        @Transactional(rollbackFor = Exception.class)
    public void updateQualificationOverrides(String userId,
                                              String tournamentId,
                                              UpdateQualificationOverridesReq req) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireCreatorOrReferee(userId, tournamentId);
        if (tournament.getTournamentType() != TYPE_GROUP) {
            throw new IllegalArgumentException("only group plus knockout tournaments support manual qualification");
        }
        if (Boolean.TRUE.equals(tournament.getKnockoutGenerated())) {
            throw new IllegalStateException("knockout bracket already generated");
        }

        List<UpdateQualificationOverridesReq.Item> requested =
                req == null || req.getOverrides() == null ? List.of() : req.getOverrides();
        if (requested.isEmpty()) {
            clearQualificationOverrides(tournamentId);
            return;
        }

        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> matches = loadGroupMatches(tournamentId);
        RankingConfig rankingConfig = loadRankingConfig(tournamentId);
        List<MatchRecord> rankingMatches = enrichTeamRankingMatchesIfNeeded(tournament, matches, rankingConfig);
        if (!allRankingMatchesFinished(rankingMatches, rankingConfig)) {
            throw new IllegalStateException("group matches are not finished");
        }

        Map<Integer, List<Player>> playersByGroup = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));
        Set<String> usedSlots = new HashSet<>();
        Set<String> usedPlayers = new HashSet<>();
        Map<Integer, List<GroupStandingEngine.Standing>> standingsByGroup = new HashMap<>();
        for (Integer groupNo : playersByGroup.keySet()) {
            List<GroupStandingEngine.Standing> standings = buildGroupStandingsWithEngine(
                    playersByGroup.getOrDefault(groupNo, List.of()),
                    rankingMatches.stream()
                            .filter(match -> java.util.Objects.equals(match.getGroupNo(), groupNo))
                            .collect(Collectors.toList()),
                    tournament.getQualifiersPerGroup(),
                    rankingConfig
            );
            standingsByGroup.put(groupNo, standings);
        }

        Map<Integer, Set<Integer>> requiredSlotsByGroup = new HashMap<>();
        Map<Integer, Integer> requiredCountByGroup = new HashMap<>();
        for (Map.Entry<Integer, List<GroupStandingEngine.Standing>> entry : standingsByGroup.entrySet()) {
            List<GroupStandingEngine.Standing> groupStandings = entry.getValue();
            if (groupStandings.stream().noneMatch(GroupStandingEngine.Standing::isTieUnresolved)) {
                continue;
            }
            Set<Integer> fixedQualifiedSlots = groupStandings.stream()
                    .filter(standing -> standing.isQualified() && !standing.isTieUnresolved())
                    .map(GroupStandingEngine.Standing::getRank)
                    .collect(Collectors.toSet());
            Set<Integer> requiredSlots = new HashSet<>();
            for (int slot = 1; slot <= safeInt(tournament.getQualifiersPerGroup()); slot++) {
                if (!fixedQualifiedSlots.contains(slot)) {
                    requiredSlots.add(slot);
                }
            }
            requiredSlotsByGroup.put(entry.getKey(), requiredSlots);
            requiredCountByGroup.put(entry.getKey(), requiredSlots.size());
        }

        Map<Integer, Integer> requestedCountByGroup = requested.stream()
                .filter(item -> item != null && item.getGroupNo() != null)
                .collect(Collectors.groupingBy(UpdateQualificationOverridesReq.Item::getGroupNo,
                        Collectors.summingInt(item -> 1)));
        if (!requestedCountByGroup.keySet().equals(requiredCountByGroup.keySet())) {
            throw new IllegalArgumentException("manual qualification must cover every unresolved group");
        }
        for (Map.Entry<Integer, Integer> entry : requiredCountByGroup.entrySet()) {
            if (!java.util.Objects.equals(requestedCountByGroup.get(entry.getKey()), entry.getValue())) {
                throw new IllegalArgumentException("manual qualification count is incomplete");
            }
        }

        List<TournamentQualificationOverride> entities = new ArrayList<>();
        for (UpdateQualificationOverridesReq.Item item : requested) {
            if (item == null || item.getGroupNo() == null || item.getRankSlot() == null
                    || StrUtil.isBlank(item.getPlayerId())) {
                throw new IllegalArgumentException("manual qualification item is incomplete");
            }
            int rankSlot = item.getRankSlot();
            if (rankSlot < 1 || rankSlot > safeInt(tournament.getQualifiersPerGroup())) {
                throw new IllegalArgumentException("manual qualification rank slot is invalid");
            }
            Set<Integer> requiredSlots = requiredSlotsByGroup.get(item.getGroupNo());
            if (requiredSlots == null || !requiredSlots.contains(rankSlot)) {
                throw new IllegalArgumentException("manual qualification rank slot is not unresolved");
            }
            String slotKey = item.getGroupNo() + ":" + rankSlot;
            if (!usedSlots.add(slotKey) || !usedPlayers.add(item.getPlayerId())) {
                throw new IllegalArgumentException("manual qualification contains duplicate slot or player");
            }

            List<GroupStandingEngine.Standing> groupStandings = standingsByGroup.get(item.getGroupNo());
            if (groupStandings == null) {
                throw new IllegalArgumentException("manual qualification group does not exist");
            }
            GroupStandingEngine.Standing selected = groupStandings.stream()
                    .filter(standing -> StrUtil.equals(standing.getPlayerId(), item.getPlayerId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("manual qualification player is not in group"));
            if (!selected.isTieUnresolved()) {
                throw new IllegalArgumentException("manual qualification player is not in an unresolved tie");
            }

            TournamentQualificationOverride entity = new TournamentQualificationOverride();
            entity.setTournamentId(tournamentId);
            entity.setGroupNo(item.getGroupNo());
            entity.setRankSlot(rankSlot);
            entity.setPlayerId(item.getPlayerId());
            entity.setOperatorUserId(userId);
            entities.add(entity);
        }
        clearQualificationOverrides(tournamentId);
        for (TournamentQualificationOverride entity : entities) {
            tournamentQualificationOverrideMapper.insert(entity);
        }
    }

    public List<Player> loadPlayers(String tournamentId) {
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "group_position", "create_time", "id")
        );
    }

    public List<MatchRecord> loadGroupMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", STAGE_GROUP)
                        .orderByAsc("group_no", "round_num", "match_index")
        );
    }

    public List<MatchRecord> loadAllTournamentMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .ne("stage_type", STAGE_TEAM_CHILD)
                .orderByAsc("round_num", "match_index")
        );
    }

    private RankingConfig loadRankingConfig(String tournamentId) {
        TournamentRankingConfig entity = loadRankingConfigEntity(tournamentId);
        if (entity == null) {
            return RankingConfig.legacyDefault();
        }
        try {
            return RankingConfig.fromJson(entity.getConfigJson());
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("invalid tournament ranking config");
        }
    }

    private TournamentRankingConfig loadRankingConfigEntity(String tournamentId) {
        return tournamentRankingConfigMapper.selectOne(
                new QueryWrapper<TournamentRankingConfig>()
                        .eq("tournament_id", tournamentId)
        );
    }

    private RankingConfig parseRankingConfig(UpdateTournamentRankingConfigReq req) {
        String templateValue = req == null ? null : req.getTemplate();
        List<String> priorities = req == null ? null : req.getPriorities();
        RankingConfig base = StrUtil.isBlank(templateValue)
                ? RankingConfig.legacyDefault()
                : parseRankingTemplate(templateValue);

        if (priorities == null || priorities.isEmpty()) {
            if (StrUtil.isNotBlank(templateValue)) {
                return base;
            }
            throw new IllegalArgumentException("at least one ranking criterion is required");
        }

        return new RankingConfig(
                RankingConfig.Template.CUSTOM,
                parsePriorityCriteria(priorities, base),
                base.getMathType(),
                base.isTwoWayTieH2HFirst(),
                base.getWithdrawPolicy(),
                base.getPointsSystem(),
                resolveSystemFallback(priorities, base)
        );
    }

    public RankingConfig parseCreateRankingConfig(Tournament tournament, CreateTournamentReq req) {
        String templateValue = req == null ? null : req.getRankingTemplate();
        List<String> priorities = req == null ? null : req.getRankingPriorities();
        RankingConfig base = StrUtil.isBlank(templateValue)
                ? defaultRankingConfigForSport(tournament.getSportType())
                : parseRankingTemplate(templateValue);

        if (priorities == null || priorities.isEmpty()) {
            return base;
        }

        return new RankingConfig(
                RankingConfig.Template.CUSTOM,
                parsePriorityCriteria(priorities, base),
                base.getMathType(),
                base.isTwoWayTieH2HFirst(),
                base.getWithdrawPolicy(),
                base.getPointsSystem(),
                resolveSystemFallback(priorities, base)
        );
    }

    private RankingConfig defaultRankingConfigForSport(Integer sportType) {
        if (Integer.valueOf(SPORT_VOLLEYBALL).equals(sportType)) {
            return RankingConfig.preset(RankingConfig.Template.FIVB_VOLLEYBALL);
        }
        return RankingConfig.preset(RankingConfig.Template.BWF_BADMINTON);
    }

    private RankingConfig parseRankingTemplate(String template) {
        if ("BADMINTON_COMMON_1".equals(template)) {
            return RankingConfig.preset(RankingConfig.Template.BADMINTON_COMMON_1);
        }
        if ("BADMINTON_TEAM_COMMON_1".equals(template)) {
            return RankingConfig.preset(RankingConfig.Template.BADMINTON_TEAM_COMMON_1);
        }
        if ("BADMINTON_RELAY_COMMON_1".equals(template)) {
            return RankingConfig.preset(RankingConfig.Template.BADMINTON_RELAY_COMMON_1);
        }
        if ("VOLLEYBALL_COMMON_1".equals(template)) {
            return RankingConfig.preset(RankingConfig.Template.VOLLEYBALL_COMMON_1);
        }
        return RankingConfig.preset(parseTemplate(template));
    }

    private RankingConfig.Template parseTemplate(String template) {
        try {
            return RankingConfig.Template.valueOf(template);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unsupported ranking template: " + template);
        }
    }

    private List<RankingConfig.Criterion> parsePriorityCriteria(List<String> priorities,
                                                                RankingConfig base) {
        if (priorities == null || priorities.isEmpty()) {
            throw new IllegalArgumentException("at least one ranking criterion is required");
        }

        List<RankingConfig.Criterion> criteria = new ArrayList<>();
        Set<RankingConfig.Criterion> seen = new HashSet<>();
        for (String value : priorities) {
            if (StrUtil.isBlank(value)) {
                throw new IllegalArgumentException("ranking criterion cannot be blank");
            }
            RankingConfig.Criterion criterion;
            try {
                criterion = RankingConfig.Criterion.valueOf(value);
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("unsupported ranking criterion: " + value);
            }
            if (!seen.add(criterion)) {
                throw new IllegalArgumentException("ranking criteria cannot repeat: " + value);
            }
            criteria.add(criterion);
        }
        RankingConfig.Criterion fallback = usesTeamPointFallback(base)
                ? RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE
                : RankingConfig.Criterion.POINT_WIN_RATE;
        boolean hasPointResolutionCriterion = usesTeamPointFallback(base)
                ? seen.contains(RankingConfig.Criterion.TEAM_CHILD_NET_POINTS)
                || seen.contains(RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE)
                : seen.contains(RankingConfig.Criterion.NET_POINTS)
                || seen.contains(RankingConfig.Criterion.POINT_WIN_RATE);
        if (!hasPointResolutionCriterion) {
            criteria.add(fallback);
        }
        return criteria;
    }

    private boolean usesTeamPointFallback(RankingConfig config) {
        return config != null
                && (config.getTemplate() == RankingConfig.Template.BADMINTON_TEAM_COMMON_1
                || config.getTemplate() == RankingConfig.Template.BADMINTON_RELAY_COMMON_1
                || config.contains(RankingConfig.Criterion.TEAM_ITEM_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_ITEM_NET_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_ITEM_WIN_RATE)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_NET_GAMES)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WIN_RATE)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_NET_POINTS)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE));
    }

    private RankingConfig.Criterion resolveSystemFallback(List<String> priorities,
                                                          RankingConfig base) {
        boolean teamRanking = usesTeamPointFallback(base);
        RankingConfig.Criterion fallback = teamRanking
                ? RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE
                : RankingConfig.Criterion.POINT_WIN_RATE;
        if (priorities == null) {
            return fallback;
        }
        boolean hasPointResolutionCriterion = teamRanking
                ? priorities.contains(RankingConfig.Criterion.TEAM_CHILD_NET_POINTS.name())
                || priorities.contains(RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE.name())
                : priorities.contains(RankingConfig.Criterion.NET_POINTS.name())
                || priorities.contains(RankingConfig.Criterion.POINT_WIN_RATE.name());
        return hasPointResolutionCriterion ? null : fallback;
    }

    private boolean hasFinishedRankingMatch(Tournament tournament) {
        QueryWrapper<MatchRecord> query = new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournament.getId())
                .in("status", 2, 3);
        if (Integer.valueOf(TYPE_GROUP).equals(tournament.getTournamentType())) {
            query.eq("stage_type", STAGE_GROUP);
        } else if (Integer.valueOf(TYPE_ROUND_ROBIN).equals(tournament.getTournamentType())) {
            query.ne("stage_type", STAGE_TEAM_CHILD);
        } else {
            return false;
        }
        return matchRecordMapper.selectCount(query) > 0;
    }

    private TournamentRankingConfigVO toRankingConfigVO(Tournament tournament,
                                                        TournamentRankingConfig entity,
                                                        boolean finishedMatch,
                                                        String currentUserId) {
        RankingConfig rankingConfig = entity == null
                ? RankingConfig.legacyDefault()
                : loadRankingConfig(tournament.getId());
        TournamentRankingConfigVO vo = new TournamentRankingConfigVO();
        vo.setTournamentId(tournament.getId());
        vo.setConfigVersion(entity == null || entity.getConfigVersion() == null ? 1 : entity.getConfigVersion());
        vo.setTemplate(rankingConfig.getTemplate().name());
        vo.setPriorities(rankingConfig.getPriorities().stream().map(Enum::name).collect(Collectors.toList()));
        vo.setSystemFallbackCriterion(rankingConfig.getSystemFallbackCriterion() == null
                ? null
                : rankingConfig.getSystemFallbackCriterion().name());
        vo.setPointsSystemEnabled(rankingConfig.getPointsSystem().enabled());
        vo.setMathType(rankingConfig.getMathType().name());
        vo.setTwoWayTieH2HFirst(rankingConfig.isTwoWayTieH2HFirst());
        vo.setWithdrawPolicy(rankingConfig.getWithdrawPolicy().name());
        boolean locked = finishedMatch || (entity != null && entity.getLockedAt() != null);
        vo.setLocked(locked);
        vo.setLockedAt(entity == null || entity.getLockedAt() == null
                ? null
                : entity.getLockedAt().format(DATETIME_FORMATTER));
        vo.setCreator(StrUtil.equals(currentUserId, tournament.getCreatorUserId()));
        return vo;
    }

    public GroupStandingsVO buildStandingsVO(Tournament tournament, List<Player> players, List<MatchRecord> matches) {
        Map<Integer, List<Player>> playersByGroup;
        Map<Integer, List<MatchRecord>> matchesByGroup;
        if (TYPE_ROUND_ROBIN == tournament.getTournamentType()) {
            playersByGroup = Map.of(1, players);
            matchesByGroup = Map.of(1, matches);
        } else {
            playersByGroup = players.stream()
                    .filter(player -> player.getGroupNo() != null)
                    .collect(Collectors.groupingBy(Player::getGroupNo));
            matchesByGroup = matches.stream()
                    .filter(match -> match.getGroupNo() != null)
                    .collect(Collectors.groupingBy(MatchRecord::getGroupNo));
        }

        List<GroupStandingsVO.GroupVO> groups = new ArrayList<>();
        boolean hasUnresolvedTie = false;
        RankingConfig rankingConfig = loadRankingConfig(tournament.getId());
        List<MatchRecord> rankingMatches = enrichTeamRankingMatchesIfNeeded(tournament, matches, rankingConfig);
        boolean allFinished = allRankingMatchesFinished(rankingMatches, rankingConfig);
        Map<Integer, List<TournamentQualificationOverride>> overridesByGroup =
                loadQualificationOverrides(tournament.getId()).stream()
                        .collect(Collectors.groupingBy(TournamentQualificationOverride::getGroupNo));

        for (Integer groupNo : playersByGroup.keySet().stream().sorted().collect(Collectors.toList())) {
            List<GroupStandingEngine.Standing> standings = buildGroupStandingsWithEngine(
                    playersByGroup.getOrDefault(groupNo, List.of()),
                    rankingMatches.stream()
                            .filter(match -> TYPE_ROUND_ROBIN == tournament.getTournamentType()
                                    || java.util.Objects.equals(match.getGroupNo(), groupNo))
                            .collect(Collectors.toList()),
                    tournament.getQualifiersPerGroup(),
                    rankingConfig
            );
            applyQualificationOverrides(standings, overridesByGroup.getOrDefault(groupNo, List.of()));
            if (standings.stream().anyMatch(GroupStandingEngine.Standing::isTieUnresolved)) {
                hasUnresolvedTie = true;
            }

            GroupStandingsVO.GroupVO group = new GroupStandingsVO.GroupVO();
            group.setGroupNo(groupNo);
            group.setStandings(standings.stream()
                    .map(standing -> toStandingVO(standing, TYPE_ROUND_ROBIN == tournament.getTournamentType()))
                    .collect(Collectors.toList()));
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

    private List<MatchRecord> enrichTeamRankingMatchesIfNeeded(Tournament tournament,
                                                               List<MatchRecord> matches,
                                                               RankingConfig rankingConfig) {
        List<MatchRecord> source = matches == null ? List.of() : matches;
        if (tournament == null
                || !Integer.valueOf(SPORT_BADMINTON).equals(tournament.getSportType())
                || !Integer.valueOf(PARTICIPANT_TEAM).equals(tournament.getParticipantType())
                || rankingConfig == null
                || (!rankingConfig.contains(RankingConfig.Criterion.TEAM_ITEM_NET_WINS)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_ITEM_WINS)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_ITEM_WIN_RATE)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WINS)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_CHILD_NET_GAMES)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WIN_RATE)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_CHILD_NET_POINTS)
                && !rankingConfig.contains(RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE))) {
            return source;
        }
        List<String> parentIds = source.stream()
                .map(MatchRecord::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        if (parentIds.isEmpty()) {
            return source;
        }
        List<TeamMatchItem> items = teamMatchItemMapper.selectList(
                new QueryWrapper<TeamMatchItem>()
                        .in("match_id", parentIds)
                        .isNotNull("child_match_id")
        );
        if (CollUtil.isEmpty(items)) {
            return source;
        }
        List<String> childIds = items.stream()
                .map(TeamMatchItem::getChildMatchId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());
        if (childIds.isEmpty()) {
            return source;
        }
        Map<String, MatchRecord> childMap = matchRecordMapper.selectList(
                        new QueryWrapper<MatchRecord>().in("id", childIds)
                ).stream()
                .collect(Collectors.toMap(MatchRecord::getId, match -> match, (a, b) -> a));
        Map<String, List<MatchRecord>> childrenByParent = items.stream()
                .filter(item -> childMap.containsKey(item.getChildMatchId()))
                .collect(Collectors.groupingBy(TeamMatchItem::getMatchId,
                        Collectors.mapping(item -> childMap.get(item.getChildMatchId()), Collectors.toList())));

        return source.stream()
                .map(match -> enrichTeamRankingMatch(match, childrenByParent.get(match.getId())))
                .collect(Collectors.toList());
    }

    private MatchRecord enrichTeamRankingMatch(MatchRecord source, List<MatchRecord> childMatches) {
        if (source == null || CollUtil.isEmpty(childMatches)) {
            return source;
        }
        MatchRecord enriched = copyMatchRecord(source);
        JSONArray scores = new JSONArray();
        for (MatchRecord child : childMatches) {
            if (child == null || StrUtil.isBlank(child.getGameScores())) {
                continue;
            }
            for (Object score : JSONUtil.parseArray(child.getGameScores())) {
                scores.add(score);
            }
        }
        if (scores.size() > 0) {
            enriched.setGameScores(scores.toString());
        }
        return enriched;
    }

    private MatchRecord copyMatchRecord(MatchRecord source) {
        MatchRecord copy = new MatchRecord();
        copy.setId(source.getId());
        copy.setTournamentId(source.getTournamentId());
        copy.setRoundNum(source.getRoundNum());
        copy.setMatchIndex(source.getMatchIndex());
        copy.setStageType(source.getStageType());
        copy.setMatchRole(source.getMatchRole());
        copy.setGroupNo(source.getGroupNo());
        copy.setLeftPlayerId(source.getLeftPlayerId());
        copy.setRightPlayerId(source.getRightPlayerId());
        copy.setScoreDisplay(source.getScoreDisplay());
        copy.setWinnerId(source.getWinnerId());
        copy.setLeftGameWins(source.getLeftGameWins());
        copy.setRightGameWins(source.getRightGameWins());
        copy.setGameScores(source.getGameScores());
        copy.setStatus(source.getStatus());
        copy.setNextMatchId(source.getNextMatchId());
        copy.setNextMatchSlot(source.getNextMatchSlot());
        copy.setLoserNextMatchId(source.getLoserNextMatchId());
        copy.setLoserNextMatchSlot(source.getLoserNextMatchSlot());
        copy.setRetiredSide(source.getRetiredSide());
        return copy;
    }

    private boolean allRankingMatchesFinished(List<MatchRecord> matches, RankingConfig rankingConfig) {
        List<MatchRecord> source = matches == null ? List.of() : matches;
        if (rankingConfig.getWithdrawPolicy() != RankingConfig.WithdrawPolicy.DELETE_ALL) {
            return source.stream().allMatch(this::isFinishedOrRetired);
        }

        Set<String> withdrawnIds = GroupStandingEngine.withdrawnParticipantIds(source);
        return source.stream()
                .filter(match -> match != null
                        && !withdrawnIds.contains(match.getLeftPlayerId())
                        && !withdrawnIds.contains(match.getRightPlayerId()))
                .allMatch(this::isFinishedOrRetired);
    }

    private boolean isFinishedOrRetired(MatchRecord match) {
        return match != null
                && (Integer.valueOf(2).equals(match.getStatus())
                || Integer.valueOf(3).equals(match.getStatus()));
    }

    private List<GroupStandingEngine.Standing> buildGroupStandingsWithEngine(List<Player> players,
                                                                              List<MatchRecord> matches,
                                                                              Integer qualifiersPerGroup,
                                                                              RankingConfig rankingConfig) {
        return groupStandingEngine.rank(players, matches, qualifiersPerGroup, rankingConfig);
    }

    private GroupStandingsVO.StandingVO toStandingVO(GroupStandingEngine.Standing standing, boolean roundRobin) {
        GroupStandingsVO.StandingVO vo = new GroupStandingsVO.StandingVO();
        vo.setPlayerId(standing.getPlayerId());
        vo.setPlayerName(standing.getPlayerName());
        vo.setSeedRank(standing.getSeedRank());
        vo.setRank(standing.getRank());
        vo.setDisplayRankText(standing.getDisplayRankText());
        vo.setQualified(standing.isQualified());
        vo.setTieUnresolved(standing.isTieUnresolved());
        vo.setManualQualified(standing.isManualQualified());
        vo.setMatchWins(standing.getMatchWins());
        vo.setMatchLosses(standing.getMatchLosses());
        vo.setMatchWinRate(standing.getMatchWinRate().toPlainString());
        vo.setMatchPoints(standing.getMatchPoints());
        vo.setTeamItemWins(standing.getTeamItemWins());
        vo.setTeamItemLosses(standing.getTeamItemLosses());
        vo.setTeamItemNetWins(standing.getTeamItemNetWins());
        vo.setTeamItemWinRate(standing.getTeamItemWinRate().toPlainString());
        vo.setGameWins(standing.getGameWins());
        vo.setGameLosses(standing.getGameLosses());
        vo.setNetGames(standing.getNetGames());
        vo.setPointsFor(standing.getPointsFor());
        vo.setPointsAgainst(standing.getPointsAgainst());
        vo.setNetPoints(standing.getNetPoints());
        vo.setGameWinRate(standing.getGameWinRate().toPlainString());
        vo.setPointWinRate(standing.getPointWinRate().toPlainString());
        return vo;
    }

    private List<TournamentQualificationOverride> loadQualificationOverrides(String tournamentId) {
        return tournamentQualificationOverrideMapper.selectList(
                new QueryWrapper<TournamentQualificationOverride>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("group_no", "rank_slot")
        );
    }

    private void applyQualificationOverrides(List<GroupStandingEngine.Standing> standings,
                                             List<TournamentQualificationOverride> overrides) {
        if (CollUtil.isEmpty(overrides)) {
            return;
        }
        standings.stream()
                .filter(GroupStandingEngine.Standing::isTieUnresolved)
                .forEach(GroupStandingEngine.Standing::clearManualTie);
        for (TournamentQualificationOverride override : overrides) {
            standings.stream()
                    .filter(standing -> StrUtil.equals(standing.getPlayerId(), override.getPlayerId()))
                    .findFirst()
                    .ifPresent(standing -> standing.applyManualQualification(override.getRankSlot()));
        }
    }

    private void clearQualificationOverrides(String tournamentId) {
        tournamentQualificationOverrideMapper.delete(
                new QueryWrapper<TournamentQualificationOverride>()
                        .eq("tournament_id", tournamentId)
        );
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
