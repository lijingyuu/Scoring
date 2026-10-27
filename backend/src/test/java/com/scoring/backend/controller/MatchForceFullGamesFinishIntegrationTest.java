package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.scoring.backend.controller.MatchLockTestSupport.withMatchLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * V32 打满三局（force_full_games）结算校验：
 * 开关开启（仅 BO3）——终局 3:0 / 2:1 接受；2:0（未打满 bestOf）拒绝；退赛分支维持原契约；
 * 开关关闭——2:0 即终场、3:0 沿用原校验拒绝，行为零变化。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:match_force_full_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchForceFullGamesFinishIntegrationTest {

    private static final String TOURNAMENT_ID = "t-force-full-1";
    private static final String MATCH_ID = "m-force-full-1";
    private static final String LEFT_ID = "p-left";
    private static final String RIGHT_ID = "p-right";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TournamentMapper tournamentMapper;
    @Autowired
    private TournamentDivisionMapper tournamentDivisionMapper;
    @Autowired
    private MatchRecordMapper matchRecordMapper;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MatchEventMapper matchEventMapper;
    @Autowired
    private MatchLineupConfigMapper matchLineupConfigMapper;
    @Autowired
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-creator");
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        matchEventMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        tournamentRoundRuleMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-creator"));
    }

    @Test
    void forceFullGames_on_shouldAccept21FullThreeGames() throws Exception {
        prepareMatch(true);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 2, 1, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 20, 25, "right"),
                                        buildGameScore(3, 15, 10, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, source.getStatus());
        assertEquals(LEFT_ID, source.getWinnerId());
        assertEquals(2, source.getLeftGameWins());
        assertEquals(1, source.getRightGameWins());
    }

    @Test
    void forceFullGames_on_shouldAccept30Sweep() throws Exception {
        prepareMatch(true);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 25, 18, "left"),
                                        buildGameScore(3, 25, 12, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(LEFT_ID, source.getWinnerId());
        assertEquals(3, source.getLeftGameWins());
    }

    @Test
    void forceFullGames_on_shouldReject20NotFillingBestOf() throws Exception {
        prepareMatch(true);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 2, 0, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 25, 18, "left")
                                )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void forceFullGames_on_shouldKeepRetiredContract() throws Exception {
        prepareMatch(true);

        // 退赛：胜方局数补满 gamesToWin=2，gameScores 只保留实际已打完的局
        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(retiredPayload(
                                "left", 2, 0, "right", List.of(
                                        buildGameScore(1, 25, 20, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        assertEquals("right", source.getRetiredSide());
        assertEquals(LEFT_ID, source.getWinnerId());
    }

    @Test
    void forceFullGames_off_shouldKeepLegacyContract() throws Exception {
        prepareMatch(false);

        // 开关关闭：2:0 即终场，正常接受
        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 2, 0, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 25, 18, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void forceFullGames_off_shouldReject30ByLegacyValidation() throws Exception {
        prepareMatch(false);

        // 开关关闭：3:0 超出 gamesToWin=2，沿用原校验拒绝
        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 25, 18, "left"),
                                        buildGameScore(3, 25, 12, "left")
                                )))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /**
     * 场景 2（终场后重开 → 二次结算）：flag ON 下 2:1 完赛 → 重开 →
     * 重开痕迹清理（结果字段归 NULL、事件/阵容配置删除、开关不受影响）→
     * 再结算 3:0 与 2:1 均可；开关关闭对照：重开后 3:0 仍按 legacy 校验拒绝。
     */
    @Test
    void forceFullGames_on_restartAfterFinish_shouldClearArtifactsAndResettle30And21() throws Exception {
        prepareKnockoutMatch(true);

        finishMatch("left", 2, 1, List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 20, 25, "right"),
                buildGameScore(3, 15, 10, "left")));
        MatchRecord finished = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, finished.getStatus());
        assertEquals(LEFT_ID, finished.getWinnerId());

        // 预埋执裁痕迹：事件 + 阵容配置（重开必须一并清掉）
        MatchEvent event = new MatchEvent();
        event.setMatchId(MATCH_ID);
        event.setEventSeq(1);
        event.setEventType("score_snapshot");
        event.setGameNo(3);
        event.setLeftScore(15);
        event.setRightScore(10);
        event.setServeSide("left");
        event.setPayloadJson("{}");
        matchEventMapper.insert(event);
        MatchLineupConfig lineup = new MatchLineupConfig();
        lineup.setMatchId(MATCH_ID);
        lineup.setGameNo(3);
        lineup.setLeftCourtJson("[]");
        lineup.setRightCourtJson("[]");
        lineup.setLeftMiddlePairIndexesJson("[]");
        lineup.setRightMiddlePairIndexesJson("[]");
        lineup.setServeSide("left");
        matchLineupConfigMapper.insert(lineup);

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 重开痕迹清理：结果字段归 NULL（非 0），状态回到未开始，执裁痕迹删除
        MatchRecord restarted = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(0, restarted.getStatus());
        assertNull(restarted.getWinnerId());
        assertNull(restarted.getLeftGameWins());
        assertNull(restarted.getRightGameWins());
        assertNull(restarted.getGameScores());
        assertNull(restarted.getScoreDisplay());
        assertNull(restarted.getRetiredSide());
        assertEquals(0, matchEventMapper.selectCount(new QueryWrapper<MatchEvent>().eq("match_id", MATCH_ID)));
        assertEquals(0, matchLineupConfigMapper.selectCount(new QueryWrapper<MatchLineupConfig>().eq("match_id", MATCH_ID)));

        // 二次结算 3:0（重开窗口重新打开后开关语义不变）
        finishMatch("left", 3, 0, List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 25, 18, "left"),
                buildGameScore(3, 25, 12, "left")));
        MatchRecord resettleSweep = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, resettleSweep.getStatus());
        assertEquals(LEFT_ID, resettleSweep.getWinnerId());
        assertEquals(3, resettleSweep.getLeftGameWins());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 二次结算 2:1 同样可行
        finishMatch("right", 1, 2, List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 20, 25, "right"),
                buildGameScore(3, 10, 15, "right")));
        MatchRecord resettleFull = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, resettleFull.getStatus());
        assertEquals(RIGHT_ID, resettleFull.getWinnerId());
        assertEquals(2, resettleFull.getRightGameWins());
    }

    /** 开关关闭对照：重开后 3:0 仍按 legacy 校验拒绝，重开不改变既有终场语义。 */
    @Test
    void forceFullGames_off_restart_shouldStillReject30ByLegacyValidation() throws Exception {
        prepareKnockoutMatch(false);
        finishMatch("left", 2, 0, List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 25, 18, "left")));
        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 25, 18, "left"),
                                        buildGameScore(3, 25, 12, "left"))))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    /** 场景 3（退赛后重开）：flag ON 退赛完赛 → 重开清 retiredSide → 再按正常 2:1 结算。 */
    @Test
    void forceFullGames_on_restartAfterRetire_shouldClearRetiredTraceAndResettle() throws Exception {
        prepareKnockoutMatch(true);

        finishMatch("left", 2, 0, List.of(buildGameScore(1, 25, 20, "left")), "right");
        MatchRecord retired = matchRecordMapper.selectById(MATCH_ID);
        assertEquals("right", retired.getRetiredSide());
        assertEquals(2, retired.getStatus());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord restarted = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(0, restarted.getStatus());
        assertNull(restarted.getRetiredSide());
        assertNull(restarted.getWinnerId());

        finishMatch("right", 1, 2, List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 20, 25, "right"),
                buildGameScore(3, 10, 15, "right")));
        MatchRecord resettle = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, resettle.getStatus());
        assertEquals(RIGHT_ID, resettle.getWinnerId());
        assertNull(resettle.getRetiredSide());
    }

    /**
     * 场景 4 后端契约（换设备恢复的前提）：forceFullGames 经 /record 重取，
     * 按分轮规则作用域返回 —— 小组赛轮 true、淘汰赛轮强制 false，不依赖客户端本地缓存。
     */
    @Test
    void forceFullGames_on_record_shouldResolveSwitchByRoundRuleScope() throws Exception {
        prepareKnockoutMatch(true);

        TournamentDivision division = tournamentDivisionMapper.selectById(TOURNAMENT_ID + "D01");
        division.setRoundRuleEnabled(true);
        tournamentDivisionMapper.updateById(division);
        insertRoundRule(0, 0, true);
        insertRoundRule(1, 1, false);

        // 淘汰赛轮（stageType=1, round=1）：强制 false
        mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.forceFullGames").value(false));

        // 小组赛轮（stageType=0, round=0）：透传 true
        MatchRecord groupMatch = matchRecordMapper.selectById(MATCH_ID);
        groupMatch.setStageType(0);
        groupMatch.setRoundNum(0);
        matchRecordMapper.updateById(groupMatch);
        mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.forceFullGames").value(true));
    }

    private void finishMatch(String winnerSide, int leftWins, int rightWins, List<Map<String, Object>> gameScores) throws Exception {
        finishMatch(winnerSide, leftWins, rightWins, gameScores, null);
    }

    private void finishMatch(String winnerSide, int leftWins, int rightWins, List<Map<String, Object>> gameScores,
                             String retiredSide) throws Exception {
        Map<String, Object> payload = retiredSide == null
                ? buildFinishPayload(winnerSide, leftWins, rightWins, gameScores)
                : retiredPayload(winnerSide, leftWins, rightWins, retiredSide, gameScores);
        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID, "user-creator"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    /** 淘汰赛轮次场次（stageType=1）：绕开「小组赛淘汰赛已生成不可重开」守卫，专测重开/恢复链路。 */
    private void prepareKnockoutMatch(boolean forceFullGames) {
        prepareMatch(forceFullGames);
        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        match.setStageType(1);
        match.setRoundNum(1);
        matchRecordMapper.updateById(match);
    }

    private void insertRoundRule(int stageType, int roundNum, boolean forceFullGames) {
        TournamentRoundRule rule = new TournamentRoundRule();
        rule.setTournamentId(TOURNAMENT_ID);
        rule.setDivisionId(TOURNAMENT_ID + "D01");
        rule.setStageType(stageType);
        rule.setRoundNum(roundNum);
        rule.setBestOf(3);
        rule.setGamesToWin(2);
        rule.setPointsToWin(25);
        rule.setDecidingPointsToWin(15);
        rule.setEnableDeuce(true);
        rule.setCapPoint(99);
        rule.setForceFullGames(forceFullGames);
        tournamentRoundRuleMapper.insert(rule);
    }

    private void prepareMatch(boolean forceFullGames) {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("force-full-test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(1);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(25);
        tournament.setDecidingPointsToWin(15);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(99);
        tournament.setCreatorUserId("user-creator");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        TournamentDivision division = new TournamentDivision();
        division.setId(TOURNAMENT_ID + "D01");
        division.setTournamentId(TOURNAMENT_ID);
        division.setName("默认组别");
        division.setSortOrder(0);
        division.setStatus(1);
        division.setParticipantType(0);
        division.setTournamentType(1);
        division.setRoundRobinRounds(1);
        division.setCurrentStage(1);
        division.setKnockoutGenerated(true);
        division.setBestOf(3);
        division.setGamesToWin(2);
        division.setPointsToWin(25);
        division.setDecidingPointsToWin(15);
        division.setEnableDeuce(true);
        division.setCapPoint(99);
        division.setForceFullGames(forceFullGames);
        tournamentDivisionMapper.insert(division);

        Player left = new Player();
        left.setId(LEFT_ID);
        left.setTournamentId(TOURNAMENT_ID);
        left.setDivisionId(division.getId());
        left.setName("Left Team");
        playerMapper.insert(left);

        Player right = new Player();
        right.setId(RIGHT_ID);
        right.setTournamentId(TOURNAMENT_ID);
        right.setDivisionId(division.getId());
        right.setName("Right Team");
        playerMapper.insert(right);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setDivisionId(division.getId());
        match.setRoundNum(0);
        match.setMatchIndex(0);
        match.setStageType(0);
        match.setLeftPlayerId(LEFT_ID);
        match.setRightPlayerId(RIGHT_ID);
        match.setStatus(1);
        matchRecordMapper.insert(match);
    }

    private User buildUser(String id) {
        User user = new User();
        user.setId(id);
        user.setOpenid("openid-" + id);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        return user;
    }

    private Map<String, Object> buildFinishPayload(String winnerSide, int leftWins, int rightWins, List<Map<String, Object>> gameScores) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", winnerSide);
        payload.put("leftScore", leftWins);
        payload.put("rightScore", rightWins);
        payload.put("leftGameWins", leftWins);
        payload.put("rightGameWins", rightWins);
        payload.put("gameScores", gameScores);
        return payload;
    }

    private Map<String, Object> retiredPayload(String winnerSide, int leftWins, int rightWins, String retiredSide, List<Map<String, Object>> gameScores) {
        Map<String, Object> payload = buildFinishPayload(winnerSide, leftWins, rightWins, gameScores);
        payload.put("retiredSide", retiredSide);
        return payload;
    }

    private Map<String, Object> buildGameScore(int gameNo, int leftScore, int rightScore, String winnerSide) {
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("gameNo", gameNo);
        score.put("leftScore", leftScore);
        score.put("rightScore", rightScore);
        score.put("winnerSide", winnerSide);
        return score;
    }
}
