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
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentMatchAccessVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TeamMatchItemVO;
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
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
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
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
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
                                 TournamentRoundRuleMapper tournamentRoundRuleMapper,
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
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.userMapper = userMapper;
        this.bracketEngine = bracketEngine;
        this.roundRobinEngine = roundRobinEngine;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String createTournament(String creatorUserId, CreateTournamentReq req) {
        if (StrUtil.isBlank(creatorUserId)) {
            throw new IllegalArgumentException("\u8bf7\u5148\u767b\u5f55");
        }
        requireCompletedProfile(creatorUserId);
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
        return bracketEngine.generateKnockoutBracket(tournament.getId(), participants);
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
        if (members.size() < 6 || members.size() > 12) {
            throw new IllegalArgumentException(teamName + " \u9700\u89816\u523012\u540d\u961f\u5458");
        }

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
        if (members.size() > 12) {
            throw new IllegalArgumentException(teamName + " \u6700\u591a\u53ea\u80fd\u62a5\u540d12\u540d\u6210\u5458");
        }
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
                ? loadAllTournamentMatches(tournamentId)
                : loadGroupMatches(tournamentId);

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
        Tournament tournament = requireTournament(tournamentId);
        requireArchivedReadable(tournament, currentUserId);
        List<Player> players = loadPlayers(tournamentId);
        List<MatchRecord> matches = TYPE_ROUND_ROBIN == tournament.getTournamentType()
                ? loadAllTournamentMatches(tournamentId)
                : loadGroupMatches(tournamentId);
        return buildStandingsVO(tournament, players, matches);
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
    @Transactional(rollbackFor = Exception.class)
    public void generateKnockout(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectByIdForUpdate(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以生成淘汰赛");
        }
        requireNotArchived(tournament);
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
            throw new IllegalArgumentException("group ranking has unresolved tie");
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
        vo.setRoundRuleEnabled(Boolean.TRUE.equals(tournament.getRoundRuleEnabled()));
        vo.setRoundRules(loadRoundRules(tournament.getId()));
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
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        return tournament;
    }

    private boolean isArchived(Tournament tournament) {
        return tournament != null && Boolean.TRUE.equals(tournament.getArchived());
    }

    private void requireArchivedReadable(Tournament tournament, String currentUserId) {
        if (!isArchived(tournament)) {
            return;
        }
        if (StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId())) {
            return;
        }
        throw new IllegalArgumentException("archived tournament is only visible to creator");
    }

    private void requireNotArchived(Tournament tournament) {
        if (isArchived(tournament)) {
            throw new IllegalStateException("archived tournament is read-only");
        }
    }

    private void requireCreator(String userId, Tournament tournament) {
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("only creator can operate this tournament");
        }
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
        return tournament.getParticipantType() == null ? PARTICIPANT_INDIVIDUAL : tournament.getParticipantType();
    }

    private Integer safeTeamMatchTemplate(Tournament tournament) {
        return tournament.getTeamMatchTemplate() == null ? TEAM_MATCH_TEMPLATE_NONE : tournament.getTeamMatchTemplate();
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

    private List<MatchRecord> loadAllTournamentMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .ne("stage_type", STAGE_TEAM_CHILD)
                        .orderByAsc("round_num", "match_index")
        );
    }

    private GroupStandingsVO buildStandingsVO(Tournament tournament, List<Player> players, List<MatchRecord> matches) {
        Map<String, Player> playerMap = players.stream()
                .filter(player -> player.getId() != null)
                .collect(Collectors.toMap(Player::getId, player -> player));
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

        boolean allFinished = matches.stream()
                .allMatch(match -> Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus()));
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

        Map<String, Integer> h2hBalance = new HashMap<>();
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
            addHeadToHeadBalance(h2hBalance, match.getLeftPlayerId(), match.getRightPlayerId(), match.getWinnerId());
        }

        Map<String, String> h2hWinner = resolveHeadToHeadWinners(h2hBalance);
        List<Standing> standings = new ArrayList<>(standingMap.values());
        standings.sort((a, b) -> compareStanding(a, b, h2hWinner));
        markRanksAndTies(standings, matches, qualifiersPerGroup == null ? 0 : qualifiersPerGroup, h2hWinner);
        markDisplayRanks(standings, matches, h2hWinner);
        return standings;
    }


    private void addHeadToHeadBalance(Map<String, Integer> h2hBalance, String leftId, String rightId, String winnerId) {
        String key = pairKey(leftId, rightId);
        if (StrUtil.isBlank(key) || StrUtil.isBlank(winnerId)) {
            return;
        }
        String firstId = key.split(":", 2)[0];
        int delta = StrUtil.equals(winnerId, firstId) ? 1 : -1;
        h2hBalance.merge(key, delta, Integer::sum);
    }

    private Map<String, String> resolveHeadToHeadWinners(Map<String, Integer> h2hBalance) {
        Map<String, String> winners = new HashMap<>();
        for (Map.Entry<String, Integer> entry : h2hBalance.entrySet()) {
            int balance = entry.getValue() == null ? 0 : entry.getValue();
            if (balance == 0) {
                continue;
            }
            String[] ids = entry.getKey().split(":", 2);
            winners.put(entry.getKey(), balance > 0 ? ids[0] : ids[1]);
        }
        return winners;
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

    private void markRanksAndTies(List<Standing> standings,
                                  List<MatchRecord> matches,
                                  int qualifiersPerGroup,
                                  Map<String, String> h2hWinner) {
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            standing.rank = i + 1;
            standing.qualified = false;
            standing.tieUnresolved = false;
        }

        if (qualifiersPerGroup <= 0 || finishedMatchCount(matches) == 0) {
            return;
        }

        for (int i = 0; i < standings.size();) {
            Standing current = standings.get(i);
            int j = i + 1;
            while (j < standings.size() && sameDisplayStats(current, standings.get(j))) {
                j++;
            }

            List<Standing> block = standings.subList(i, j);
            boolean unresolvedTieBlock = block.size() > 1 && !canResolveDisplayTie(block, h2hWinner);
            int startRank = i + 1;
            int endRank = j;
            boolean crossesQualificationLine = startRank <= qualifiersPerGroup && endRank > qualifiersPerGroup;

            if (!unresolvedTieBlock) {
                for (int k = i; k < j; k++) {
                    standings.get(k).qualified = k < qualifiersPerGroup;
                }
            } else if (crossesQualificationLine) {
                block.forEach(standing -> standing.tieUnresolved = true);
            } else if (endRank <= qualifiersPerGroup) {
                block.forEach(standing -> standing.qualified = true);
            }

            i = j;
        }
    }

    private boolean hasHeadToHeadWinner(Standing left, Standing right, Map<String, String> h2hWinner) {
        String winner = h2hWinner.get(pairKey(left.playerId, right.playerId));
        return StrUtil.equals(winner, left.playerId) || StrUtil.equals(winner, right.playerId);
    }

    private void markDisplayRanks(List<Standing> standings, List<MatchRecord> matches, Map<String, String> h2hWinner) {
        if (finishedMatchCount(matches) == 0) {
            standings.forEach(standing -> standing.displayRankText = "-");
            return;
        }

        for (int i = 0; i < standings.size();) {
            List<Standing> tied = new ArrayList<>();
            Standing current = standings.get(i);
            tied.add(current);
            int j = i + 1;
            while (j < standings.size()) {
                Standing next = standings.get(j);
                if (!sameDisplayStats(current, next)) {
                    break;
                }
                tied.add(next);
                j++;
            }

            boolean displayTie = tied.size() > 1 && !canResolveDisplayTie(tied, h2hWinner);
            String displayRankText = String.valueOf(i + 1);
            if (displayTie) {
                tied.forEach(standing -> standing.displayRankText = displayRankText);
            } else {
                for (int k = 0; k < tied.size(); k++) {
                    tied.get(k).displayRankText = String.valueOf(i + 1 + k);
                }
            }
            i = j;
        }
    }

    private boolean sameDisplayStats(Standing left, Standing right) {
        return left.matchWins == right.matchWins
                && left.netGames() == right.netGames()
                && left.netPoints() == right.netPoints();
    }

    private long finishedMatchCount(List<MatchRecord> matches) {
        return matches.stream()
                .filter(match -> Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus()))
                .count();
    }

    private boolean canResolveDisplayTie(List<Standing> tied, Map<String, String> h2hWinner) {
        if (tied.size() != 2) {
            return false;
        }
        return hasHeadToHeadWinner(tied.get(0), tied.get(1), h2hWinner);
    }

    private GroupStandingsVO.StandingVO toStandingVO(Standing standing, boolean roundRobin) {
        GroupStandingsVO.StandingVO vo = new GroupStandingsVO.StandingVO();
        vo.setPlayerId(standing.playerId);
        vo.setPlayerName(standing.playerName);
        vo.setSeedRank(standing.seedRank);
        vo.setRank(standing.rank);
        vo.setDisplayRankText(standing.displayRankText);
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
        if (groupCount < 1 || minGroupSize <= qualifiers) {
            throw new IllegalArgumentException("each group must have more players than qualifiers");
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
        Tournament tournament = requireTournament(tournamentId);
        requireNotArchived(tournament);
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
        private String displayRankText;
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
