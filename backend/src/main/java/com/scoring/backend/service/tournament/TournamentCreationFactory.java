package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.IdUtil;
import cn.hutool.crypto.digest.DigestUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentRankingConfig;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.engine.BracketEngine;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.engine.ranking.RankingConfig;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.tournament.TournamentAccessGuard;
import com.scoring.backend.service.tournament.TournamentRankingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 赛事创建工厂：三种运动形态（羽毛球个人/羽毛球团体/排球）的创建流程，
 * 含规则解析、轮次模板、参赛名单归一化校验、比赛生成与持久化。
 * 写路径服务；赛制相关常量在本类与门面各有一份（门面读侧仍需）。
 *
 * 组别层级（V23 起）：每个赛事至少一个 tournament_division；旧扁平 payload 归一化为
 * 单个匿名默认组别后走统一路径，多组别仅支持羽毛球个人赛。tournament 表的下沉列
 * 以第 1 个组别的值占位写入（列仍 NOT NULL），保证回滚兼容。
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
    private static final int MAX_DIVISIONS = 16;
    private static final int MAX_DIVISION_NAME_LENGTH = 64;
    private static final String DEFAULT_DIVISION_NAME = "默认组别";
    private static final String REFEREE_PASSWORD_PATTERN = "^\\d{8}$";
    private static final String REFEREE_HASH_SALT = "tournament_referee_password";

    private final TournamentMapper tournamentMapper;
    private final TournamentDivisionMapper tournamentDivisionMapper;
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
            TournamentDivisionMapper tournamentDivisionMapper,
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
        this.tournamentDivisionMapper = tournamentDivisionMapper;
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
        List<CreateTournamentReq.DivisionSpec> divisions = normalizeDivisions(req, sportType, participantType);
        if (sportType == SPORT_VOLLEYBALL) {
            return createVolleyballTournament(creatorUserId, req, divisions);
        }
        if (participantType == PARTICIPANT_TEAM) {
            return createBadmintonTeamTournament(creatorUserId, req, divisions);
        }
        return createBadmintonIndividualTournament(creatorUserId, req, divisions);
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

    /**
     * 组别归一化：divisions 为空 = 旧扁平 payload，打包成单个匿名默认组别（行为与历史版本一致）；
     * 显式传入时校验：数量 ≤ {@value MAX_DIVISIONS}、多组别仅限羽毛球个人赛、
     * 组别名非空（去空白）且赛事内不重名（DB 唯一键兜底）。
     */
    private List<CreateTournamentReq.DivisionSpec> normalizeDivisions(CreateTournamentReq req, int sportType, int participantType) {
        if (CollUtil.isEmpty(req.getDivisions())) {
            return List.of(legacyDivision(req));
        }
        List<CreateTournamentReq.DivisionSpec> raw = req.getDivisions();
        boolean hasTopLevelPayload = CollUtil.isNotEmpty(req.getPlayers())
                || CollUtil.isNotEmpty(req.getTeams())
                || req.getRule() != null
                || CollUtil.isNotEmpty(req.getRoundRules())
                || req.getRankingTemplate() != null
                || CollUtil.isNotEmpty(req.getRankingPriorities())
                || req.getTournamentType() != null;
        if (hasTopLevelPayload) {
            throw new IllegalArgumentException("divisions 与顶层选手/赛制/规则字段不可混用，请只在 divisions 内配置各组别");
        }
        if (raw.size() > MAX_DIVISIONS) {
            throw new IllegalArgumentException("组别数量最多为" + MAX_DIVISIONS);
        }
        if (raw.size() > 1 && (sportType != SPORT_BADMINTON || participantType != PARTICIPANT_INDIVIDUAL)) {
            throw new IllegalArgumentException("多组别仅支持羽毛球个人赛");
        }
        List<CreateTournamentReq.DivisionSpec> divisions = new ArrayList<>(raw.size());
        Set<String> names = new HashSet<>();
        for (CreateTournamentReq.DivisionSpec spec : raw) {
            if (spec == null || StrUtil.isBlank(spec.getName())) {
                throw new IllegalArgumentException("组别名称不能为空");
            }
            String name = spec.getName().trim();
            if (name.length() > MAX_DIVISION_NAME_LENGTH) {
                throw new IllegalArgumentException("组别名称长度不能超过" + MAX_DIVISION_NAME_LENGTH);
            }
            if (!names.add(name)) {
                throw new IllegalArgumentException("组别名称重复: " + name);
            }
            spec.setName(name);
            divisions.add(spec);
        }
        return divisions;
    }

    /** 旧扁平 payload → 单个匿名默认组别（老小程序 / web 创建页零改动）。 */
    private CreateTournamentReq.DivisionSpec legacyDivision(CreateTournamentReq req) {
        CreateTournamentReq.DivisionSpec spec = new CreateTournamentReq.DivisionSpec();
        spec.setName(DEFAULT_DIVISION_NAME);
        spec.setPlayers(req.getPlayers());
        spec.setTournamentType(req.getTournamentType());
        spec.setKnockoutSlots(req.getKnockoutSlots());
        spec.setKnockoutRounds(req.getKnockoutRounds());
        spec.setQualifiersPerGroup(req.getQualifiersPerGroup());
        spec.setRoundRobinRounds(req.getRoundRobinRounds());
        spec.setRule(req.getRule());
        spec.setRoundRuleEnabled(req.getRoundRuleEnabled());
        spec.setRoundRules(req.getRoundRules());
        spec.setThirdPlaceEnabled(req.getThirdPlaceEnabled());
        spec.setThirdPlaceRule(req.getThirdPlaceRule());
        spec.setRankingTemplate(req.getRankingTemplate());
        spec.setRankingPriorities(req.getRankingPriorities());
        return spec;
    }

    private String createBadmintonIndividualTournament(String creatorUserId, CreateTournamentReq req,
                                                       List<CreateTournamentReq.DivisionSpec> divisions) {
        if (CollUtil.isNotEmpty(req.getTeams())) {
            throw new IllegalArgumentException("\u4e2a\u4eba\u8d5b\u4e0d\u652f\u6301\u961f\u4f0d\u5217\u8868");
        }
        ensureNoTeamMatchTemplate(req);

        Tournament tournament = new Tournament();
        tournament.setName(req.getName().trim());
        tournament.setLocation(StrUtil.blankToDefault(StrUtil.trim(req.getLocation()), null));
        tournament.setStatus(0);
        tournament.setSportType(SPORT_BADMINTON);
        tournament.setParticipantType(PARTICIPANT_INDIVIDUAL);
        tournament.setTeamMatchTemplate(TEAM_MATCH_TEMPLATE_NONE);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);

        List<DivisionPlan> plans = new ArrayList<>(divisions.size());
        for (int i = 0; i < divisions.size(); i++) {
            CreateTournamentReq.DivisionSpec spec = divisions.get(i);
            List<CreateTournamentReq.PlayerEntry> entries = normalizePlayers(spec.getPlayers());
            TournamentDivision division = buildIndividualDivision(spec, i, entries.size());
            plans.add(new DivisionPlan(spec, division, entries, entries.size()));
        }
        mirrorSunkColumns(tournament, plans.get(0).division());

        return persistTournament(tournament, plans, List.of(), req);
    }

    private TournamentDivision buildIndividualDivision(CreateTournamentReq.DivisionSpec spec, int sortOrder, int participantCount) {
        TournamentDivision division = new TournamentDivision();
        division.setName(spec.getName());
        division.setSortOrder(sortOrder);
        division.setStatus(0);
        division.setParticipantType(PARTICIPANT_INDIVIDUAL);
        applyRule(division, spec.getRule());
        applyTournamentType(division, spec, participantCount);
        applyRoundRuleFlag(division, spec, TEAM_MATCH_TEMPLATE_NONE);
        applyThirdPlaceRule(division, spec, participantCount, SPORT_BADMINTON, TEAM_MATCH_TEMPLATE_NONE);
        return division;
    }

    private String createBadmintonTeamTournament(String creatorUserId, CreateTournamentReq req,
                                                 List<CreateTournamentReq.DivisionSpec> divisions) {
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

        CreateTournamentReq.DivisionSpec spec = divisions.get(0);
        TournamentDivision division = buildTeamDivision(spec, teams, teamMatchTemplate);
        mirrorSunkColumns(tournament, division);

        return persistTournament(tournament, List.of(new DivisionPlan(spec, division, null, teams.size())), teams, req);
    }

    /** 团体赛固定单组别（sortOrder=0），规则 / 赛制 / 轮次规则全部来自该组别。 */
    private TournamentDivision buildTeamDivision(CreateTournamentReq.DivisionSpec spec,
                                                 List<CreateTournamentReq.TeamEntry> teams,
                                                 int teamMatchTemplate) {
        TournamentDivision division = new TournamentDivision();
        division.setName(spec.getName());
        division.setSortOrder(0);
        division.setStatus(0);
        division.setParticipantType(PARTICIPANT_INDIVIDUAL);
        if (teamMatchTemplate == TEAM_MATCH_TEMPLATE_RELAY) {
            applyRelayRule(division, spec.getRule());
            validateRelayTeamCapacity(teams, division.getCapPoint());
        } else {
            applyRule(division, spec.getRule());
        }
        applyTournamentType(division, spec, teams.size());
        applyRoundRuleFlag(division, spec, teamMatchTemplate);
        applyThirdPlaceRule(division, spec, teams.size(), SPORT_BADMINTON, teamMatchTemplate);
        return division;
    }

    private int resolveBadmintonTeamMatchTemplate(CreateTournamentReq req) {
        int template = req.getTeamMatchTemplate() == null ? TEAM_MATCH_TEMPLATE_SUDIRMAN_5 : req.getTeamMatchTemplate();
        if (template != TEAM_MATCH_TEMPLATE_SUDIRMAN_5 && template != TEAM_MATCH_TEMPLATE_RELAY) {
            throw new IllegalArgumentException("\u672a\u77e5\u7684\u7fbd\u6bdb\u7403\u56e2\u4f53\u6a21\u677f");
        }
        return template;
    }

    private void applyRelayRule(TournamentDivision division, CreateTournamentReq.RuleConfig rule) {
        int pointsToWin = rule == null || rule.getPointsToWin() == null ? DEFAULT_POINTS_TO_WIN : rule.getPointsToWin();
        validatePointsToWin(pointsToWin);
        division.setBestOf(1);
        division.setGamesToWin(1);
        division.setPointsToWin(pointsToWin);
        division.setEnableDeuce(false);
        division.setDecidingPointsToWin(null);
        int relayMemberCount = rule == null || rule.getCapPoint() == null ? DEFAULT_RELAY_MEMBER_COUNT : rule.getCapPoint();
        division.setCapPoint(Math.max(3, Math.min(12, relayMemberCount)));
    }

    private void applyRoundRuleFlag(TournamentDivision division, CreateTournamentReq.DivisionSpec spec, Integer teamMatchTemplate) {
        boolean enabled = Boolean.TRUE.equals(spec.getRoundRuleEnabled());
        if (enabled && TYPE_ROUND_ROBIN == division.getTournamentType()) {
            throw new IllegalArgumentException("循环赛暂不支持分轮规则");
        }
        if (enabled && Integer.valueOf(TEAM_MATCH_TEMPLATE_RELAY).equals(teamMatchTemplate)) {
            throw new IllegalArgumentException("接力追分赛暂不支持分轮规则");
        }
        division.setRoundRuleEnabled(enabled);
    }

    private void applyThirdPlaceRule(TournamentDivision division, CreateTournamentReq.DivisionSpec spec,
                                     int participantCount, int sportType, Integer teamMatchTemplate) {
        boolean enabled = Boolean.TRUE.equals(spec.getThirdPlaceEnabled());
        if (enabled && TYPE_ROUND_ROBIN == division.getTournamentType()) {
            throw new IllegalArgumentException("循环赛不支持季军赛");
        }
        if (enabled) {
            int knockoutSize = TYPE_GROUP == division.getTournamentType()
                    ? safeInt(division.getKnockoutSlots())
                    : participantCount;
            if (knockoutSize < 4) {
                throw new IllegalArgumentException("季军赛至少需要4个淘汰阶段参赛单位");
            }
        }

        division.setThirdPlaceEnabled(enabled);
        RuleValues values = resolveThirdPlaceRuleValues(division, spec, enabled, sportType, teamMatchTemplate);
        division.setThirdPlaceBestOf(values.bestOf());
        division.setThirdPlaceGamesToWin(values.gamesToWin());
        division.setThirdPlacePointsToWin(values.pointsToWin());
        division.setThirdPlaceDecidingPointsToWin(values.decidingPointsToWin());
        division.setThirdPlaceEnableDeuce(values.enableDeuce());
        division.setThirdPlaceCapPoint(values.capPoint());
    }

    private RuleValues resolveThirdPlaceRuleValues(TournamentDivision division, CreateTournamentReq.DivisionSpec spec,
                                                   boolean enabled, int sportType, Integer teamMatchTemplate) {
        CreateTournamentReq.RuleConfig rule = enabled && spec.getThirdPlaceRule() != null ? spec.getThirdPlaceRule() : spec.getRule();
        if (Integer.valueOf(TEAM_MATCH_TEMPLATE_RELAY).equals(teamMatchTemplate)) {
            int pointsToWin = rule == null || rule.getPointsToWin() == null ? division.getPointsToWin() : rule.getPointsToWin();
            validatePointsToWin(pointsToWin);
            return new RuleValues(1, 1, pointsToWin, null, false, division.getCapPoint());
        }
        return resolveRuleValues(sportType, rule);
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

    private List<MatchRecord> generateMatchesForType(Tournament tournament, TournamentDivision division, List<Player> participants) {
        if (TYPE_ROUND_ROBIN == division.getTournamentType()) {
            int rounds = division.getRoundRobinRounds() == null ? 1 : division.getRoundRobinRounds();
            return roundRobinEngine.generateLeagueMatches(tournament.getId(), division.getId(), participants, rounds);
        }
        if (TYPE_GROUP == division.getTournamentType()) {
            return roundRobinEngine.generateGroupMatches(tournament.getId(), division.getId(), participants);
        }
        return appendThirdPlaceMatch(division, bracketEngine.generateKnockoutBracket(tournament.getId(), division.getId(), participants));
    }

    public List<MatchRecord> appendThirdPlaceMatch(Tournament tournament, List<MatchRecord> matches) {
        return appendThirdPlaceMatch(tournament.getId(), null,
                Boolean.TRUE.equals(tournament.getThirdPlaceEnabled()), tournament.getKnockoutRounds(), matches);
    }

    /** 组别版：季军赛挂在组别维度，match_record 写 tournament_id + division_id。 */
    public List<MatchRecord> appendThirdPlaceMatch(TournamentDivision division, List<MatchRecord> matches) {
        return appendThirdPlaceMatch(division.getTournamentId(), division.getId(),
                Boolean.TRUE.equals(division.getThirdPlaceEnabled()), division.getKnockoutRounds(), matches);
    }

    private List<MatchRecord> appendThirdPlaceMatch(String tournamentId, String divisionId, boolean thirdPlaceEnabled,
                                                    Integer knockoutRounds, List<MatchRecord> matches) {
        if (!thirdPlaceEnabled) {
            return matches;
        }
        int finalRound = knockoutRounds == null
                ? matches.stream().map(MatchRecord::getRoundNum).filter(java.util.Objects::nonNull).max(Integer::compareTo).orElse(0)
                : knockoutRounds;
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
        thirdPlace.setTournamentId(tournamentId);
        thirdPlace.setDivisionId(divisionId);
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

    private String createVolleyballTournament(String creatorUserId, CreateTournamentReq req,
                                              List<CreateTournamentReq.DivisionSpec> divisions) {
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

        CreateTournamentReq.DivisionSpec spec = divisions.get(0);
        TournamentDivision division = buildVolleyballDivision(spec, teams);
        mirrorSunkColumns(tournament, division);

        return persistTournament(tournament, List.of(new DivisionPlan(spec, division, null, teams.size())), teams, req);
    }

    /** 排球固定单组别（sortOrder=0）。 */
    private TournamentDivision buildVolleyballDivision(CreateTournamentReq.DivisionSpec spec,
                                                       List<CreateTournamentReq.TeamEntry> teams) {
        TournamentDivision division = new TournamentDivision();
        division.setName(spec.getName());
        division.setSortOrder(0);
        division.setStatus(0);
        division.setParticipantType(PARTICIPANT_INDIVIDUAL);
        applyVolleyballRule(division, spec.getRule());
        applyTournamentType(division, spec, teams.size());
        applyRoundRuleFlag(division, spec, TEAM_MATCH_TEMPLATE_NONE);
        applyThirdPlaceRule(division, spec, teams.size(), SPORT_VOLLEYBALL, TEAM_MATCH_TEMPLATE_NONE);
        return division;
    }

    private String persistTournament(Tournament tournament,
                                     List<DivisionPlan> plans,
                                     List<CreateTournamentReq.TeamEntry> teams,
                                     CreateTournamentReq req) {
        tournamentMapper.insert(tournament);
        saveRefereeConfigIfPresent(tournament.getId(), req.getRefereePassword());

        boolean hasGeneratedMatches = false;
        for (DivisionPlan plan : plans) {
            hasGeneratedMatches |= persistDivision(tournament, plan, teams);
        }

        if (hasGeneratedMatches) {
            Tournament update = new Tournament();
            update.setId(tournament.getId());
            update.setStatus(1);
            tournamentMapper.updateById(update);
        }

        return tournament.getId();
    }

    /**
     * 单个组别落库：tournament_division → player（含 division_id）→ 分组抽签 →
     * 分轮规则 / 排名配置（含 division_id）→ 赛程生成（match_record 含 division_id）。
     */
    private boolean persistDivision(Tournament tournament, DivisionPlan plan, List<CreateTournamentReq.TeamEntry> teams) {
        TournamentDivision division = plan.division();
        division.setTournamentId(tournament.getId());
        tournamentDivisionMapper.insert(division);

        saveRoundRulesIfNeeded(tournament, division, plan.spec(), plan.participantCount());
        saveInitialRankingConfigIfNeeded(tournament, division, plan.spec());

        List<Player> participants = plan.entries() != null
                ? buildPlayers(tournament.getId(), division.getId(), plan.entries())
                : buildTeamParticipants(tournament.getId(), division.getId(), teams);

        if (TYPE_GROUP == division.getTournamentType()) {
            int groupCount = division.getKnockoutSlots() / division.getQualifiersPerGroup();
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

        List<MatchRecord> matches = generateMatchesForType(tournament, division, participants);
        for (MatchRecord matchRecord : matches) {
            matchRecord.setDivisionId(division.getId());
            matchRecordMapper.insert(matchRecord);
        }
        if (CollUtil.isNotEmpty(matches)) {
            // 与 tournament.status 语义对齐：已生成赛程即为"进行中"
            TournamentDivision divisionUpdate = new TournamentDivision();
            divisionUpdate.setId(division.getId());
            divisionUpdate.setStatus(1);
            tournamentDivisionMapper.updateById(divisionUpdate);
        }
        return CollUtil.isNotEmpty(matches);
    }

    /**
     * 单组别镜像写（回滚保险）：tournament 表下沉列自 V23 起不再被业务读取，
     * 但列仍 NOT NULL，创建时以第 1 个组别的值占位。
     */
    private void mirrorSunkColumns(Tournament tournament, TournamentDivision division) {
        tournament.setTournamentType(division.getTournamentType());
        tournament.setGroupSize(division.getGroupSize());
        tournament.setKnockoutSlots(division.getKnockoutSlots());
        tournament.setKnockoutRounds(division.getKnockoutRounds());
        tournament.setQualifiersPerGroup(division.getQualifiersPerGroup());
        tournament.setRoundRobinRounds(division.getRoundRobinRounds());
        tournament.setCurrentStage(division.getCurrentStage());
        tournament.setKnockoutGenerated(division.getKnockoutGenerated());
        tournament.setBestOf(division.getBestOf());
        tournament.setGamesToWin(division.getGamesToWin());
        tournament.setPointsToWin(division.getPointsToWin());
        tournament.setDecidingPointsToWin(division.getDecidingPointsToWin());
        tournament.setEnableDeuce(division.getEnableDeuce());
        tournament.setCapPoint(division.getCapPoint());
        tournament.setRoundRuleEnabled(division.getRoundRuleEnabled());
        tournament.setThirdPlaceEnabled(division.getThirdPlaceEnabled());
        tournament.setThirdPlaceBestOf(division.getThirdPlaceBestOf());
        tournament.setThirdPlaceGamesToWin(division.getThirdPlaceGamesToWin());
        tournament.setThirdPlacePointsToWin(division.getThirdPlacePointsToWin());
        tournament.setThirdPlaceDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin());
        tournament.setThirdPlaceEnableDeuce(division.getThirdPlaceEnableDeuce());
        tournament.setThirdPlaceCapPoint(division.getThirdPlaceCapPoint());
    }

    /** 创建期组别计划：入参 spec + 校验后的 division 实体 + 该组别报名名单（团体赛 entries=null）。 */
    private record DivisionPlan(CreateTournamentReq.DivisionSpec spec,
                                TournamentDivision division,
                                List<CreateTournamentReq.PlayerEntry> entries,
                                int participantCount) {
    }

    private void saveInitialRankingConfigIfNeeded(Tournament tournament, TournamentDivision division,
                                                  CreateTournamentReq.DivisionSpec spec) {
        if (division == null || division.getTournamentType() == null
                || (division.getTournamentType() != TYPE_GROUP
                && division.getTournamentType() != TYPE_ROUND_ROBIN)) {
            return;
        }
        CreateTournamentReq rankingReq = new CreateTournamentReq();
        rankingReq.setRankingTemplate(spec.getRankingTemplate());
        rankingReq.setRankingPriorities(spec.getRankingPriorities());
        RankingConfig rankingConfig = rankingService.parseCreateRankingConfig(tournament, rankingReq);
        TournamentRankingConfig entity = new TournamentRankingConfig();
        entity.setTournamentId(tournament.getId());
        entity.setDivisionId(division.getId());
        entity.setConfigVersion(1);
        entity.setConfigJson(rankingConfig.toJson());
        tournamentRankingConfigMapper.insert(entity);
    }

    private void saveRoundRulesIfNeeded(Tournament tournament, TournamentDivision division,
                                        CreateTournamentReq.DivisionSpec spec, int participantCount) {
        if (!Boolean.TRUE.equals(division.getRoundRuleEnabled())) {
            return;
        }
        List<RoundRuleScope> expectedScopes = expectedRoundRuleScopes(division, participantCount);
        Map<String, String> expectedLabels = expectedScopes.stream()
                .collect(Collectors.toMap(RoundRuleScope::key, RoundRuleScope::label));
        Map<String, CreateTournamentReq.RoundRuleConfig> submitted = new HashMap<>();
        for (CreateTournamentReq.RoundRuleConfig item : spec.getRoundRules() == null ? List.<CreateTournamentReq.RoundRuleConfig>of() : spec.getRoundRules()) {
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
            TournamentRoundRule rule = toRoundRule(tournament, division, scope.stageType(), scope.roundNum(), item.getRule());
            tournamentRoundRuleMapper.insert(rule);
        }
    }

    private List<RoundRuleScope> expectedRoundRuleScopes(TournamentDivision division, int participantCount) {
        List<RoundRuleScope> scopes = new ArrayList<>();
        if (TYPE_GROUP == division.getTournamentType()) {
            scopes.add(new RoundRuleScope(STAGE_GROUP, 0, "小组赛"));
            int roundCount = division.getKnockoutRounds() == null
                    ? Integer.numberOfTrailingZeros(division.getKnockoutSlots())
                    : division.getKnockoutRounds();
            int capacity = division.getKnockoutSlots() == null ? 1 << roundCount : division.getKnockoutSlots();
            addKnockoutScopes(scopes, capacity, roundCount);
            return scopes;
        }
        if (TYPE_KNOCKOUT == division.getTournamentType()) {
            if (participantCount < 2) {
                throw new IllegalArgumentException("至少2名参赛方才可启用分轮规则");
            }
            int roundCount = division.getKnockoutRounds() == null
                    ? Integer.numberOfTrailingZeros(calcPowerOfTwoCapacity(participantCount))
                    : division.getKnockoutRounds();
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

    private TournamentRoundRule toRoundRule(Tournament tournament, TournamentDivision division,
                                            int stageType, int roundNum, CreateTournamentReq.RuleConfig ruleConfig) {
        RuleValues values = resolveRuleValues(tournament.getSportType(), ruleConfig);
        TournamentRoundRule rule = new TournamentRoundRule();
        rule.setTournamentId(tournament.getId());
        rule.setDivisionId(division.getId());
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

    private List<Player> buildTeamParticipants(String tournamentId, String divisionId, List<CreateTournamentReq.TeamEntry> teams) {
        List<Player> participants = new ArrayList<>();
        for (CreateTournamentReq.TeamEntry team : teams) {
            Player participant = new Player();
            participant.setTournamentId(tournamentId);
            participant.setDivisionId(divisionId);
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

    private List<Player> buildPlayers(String tournamentId, String divisionId, List<CreateTournamentReq.PlayerEntry> entries) {
        List<Player> players = new ArrayList<>();
        for (CreateTournamentReq.PlayerEntry entry : entries) {
            Player player = new Player();
            player.setTournamentId(tournamentId);
            player.setDivisionId(divisionId);
            player.setName(entry.getName());
            player.setSeedRank(entry.getSeed());
            players.add(player);
        }
        return players;
    }

    private void applyVolleyballRule(TournamentDivision division, CreateTournamentReq.RuleConfig rule) {
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

        division.setBestOf(bestOf);
        division.setGamesToWin(gamesToWin);
        division.setPointsToWin(pointsToWin);
        division.setDecidingPointsToWin(decidingPointsToWin);
        division.setEnableDeuce(enableDeuce);
        division.setCapPoint(capPoint);
    }

    private void applyRule(TournamentDivision division, CreateTournamentReq.RuleConfig rule) {
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

        division.setBestOf(bestOf);
        division.setGamesToWin(gamesToWin);
        division.setPointsToWin(pointsToWin);
        division.setDecidingPointsToWin(null);
        division.setEnableDeuce(enableDeuce);
        division.setCapPoint(capPoint);
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

    private void applyTournamentType(TournamentDivision division, CreateTournamentReq.DivisionSpec spec, int playerCount) {
        int tournamentType = spec.getTournamentType() == null ? TYPE_KNOCKOUT : spec.getTournamentType();
        if (tournamentType != TYPE_KNOCKOUT && tournamentType != TYPE_GROUP && tournamentType != TYPE_ROUND_ROBIN) {
            throw new IllegalArgumentException("tournamentType must be 0, 1 or 2");
        }

        division.setTournamentType(tournamentType);
        if (tournamentType == TYPE_KNOCKOUT) {
            int knockoutRounds = resolveKnockoutRounds(spec, playerCount);
            division.setGroupSize(null);
            division.setKnockoutSlots(null);
            division.setKnockoutRounds(knockoutRounds);
            division.setQualifiersPerGroup(null);
            division.setRoundRobinRounds(null);
            division.setCurrentStage(STAGE_KNOCKOUT);
            division.setKnockoutGenerated(true);
            return;
        }

        if (tournamentType == TYPE_ROUND_ROBIN) {
            int rounds = spec.getRoundRobinRounds() == null ? 1 : spec.getRoundRobinRounds();
            if (rounds != 1 && rounds != 2) {
                throw new IllegalArgumentException("roundRobinRounds must be 1 or 2");
            }
            division.setGroupSize(null);
            division.setKnockoutSlots(null);
            division.setKnockoutRounds(null);
            division.setQualifiersPerGroup(null);
            division.setRoundRobinRounds(rounds);
            division.setCurrentStage(STAGE_GROUP);
            division.setKnockoutGenerated(false);
            return;
        }

        int knockoutSlots = spec.getKnockoutSlots() == null ? 8 : spec.getKnockoutSlots();
        int qualifiers = spec.getQualifiersPerGroup() == null ? 2 : spec.getQualifiersPerGroup();
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

        division.setGroupSize((int) Math.ceil(playerCount * 1.0 / groupCount));
        division.setKnockoutSlots(knockoutSlots);
        division.setKnockoutRounds(Integer.numberOfTrailingZeros(knockoutSlots));
        division.setQualifiersPerGroup(qualifiers);
        division.setRoundRobinRounds(null);
        division.setCurrentStage(STAGE_GROUP);
        division.setKnockoutGenerated(false);
    }

    private int resolveKnockoutRounds(CreateTournamentReq.DivisionSpec spec, int playerCount) {
        Integer requestedRounds = spec.getKnockoutRounds();
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

    /**
     * 分组抽签：刻意设置了种子(seedRank)的选手按种子序蛇形保位（种子间互不同组），
     * 未设置种子的选手随机打乱后参与蛇形分堆，不引入任何确定性排序（如按名字排序）。
     */
    private void assignGroups(List<Player> players, int groupCount) {
        List<Player> seeded = players.stream()
                .filter(p -> p.getSeedRank() != null)
                .sorted(Comparator.comparing(Player::getSeedRank))
                .collect(Collectors.toList());
        List<Player> unseeded = players.stream()
                .filter(p -> p.getSeedRank() == null)
                .collect(Collectors.toCollection(ArrayList::new));
        Collections.shuffle(unseeded);

        List<Player> ordered = new ArrayList<>(seeded.size() + unseeded.size());
        ordered.addAll(seeded);
        ordered.addAll(unseeded);

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
