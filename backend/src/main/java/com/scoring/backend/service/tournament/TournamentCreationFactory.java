package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.engine.ranking.RankingConfig;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.service.tournament.TournamentAccessGuard;
import com.scoring.backend.service.tournament.TournamentRankingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.function.Function;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛事创建工厂：三种运动形态（羽毛球个人/羽毛球团体/排球）的创建流程，
 * 含规则解析、轮次模板、参赛名单归一化校验、比赛生成与持久化。
 * 写路径服务；赛制相关常量在本类与门面各有一份（门面读侧仍需）。
 */
@Service
public class TournamentCreationFactory {

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

    private final TournamentMapper tournamentMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentRankingConfigMapper tournamentRankingConfigMapper;
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final BracketEngine bracketEngine;
    private final RoundRobinEngine roundRobinEngine;
    private final TournamentAccessGuard accessGuard;
    private final TournamentRankingService rankingService;

    public TournamentCreationFactory(            TournamentMapper tournamentMapper,
            PlayerMapper playerMapper,
            MatchRecordMapper matchRecordMapper,
            TournamentRankingConfigMapper tournamentRankingConfigMapper,
            TournamentRefereeConfigMapper tournamentRefereeConfigMapper,
            TeamMatchItemMapper teamMatchItemMapper,
            TournamentRoundRuleMapper tournamentRoundRuleMapper,
            TournamentTeamMemberMapper tournamentTeamMemberMapper,
            BracketEngine bracketEngine,
            RoundRobinEngine roundRobinEngine,
            TournamentAccessGuard accessGuard,
            TournamentRankingService rankingService) {
        this.tournamentMapper = tournamentMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentRankingConfigMapper = tournamentRankingConfigMapper;
        this.tournamentRefereeConfigMapper = tournamentRefereeConfigMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
        this.accessGuard = accessGuard;
        this.rankingService = rankingService;
    }

    @Transactional(rollbackFor = Exception.class)
    public String createTournament(String creatorUserId, CreateTournamentReq req) {
        if (StrUtil.isBlank(creatorUserId)) {
            throw new IllegalArgumentException("\u8bf7\u5148\u767b\u5f55");
        }
        accessGuard.requireCompletedProfile(creatorUserId);
        if (req == null || StrUtil.isBlank(req.getName())) {
            throw new IllegalArgumentException("\u8d5b\u4e8b\u540d\u79f0\u4e0d\u80fd\u4e3a\u7a7a");
        }

        int sportType = resolveSportType(req);
        int participantType = resolveParticipantType(req, sportType);
        if (sportType == SPORT_VOLLEYBALL) {
            return createVolleyballTournament(creatorUserId, req);
        }
        if (participantType == PARTICIPANT_TEAM) {
            return createBadmintonTeamTournament(creatorUserId, req);
        }
        return createBadmintonIndividualTournament(creatorUserId, req);
    }

    private int resolveSportType(CreateTournamentReq req) {
        int sportType = req.getSportType() == null ? SPORT_BADMINTON : req.getSportType();
        if (sportType != SPORT_BADMINTON && sportType != SPORT_VOLLEYBALL) {
            throw new IllegalArgumentException("sportType must be 0 or 1");
        }
        return sportType;
    }

    private int resolveParticipantType(CreateTournamentReq req, int sportType) {
        if (sportType == SPORT_VOLLEYBALL) {
            if (req.getParticipantType() != null && !Integer.valueOf(PARTICIPANT_TEAM).equals(req.getParticipantType())) {
                throw new IllegalArgumentException("排球赛事固定为团体赛");
            }
            return PARTICIPANT_TEAM;
        }
        int participantType = req.getParticipantType() == null ? PARTICIPANT_INDIVIDUAL : req.getParticipantType();
        if (participantType != PARTICIPANT_INDIVIDUAL && participantType != PARTICIPANT_TEAM) {
            throw new IllegalArgumentException("participantType must be 0 or 1");
        }
        return participantType;
    }

    private String createBadmintonIndividualTournament(String creatorUserId, CreateTournamentReq req) {
        if (CollUtil.isNotEmpty(req.getTeams())) {
            throw new IllegalArgumentException("\u4e2a\u4eba\u8d5b\u4e0d\u652f\u6301\u961f\u4f0d\u5217\u8868");
        }
        ensureNoTeamMatchTemplate(req);
        List<CreateTournamentReq.PlayerEntry> entries = normalizePlayers(req.getPlayers());

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_BADMINTON);
        tournament.setParticipantType(PARTICIPANT_INDIVIDUAL);
        tournament.setTeamMatchTemplate(TEAM_MATCH_TEMPLATE_NONE);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        applyRule(tournament, req.getRule());
        applyTournamentType(tournament, req, entries.size());
        applyRoundRuleFlag(tournament, req);
        applyThirdPlaceRule(tournament, req, entries.size());

        return persistTournament(tournament, tournamentId -> buildPlayers(tournamentId, entries), List.of(), req, entries.size());
    }

    private String createBadmintonTeamTournament(String creatorUserId, CreateTournamentReq req) {
        if (CollUtil.isNotEmpty(req.getPlayers())) {
            throw new IllegalArgumentException("\u56e2\u4f53\u8d5b\u8bf7\u4f7f\u7528\u961f\u4f0d\u5217\u8868");
        }
        int teamMatchTemplate = resolveBadmintonTeamMatchTemplate(req);
        List<CreateTournamentReq.TeamEntry> teams = normalizeBadmintonTeams(req.getTeams());
        if (teams.size() < 2) {
            throw new IllegalArgumentException("\u81f3\u5c11\u9700\u89812\u652f\u961f\u4f0d");
        }
        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_BADMINTON);
        tournament.setParticipantType(PARTICIPANT_TEAM);
        tournament.setTeamMatchTemplate(teamMatchTemplate);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        if (teamMatchTemplate == TEAM_MATCH_TEMPLATE_RELAY) {
            applyRelayRule(tournament, req.getRule());
            validateRelayTeamCapacity(teams, tournament.getCapPoint());
        } else {
            applyRule(tournament, req.getRule());
        }
        applyTournamentType(tournament, req, teams.size());
        applyRoundRuleFlag(tournament, req);
        applyThirdPlaceRule(tournament, req, teams.size());

        return persistTournament(tournament, tournamentId -> buildTeamParticipants(tournamentId, teams), teams, req, teams.size());
    }

    private int resolveBadmintonTeamMatchTemplate(CreateTournamentReq req) {
        int template = req.getTeamMatchTemplate() == null ? TEAM_MATCH_TEMPLATE_SUDIRMAN_5 : req.getTeamMatchTemplate();
        if (template != TEAM_MATCH_TEMPLATE_SUDIRMAN_5 && template != TEAM_MATCH_TEMPLATE_RELAY) {
            throw new IllegalArgumentException("\u672a\u77e5\u7684\u7fbd\u6bdb\u7403\u56e2\u4f53\u6a21\u677f");
        }
        return template;
    }

    private void applyRelayRule(Tournament tournament, CreateTournamentReq.RuleConfig rule) {
        int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_POINTS_TO_WIN : rule.getPointsToWin();
        validatePointsToWin(pointsToWin);
        tournament.setBestOf(1);
        tournament.setGamesToWin(1);
        tournament.setPointsToWin(pointsToWin);
        tournament.setEnableDeuce(false);
        tournament.setDecidingPointsToWin(null);
        int relayMemberCount = rule == null || rule.getCapPoint() == null ? DEFAULT_RELAY_MEMBER_COUNT : rule.getCapPoint();
        tournament.setCapPoint(Math.max(3, Math.min(12, relayMemberCount)));
    }

    private void applyRoundRuleFlag(Tournament tournament, CreateTournamentReq req) {
        boolean enabled = Boolean.TRUE.equals(req.getRoundRuleEnabled());
        if (enabled && TYPE_ROUND_ROBIN == tournament.getTournamentType()) {
            throw new IllegalArgumentException("循环赛暂不支持分轮规则");
        }
        if (enabled && Integer.valueOf(TEAM_MATCH_TEMPLATE_RELAY).equals(tournament.getTeamMatchTemplate())) {
            throw new IllegalArgumentException("接力追分赛暂不支持分轮规则");
        }
        tournament.setRoundRuleEnabled(enabled);
    }

    private void applyThirdPlaceRule(Tournament tournament, CreateTournamentReq req, int participantCount) {
        boolean enabled = Boolean.TRUE.equals(req.getThirdPlaceEnabled());
        if (enabled && TYPE_ROUND_ROBIN == tournament.getTournamentType()) {
            throw new IllegalArgumentException("循环赛不支持季军赛");
        }
        if (enabled) {
            int knockoutSize = TYPE_GROUP == tournament.getTournamentType()
                    ? safeInt(tournament.getKnockoutSlots())
                    : participantCount;
            if (knockoutSize < 4) {
                throw new IllegalArgumentException("季军赛至少需要4个淘汰阶段参赛单位");
            }
        }

        tournament.setThirdPlaceEnabled(enabled);
        RuleValues values = resolveThirdPlaceRuleValues(tournament, req, enabled);
        tournament.setThirdPlaceBestOf(values.bestOf());
        tournament.setThirdPlaceGamesToWin(values.gamesToWin());
        tournament.setThirdPlacePointsToWin(values.pointsToWin());
        tournament.setThirdPlaceDecidingPointsToWin(values.decidingPointsToWin());
        tournament.setThirdPlaceEnableDeuce(values.enableDeuce());
        tournament.setThirdPlaceCapPoint(values.capPoint());
    }

    private RuleValues resolveThirdPlaceRuleValues(Tournament tournament, CreateTournamentReq req, boolean enabled) {
        CreateTournamentReq.RuleConfig rule = enabled && req.getThirdPlaceRule() != null ? req.getThirdPlaceRule() : req.getRule();
        if (Integer.valueOf(TEAM_MATCH_TEMPLATE_RELAY).equals(tournament.getTeamMatchTemplate())) {
            int pointsToWin = rule == null || rule.getPointsToWin() == null ? tournament.getPointsToWin() : rule.getPointsToWin();
            validatePointsToWin(pointsToWin);
            return new RuleValues(1, 1, pointsToWin, null, false, tournament.getCapPoint());
        }
        return resolveRuleValues(tournament.getSportType(), rule);
    }

    private void validateRelayTeamCapacity(List<CreateTournamentReq.TeamEntry> teams, int relayMemberCount) {
        for (CreateTournamentReq.TeamEntry team : teams) {
            int size = team.getMembers() == null ? 0 : team.getMembers().size();
            if (size < relayMemberCount) {
                throw new IllegalArgumentException(team.getName() + " \u62a5\u540d\u4eba\u6570\u4e0d\u80fd\u5c11\u4e8e\u63a5\u529b\u8d5b\u56fa\u5b9a\u8f6e\u8f6c\u4eba\u6570 " + relayMemberCount);
            }
        }
    }

    private void ensureNoTeamMatchTemplate(CreateTournamentReq req) {
        if (req.getTeamMatchTemplate() != null && req.getTeamMatchTemplate() != TEAM_MATCH_TEMPLATE_NONE) {
            throw new IllegalArgumentException("\u4ec5\u7fbd\u6bdb\u7403\u56e2\u4f53\u8d5b\u652f\u6301\u56e2\u4f53\u6a21\u677f");
        }
    }

    private List<MatchRecord> generateMatchesForType(Tournament tournament, List<Player> participants) {
        if (TYPE_ROUND_ROBIN == tournament.getTournamentType()) {
            int rounds = tournament.getRoundRobinRounds() == null ? 1 : tournament.getRoundRobinRounds();
            return roundRobinEngine.generateLeagueMatches(tournament.getId(), participants, rounds);
        }
        if (TYPE_GROUP == tournament.getTournamentType()) {
            return roundRobinEngine.generateGroupMatches(tournament.getId(), participants);
        }
        return appendThirdPlaceMatch(tournament, bracketEngine.generateKnockoutBracket(tournament.getId(), participants));
    }

    public List<MatchRecord> appendThirdPlaceMatch(Tournament tournament, List<MatchRecord> matches) {
        if (!Boolean.TRUE.equals(tournament.getThirdPlaceEnabled())) {
            return matches;
        }
        int finalRound = tournament.getKnockoutRounds() == null
                ? matches.stream().map(MatchRecord::getRoundNum).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0)
                : tournament.getKnockoutRounds();
        if (finalRound < 2) {
            throw new IllegalArgumentException("季军赛至少需要4个淘汰阶段参赛单位");
        }
        List<MatchRecord> semifinals = matches.stream()
                .filter(match -> Integer.valueOf(STAGE_KNOCKOUT).equals(match.getStageType()))
                .filter(match -> Integer.valueOf(finalRound - 1).equals(match.getRoundNum()))
                .sorted(Comparator.comparing(match -> safeInt(match.getMatchIndex())))
                .toList();
        if (semifinals.size() != 2) {
            throw new IllegalStateException("季军赛需要两场半决赛");
        }

        int thirdPlaceIndex = matches.stream()
                .filter(match -> Integer.valueOf(STAGE_KNOCKOUT).equals(match.getStageType()))
                .filter(match -> Integer.valueOf(finalRound).equals(match.getRoundNum()))
                .map(MatchRecord::getMatchIndex)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(-1) + 1;

        MatchRecord thirdPlace = new MatchRecord();
        thirdPlace.setId(IdUtil.simpleUUID());
        thirdPlace.setTournamentId(tournament.getId());
        thirdPlace.setStageType(STAGE_KNOCKOUT);
        thirdPlace.setMatchRole(MATCH_ROLE_THIRD_PLACE);
        thirdPlace.setRoundNum(finalRound);
        thirdPlace.setMatchIndex(thirdPlaceIndex);
        thirdPlace.setStatus(0);

        semifinals.get(0).setLoserNextMatchId(thirdPlace.getId());
        semifinals.get(0).setLoserNextMatchSlot("left");
        semifinals.get(1).setLoserNextMatchId(thirdPlace.getId());
        semifinals.get(1).setLoserNextMatchSlot("right");

        List<MatchRecord> result = new ArrayList<>(matches);
        result.add(thirdPlace);
        return result;
    }

    private String createVolleyballTournament(String creatorUserId, CreateTournamentReq req) {
        ensureNoTeamMatchTemplate(req);
        List<CreateTournamentReq.TeamEntry> teams = normalizeVolleyballTeams(req.getTeams());
        if (teams.size() < 2) {
            throw new IllegalArgumentException("\u81f3\u5c11\u9700\u89812\u652f\u961f\u4f0d");
        }

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_VOLLEYBALL);
        tournament.setParticipantType(PARTICIPANT_TEAM);
        tournament.setTeamMatchTemplate(TEAM_MATCH_TEMPLATE_NONE);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        applyVolleyballRule(tournament, req.getRule());
        applyTournamentType(tournament, req, teams.size());
        applyRoundRuleFlag(tournament, req);
        applyThirdPlaceRule(tournament, req, teams.size());

        return persistTournament(tournament, tournamentId -> buildTeamParticipants(tournamentId, teams), teams, req, teams.size());
    }

    private String persistTournament(Tournament tournament,
                                     Function<String, List<Player>> participantBuilder,
                                     List<CreateTournamentReq.TeamEntry> teams,
                                     CreateTournamentReq req,
                                     int participantCount) {
        tournamentMapper.insert(tournament);

        saveRefereeConfigIfPresent(tournament.getId(), req.getRefereePassword());
        saveRoundRulesIfNeeded(tournament, req, participantCount);
        saveInitialRankingConfigIfNeeded(tournament, req);

        List<Player> participants = participantBuilder.apply(tournament.getId());
        for (Player participant : participants) {
            participant.setTournamentId(tournament.getId());
        }

        if (TYPE_GROUP == tournament.getTournamentType()) {
            int groupCount = tournament.getKnockoutSlots() / tournament.getQualifiersPerGroup();
            assignGroups(participants, groupCount);
        }
        for (Player participant : participants) {
            playerMapper.insert(participant);
        }

        if (CollUtil.isNotEmpty(teams)) {
            for (int i = 0; i < teams.size(); i++) {
                insertTeamMembers(tournament.getId(), participants.get(i).getId(), teams.get(i).getMembers());
            }
        }

        List<MatchRecord> matches = generateMatchesForType(tournament, participants);
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

    private void saveInitialRankingConfigIfNeeded(Tournament tournament, CreateTournamentReq req) {
        if (tournament == null || tournament.getTournamentType() == null
                || (tournament.getTournamentType() != TYPE_GROUP
                && tournament.getTournamentType() != TYPE_ROUND_ROBIN)) {
            return;
        }
        RankingConfig rankingConfig = rankingService.parseCreateRankingConfig(tournament, req);
        TournamentRankingConfig entity = new TournamentRankingConfig();
        entity.setTournamentId(tournament.getId());
        entity.setConfigVersion(1);
        entity.setConfigJson(rankingConfig.toJson());
        tournamentRankingConfigMapper.insert(entity);
    }

    private void saveRoundRulesIfNeeded(Tournament tournament, CreateTournamentReq req, int participantCount) {
        if (!Boolean.TRUE.equals(tournament.getRoundRuleEnabled())) {
            return;
        }
        List<RoundRuleScope> expectedScopes = expectedRoundRuleScopes(tournament, participantCount);
        Map<String, String> expectedLabels = expectedScopes.stream()
                .collect(Collectors.toMap(RoundRuleScope::key, RoundRuleScope::label));
        Map<String, CreateTournamentReq.RoundRuleConfig> submitted = new HashMap<>();
        for (CreateTournamentReq.RoundRuleConfig item : req.getRoundRules() == null ? List.<CreateTournamentReq.RoundRuleConfig>of() : req.getRoundRules()) {
            if (item == null || item.getStageType() == null || item.getRoundNum() == null) {
                throw new IllegalArgumentException("分轮规则存在无效作用域");
            }
            String key = roundRuleKey(item.getStageType(), item.getRoundNum());
            if (submitted.put(key, item) != null) {
                throw new IllegalArgumentException("分轮规则重复: " + expectedLabels.getOrDefault(key, "非法作用域"));
            }
        }

        Set<String> expectedKeys = expectedScopes.stream().map(RoundRuleScope::key).collect(Collectors.toSet());
        Set<String> submittedKeys = new HashSet<>(submitted.keySet());
        if (!submittedKeys.equals(expectedKeys)) {
            throw new IllegalArgumentException("分轮规则必须完整覆盖: " + expectedScopes.stream()
                    .map(RoundRuleScope::label)
                    .collect(Collectors.joining("、")));
        }

        for (RoundRuleScope scope : expectedScopes) {
            CreateTournamentReq.RoundRuleConfig item = submitted.get(scope.key());
            TournamentRoundRule rule = toRoundRule(tournament, scope.stageType(), scope.roundNum(), item.getRule());
            tournamentRoundRuleMapper.insert(rule);
        }
    }

    private List<RoundRuleScope> expectedRoundRuleScopes(Tournament tournament, int participantCount) {
        List<RoundRuleScope> scopes = new ArrayList<>();
        if (TYPE_GROUP == tournament.getTournamentType()) {
            scopes.add(new RoundRuleScope(STAGE_GROUP, 0, "小组赛"));
            int roundCount = tournament.getKnockoutRounds() == null
                    ? Integer.numberOfTrailingZeros(tournament.getKnockoutSlots())
                    : tournament.getKnockoutRounds();
            int capacity = tournament.getKnockoutSlots() == null ? 1 << roundCount : tournament.getKnockoutSlots();
            addKnockoutScopes(scopes, capacity, roundCount);
            return scopes;
        }
        if (TYPE_KNOCKOUT == tournament.getTournamentType()) {
            if (participantCount < 2) {
                throw new IllegalArgumentException("至少2名参赛方才可启用分轮规则");
            }
            int roundCount = tournament.getKnockoutRounds() == null
                    ? Integer.numberOfTrailingZeros(calcPowerOfTwoCapacity(participantCount))
                    : tournament.getKnockoutRounds();
            int capacity = 1 << roundCount;
            addKnockoutScopes(scopes, capacity, roundCount);
            return scopes;
        }
        throw new IllegalArgumentException("循环赛暂不支持分轮规则");
    }

    private void addKnockoutScopes(List<RoundRuleScope> scopes, int capacity, int roundCount) {
        for (int round = 1; round <= roundCount; round++) {
            int from = capacity >> (round - 1);
            int to = from >> 1;
            String label = to == 1 ? "决赛" : from + "进" + to;
            scopes.add(new RoundRuleScope(STAGE_KNOCKOUT, round, label));
        }
    }

    private TournamentRoundRule toRoundRule(Tournament tournament, int stageType, int roundNum, CreateTournamentReq.RuleConfig ruleConfig) {
        RuleValues values = resolveRuleValues(tournament.getSportType(), ruleConfig);
        TournamentRoundRule rule = new TournamentRoundRule();
        rule.setTournamentId(tournament.getId());
        rule.setStageType(stageType);
        rule.setRoundNum(roundNum);
        rule.setBestOf(values.bestOf());
        rule.setGamesToWin(values.gamesToWin());
        rule.setPointsToWin(values.pointsToWin());
        rule.setDecidingPointsToWin(values.decidingPointsToWin());
        rule.setEnableDeuce(values.enableDeuce());
        rule.setCapPoint(values.capPoint());
        return rule;
    }

    private RuleValues resolveRuleValues(Integer sportType, CreateTournamentReq.RuleConfig rule) {
        if (Integer.valueOf(SPORT_VOLLEYBALL).equals(sportType)) {
            int bestOf = rule == null || rule.getBestOf() == null ? DEFAULT_BEST_OF : rule.getBestOf();
            int gamesToWin = rule == null || rule.getGamesToWin() == null ? DEFAULT_GAMES_TO_WIN : rule.getGamesToWin();
            int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_VOLLEYBALL_POINTS_TO_WIN : rule.getPointsToWin();
            int decidingPointsToWin = rule == null || rule.getDecidingPointsToWin() == null ? DEFAULT_VOLLEYBALL_DECIDING_POINTS_TO_WIN : rule.getDecidingPointsToWin();
            boolean enableDeuce = rule == null || rule.getEnableDeuce() == null ? DEFAULT_ENABLE_DEUCE : rule.getEnableDeuce();
            int capPoint = rule == null || rule.getCapPoint() == null ? DEFAULT_VOLLEYBALL_CAP_POINT : rule.getCapPoint();
            if (bestOf != 3 && bestOf != 5) {
                throw new IllegalArgumentException("排球只支持三局两胜或五局三胜");
            }
            if (gamesToWin != bestOf / 2 + 1) {
                throw new IllegalArgumentException("gamesToWin does not match bestOf");
            }
            validatePointRule(pointsToWin, enableDeuce, capPoint);
            if (decidingPointsToWin < 1 || decidingPointsToWin > pointsToWin) {
                throw new IllegalArgumentException("decidingPointsToWin must be between 1 and pointsToWin");
            }
            return new RuleValues(bestOf, gamesToWin, pointsToWin, decidingPointsToWin, enableDeuce, capPoint);
        }

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
        validatePointRule(pointsToWin, enableDeuce, capPoint);
        return new RuleValues(bestOf, gamesToWin, pointsToWin, null, enableDeuce, capPoint);
    }

    private String roundRuleKey(Integer stageType, Integer roundNum) {
        return stageType + ":" + roundNum;
    }

    private int calcPowerOfTwoCapacity(int count) {
        int capacity = 1;
        while (capacity < count) {
            capacity <<= 1;
        }
        return capacity;
    }

    private record RoundRuleScope(int stageType, int roundNum, String label) {
        private String key() {
            return stageType + ":" + roundNum;
        }
    }

    private record RuleValues(Integer bestOf,
                              Integer gamesToWin,
                              Integer pointsToWin,
                              Integer decidingPointsToWin,
                              Boolean enableDeuce,
                              Integer capPoint) {
    }

    private List<CreateTournamentReq.PlayerEntry> normalizePlayers(List<CreateTournamentReq.PlayerEntry> rawPlayers) {
        if (CollUtil.isEmpty(rawPlayers)) {
            throw new IllegalArgumentException("\u4e2a\u4eba\u8d5b\u8bf7\u586b\u5199\u9009\u624b\u5217\u8868");
        }
        List<CreateTournamentReq.PlayerEntry> entries = rawPlayers.stream()
                .filter(p -> p != null && StrUtil.isNotBlank(p.getName()))
                .map(p -> {
                    CreateTournamentReq.PlayerEntry clean = new CreateTournamentReq.PlayerEntry();
                    clean.setName(p.getName().trim());
                    clean.setSeed(p.getSeed());
                    return clean;
                })
                .collect(Collectors.toList());
        if (entries.size() < 2) {
            throw new IllegalArgumentException("\u81f3\u5c11\u9700\u89812\u540d\u9009\u624b");
        }
        return entries;
    }

    private List<CreateTournamentReq.TeamEntry> normalizeVolleyballTeams(List<CreateTournamentReq.TeamEntry> rawTeams) {
        return normalizeTeams(rawTeams, true);
    }

    private List<CreateTournamentReq.TeamEntry> normalizeBadmintonTeams(List<CreateTournamentReq.TeamEntry> rawTeams) {
        return normalizeTeams(rawTeams, false);
    }

    private List<CreateTournamentReq.TeamEntry> normalizeTeams(List<CreateTournamentReq.TeamEntry> rawTeams, boolean volleyball) {
        if (CollUtil.isEmpty(rawTeams)) {
            throw new IllegalArgumentException("\u56e2\u4f53\u8d5b\u8bf7\u586b\u5199\u961f\u4f0d\u5217\u8868");
        }

        List<CreateTournamentReq.TeamEntry> teams = new ArrayList<>();
        for (CreateTournamentReq.TeamEntry team : rawTeams) {
            if (team == null || StrUtil.isBlank(team.getName())) {
                continue;
            }
            CreateTournamentReq.TeamEntry cleanTeam = new CreateTournamentReq.TeamEntry();
            cleanTeam.setName(team.getName().trim());
            cleanTeam.setSeed(team.getSeed());
            cleanTeam.setMembers(volleyball
                    ? normalizeVolleyballMembers(team.getMembers(), cleanTeam.getName())
                    : normalizeBadmintonMembers(team.getMembers(), cleanTeam.getName()));
            teams.add(cleanTeam);
        }
        return teams;
    }

    private List<CreateTournamentReq.TeamMemberEntry> normalizeVolleyballMembers(List<CreateTournamentReq.TeamMemberEntry> rawMembers, String teamName) {
        List<CreateTournamentReq.TeamMemberEntry> members = normalizeTeamMembers(rawMembers, true);
        validateVolleyballTeamMembers(teamName, members);
        return members;
    }

    private List<CreateTournamentReq.TeamMemberEntry> normalizeBadmintonMembers(List<CreateTournamentReq.TeamMemberEntry> rawMembers, String teamName) {
        List<CreateTournamentReq.TeamMemberEntry> members = normalizeTeamMembers(rawMembers, false);
        validateBadmintonTeamMembers(teamName, members);
        return members;
    }

    private List<CreateTournamentReq.TeamMemberEntry> normalizeTeamMembers(List<CreateTournamentReq.TeamMemberEntry> rawMembers, boolean keepJerseyNumber) {
        List<CreateTournamentReq.TeamMemberEntry> members = new ArrayList<>();
        if (rawMembers != null) {
            for (CreateTournamentReq.TeamMemberEntry member : rawMembers) {
                if (member == null || StrUtil.isBlank(member.getName())) {
                    continue;
                }
                CreateTournamentReq.TeamMemberEntry cleanMember = new CreateTournamentReq.TeamMemberEntry();
                cleanMember.setName(member.getName().trim());
                cleanMember.setJerseyNumber(keepJerseyNumber ? member.getJerseyNumber() : null);
                cleanMember.setLibero(keepJerseyNumber && Boolean.TRUE.equals(member.getLibero()));
                cleanMember.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
                members.add(cleanMember);
            }
        }
        return members;
    }

    private void validateVolleyballTeamMembers(String teamName, List<CreateTournamentReq.TeamMemberEntry> members) {
        if (members.size() < 6) {
            throw new IllegalArgumentException(teamName + " \u81f3\u5c11\u9700\u89816\u540d\u961f\u5458");
        }
        // 暂时取消主办方队伍报名人数上限；原规则：members.size() > 12 时拒绝创建。
        // if (members.size() > 12) {
        //     throw new IllegalArgumentException(teamName + " \u9700\u89816\u523012\u540d\u961f\u5458");
        // }

        int captainCount = 0;
        Set<Integer> jerseyNumbers = new HashSet<>();
        for (CreateTournamentReq.TeamMemberEntry member : members) {
            Integer jerseyNumber = member.getJerseyNumber();
            if (jerseyNumber == null || jerseyNumber <= 0) {
                throw new IllegalArgumentException(teamName + " \u5b58\u5728\u65e0\u6548\u7403\u8863\u53f7\u7801");
            }
            if (!jerseyNumbers.add(jerseyNumber)) {
                throw new IllegalArgumentException(teamName + " \u5b58\u5728\u91cd\u590d\u7403\u8863\u53f7\u7801");
            }
            if (Boolean.TRUE.equals(member.getCaptain())) {
                captainCount++;
            }
        }
        if (captainCount != 1) {
            throw new IllegalArgumentException(teamName + " \u5fc5\u987b\u6307\u5b9a1\u540d\u961f\u957f");
        }
    }

    private void validateBadmintonTeamMembers(String teamName, List<CreateTournamentReq.TeamMemberEntry> members) {
        if (members.size() < 2) {
            throw new IllegalArgumentException(teamName + " \u81f3\u5c11\u9700\u89812\u540d\u6210\u5458");
        }
        // 暂时取消主办方队伍报名人数上限；原规则：members.size() > 12 时拒绝创建。
        // if (members.size() > 12) {
        //     throw new IllegalArgumentException(teamName + " \u6700\u591a\u53ea\u80fd\u62a5\u540d12\u540d\u6210\u5458");
        // }
        long captainCount = members.stream().filter(member -> Boolean.TRUE.equals(member.getCaptain())).count();
        if (captainCount != 1) {
            throw new IllegalArgumentException(teamName + " \u5fc5\u987b\u6307\u5b9a1\u540d\u961f\u957f");
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
    private String hashPassword(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword + REFEREE_HASH_SALT);
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

    private void applyVolleyballRule(Tournament tournament, CreateTournamentReq.RuleConfig rule) {
        int bestOf = rule == null || rule.getBestOf() == null ? DEFAULT_BEST_OF : rule.getBestOf();
        int gamesToWin = rule == null || rule.getGamesToWin() == null ? DEFAULT_GAMES_TO_WIN : rule.getGamesToWin();
        int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_VOLLEYBALL_POINTS_TO_WIN : rule.getPointsToWin();
        int decidingPointsToWin = rule == null || rule.getDecidingPointsToWin() == null ? DEFAULT_VOLLEYBALL_DECIDING_POINTS_TO_WIN : rule.getDecidingPointsToWin();
        boolean enableDeuce = rule == null || rule.getEnableDeuce() == null ? DEFAULT_ENABLE_DEUCE : rule.getEnableDeuce();
        int capPoint = rule == null || rule.getCapPoint() == null ? DEFAULT_VOLLEYBALL_CAP_POINT : rule.getCapPoint();
        if (bestOf != 3 && bestOf != 5) {
            throw new IllegalArgumentException("排球只支持三局两胜或五局三胜");
        }
        if (gamesToWin != bestOf / 2 + 1) {
            throw new IllegalArgumentException("gamesToWin does not match bestOf");
        }
        validatePointRule(pointsToWin, enableDeuce, capPoint);
        if (decidingPointsToWin < 1 || decidingPointsToWin > pointsToWin) {
            throw new IllegalArgumentException("decidingPointsToWin must be between 1 and pointsToWin");
        }

        tournament.setBestOf(bestOf);
        tournament.setGamesToWin(gamesToWin);
        tournament.setPointsToWin(pointsToWin);
        tournament.setDecidingPointsToWin(decidingPointsToWin);
        tournament.setEnableDeuce(enableDeuce);
        tournament.setCapPoint(capPoint);
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
        validatePointRule(pointsToWin, enableDeuce, capPoint);

        tournament.setBestOf(bestOf);
        tournament.setGamesToWin(gamesToWin);
        tournament.setPointsToWin(pointsToWin);
        tournament.setDecidingPointsToWin(null);
        tournament.setEnableDeuce(enableDeuce);
        tournament.setCapPoint(capPoint);
    }

    private void validatePointRule(int pointsToWin, boolean enableDeuce, int capPoint) {
        validatePointsToWin(pointsToWin);
        if (enableDeuce && (capPoint <= pointsToWin || capPoint > 99)) {
            throw new IllegalArgumentException("capPoint must be greater than pointsToWin and no more than 99");
        }
    }

    private void validatePointsToWin(int pointsToWin) {
        if (pointsToWin < 1 || pointsToWin > 99) {
            throw new IllegalArgumentException("pointsToWin must be between 1 and 99");
        }
    }

    private void applyTournamentType(Tournament tournament, CreateTournamentReq req, int playerCount) {
        int tournamentType = req.getTournamentType() == null ? TYPE_KNOCKOUT : req.getTournamentType();
        if (tournamentType != TYPE_KNOCKOUT && tournamentType != TYPE_GROUP && tournamentType != TYPE_ROUND_ROBIN) {
            throw new IllegalArgumentException("tournamentType must be 0, 1 or 2");
        }

        tournament.setTournamentType(tournamentType);
        if (tournamentType == TYPE_KNOCKOUT) {
            int knockoutRounds = resolveKnockoutRounds(req, playerCount);
            tournament.setGroupSize(null);
            tournament.setKnockoutSlots(null);
            tournament.setKnockoutRounds(knockoutRounds);
            tournament.setQualifiersPerGroup(null);
            tournament.setRoundRobinRounds(null);
            tournament.setCurrentStage(STAGE_KNOCKOUT);
            tournament.setKnockoutGenerated(true);
            return;
        }

        if (tournamentType == TYPE_ROUND_ROBIN) {
            int rounds = req.getRoundRobinRounds() == null ? 1 : req.getRoundRobinRounds();
            if (rounds != 1 && rounds != 2) {
                throw new IllegalArgumentException("roundRobinRounds must be 1 or 2");
            }
            tournament.setGroupSize(null);
            tournament.setKnockoutSlots(null);
            tournament.setKnockoutRounds(null);
            tournament.setQualifiersPerGroup(null);
            tournament.setRoundRobinRounds(rounds);
            tournament.setCurrentStage(STAGE_GROUP);
            tournament.setKnockoutGenerated(false);
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
        if (groupCount < 1 || minGroupSize < qualifiers) {
            throw new IllegalArgumentException("each group must have at least as many players as qualifiers");
        }

        tournament.setGroupSize((int) Math.ceil(playerCount * 1.0 / groupCount));
        tournament.setKnockoutSlots(knockoutSlots);
        tournament.setKnockoutRounds(Integer.numberOfTrailingZeros(knockoutSlots));
        tournament.setQualifiersPerGroup(qualifiers);
        tournament.setRoundRobinRounds(null);
        tournament.setCurrentStage(STAGE_GROUP);
        tournament.setKnockoutGenerated(false);
    }

    private int resolveKnockoutRounds(CreateTournamentReq req, int playerCount) {
        Integer requestedRounds = req.getKnockoutRounds();
        if (requestedRounds == null) {
            return Integer.numberOfTrailingZeros(calcPowerOfTwoCapacity(playerCount));
        }
        int rounds = requestedRounds;
        if (rounds < 1 || rounds > MAX_KNOCKOUT_ROUNDS) {
            throw new IllegalArgumentException("knockoutRounds must be between 1 and " + MAX_KNOCKOUT_ROUNDS);
        }
        int minExclusive = rounds == 1 ? 1 : (1 << (rounds - 1));
        int maxInclusive = 1 << rounds;
        if (playerCount <= minExclusive || playerCount > maxInclusive) {
            throw new IllegalArgumentException("player count does not match knockoutRounds");
        }
        return rounds;
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

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean hasTeamMembers(Tournament tournament) {
        if (tournament == null) {
            return false;
        }
        return tournamentTeamMemberMapper.selectCount(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("tournament_id", tournament.getId())
        ) > 0;
    }

    private boolean hasTeamMatchItems(Tournament tournament) {
        if (tournament == null || StrUtil.isBlank(tournament.getId())
                || !Integer.valueOf(SPORT_BADMINTON).equals(safeSportType(tournament))) {
            return false;
        }
        return teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>()
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

    private void validateRefereePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("裁判密码不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("裁判密码必须为8位数字");
        }
    }
}
