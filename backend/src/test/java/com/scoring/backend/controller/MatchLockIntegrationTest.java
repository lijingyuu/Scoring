package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:match_lock_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchLockIntegrationTest {

    private static final String TOURNAMENT_ID = "t-lock-1";
    private static final String MATCH_ID = "m-lock-1";
    private static final String CREATOR_ID = "user-creator";
    private static final String REFEREE_A_ID = "user-referee-a";
    private static final String REFEREE_B_ID = "user-referee-b";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchRecordMapper matchRecordMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn(REFEREE_A_ID);
        matchEventMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentRefereeGrantMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());

        userMapper.insert(buildUser(CREATOR_ID));
        userMapper.insert(buildUser(REFEREE_A_ID));
        userMapper.insert(buildUser(REFEREE_B_ID));
        prepareMatch();
        grantReferee(REFEREE_A_ID);
        grantReferee(REFEREE_B_ID);
    }

    @Test
    void acquireLock_shouldBlockOtherRefereeBeforeExpire() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        acquireLock(REFEREE_B_ID, "token-b", false)
                .andExpect(jsonPath("$.data.lockedByUserId").value(REFEREE_A_ID));
    }

    @Test
    void acquireLock_shouldAllowExpiredLockTakeover() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        expireLock();

        acquireLock(REFEREE_B_ID, "token-b", true);

        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(REFEREE_B_ID, match.getLockedByUserId());
        assertEquals("token-b", match.getLockToken());
    }

    @Test
    void acquireLock_shouldAllowCreatorOverride() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        acquireLock(CREATOR_ID, "token-creator", true);

        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(CREATOR_ID, match.getLockedByUserId());
        assertEquals("token-creator", match.getLockToken());
    }

    @Test
    void heartbeatAndRelease_shouldRequireCurrentToken() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        heartbeat(REFEREE_A_ID, "wrong-token", false);
        release(REFEREE_A_ID, "wrong-token");
        assertEquals("token-a", matchRecordMapper.selectById(MATCH_ID).getLockToken());

        heartbeat(REFEREE_A_ID, "token-a", true);
        release(REFEREE_A_ID, "token-a");

        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        assertNull(match.getLockedByUserId());
        assertNull(match.getLockToken());
        assertNull(match.getLockExpireTime());
    }

    @Test
    void writeGuard_shouldRejectAuthorizedUserWithOtherSessionToken() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        when(authService.verifyToken(anyString())).thenReturn(REFEREE_B_ID);

        mockMvc.perform(put("/api/v1/matches/{id}/score", MATCH_ID)
                        .header("Authorization", "Bearer token-b")
                        .header("X-Match-Lock-Token", "token-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "winnerId", "p-lock-left",
                                "scoreDisplay", "2:0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void writeGuard_shouldSucceedWithValidLockAndClearLockAfterFinish() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .header("X-Match-Lock-Token", "token-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "winnerSide", "left",
                                "leftScore", 2,
                                "rightScore", 0,
                                "leftGameWins", 2,
                                "rightGameWins", 0,
                                "gameScores", List.of(
                                        Map.of("gameNo", 1, "leftScore", 21, "rightScore", 15, "winnerSide", "left"),
                                        Map.of("gameNo", 2, "leftScore", 21, "rightScore", 18, "winnerSide", "left")
                                )
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 终态清锁：比赛结束（finish）成功后锁字段应全部清空
        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        assertNull(match.getLockedByUserId());
        assertNull(match.getLockToken());
        assertNull(match.getLockExpireTime());
    }

    @Test
    void writeGuard_shouldRejectExpiredLock() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        expireLock();

        mockMvc.perform(put("/api/v1/matches/{id}/score", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .header("X-Match-Lock-Token", "token-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "winnerId", "p-lock-left",
                                "scoreDisplay", "2:0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void writeGuard_shouldRejectLockHolderMismatchEvenWithOwnToken() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        // B 是授权裁判，用自己的 token，但锁由 A 持有 → 持有者不匹配，403
        when(authService.verifyToken(anyString())).thenReturn(REFEREE_B_ID);
        mockMvc.perform(put("/api/v1/matches/{id}/score", MATCH_ID)
                        .header("Authorization", "Bearer token-b")
                        .header("X-Match-Lock-Token", "token-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "winnerId", "p-lock-left",
                                "scoreDisplay", "2:0"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void heartbeat_shouldExtendLockExpireTime() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        LocalDateTime beforeExpire = matchRecordMapper.selectById(MATCH_ID).getLockExpireTime();

        heartbeat(REFEREE_A_ID, "token-a", true);

        LocalDateTime afterExpire = matchRecordMapper.selectById(MATCH_ID).getLockExpireTime();
        assertNotNull(afterExpire);
        assertTrue(afterExpire.isAfter(beforeExpire));
    }

    @Test
    void acquireLock_shouldAllowSameTokenReentry() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        // 同会话（相同 token）重进 → 幂等成功，仍是同一持有者
        acquireLock(REFEREE_A_ID, "token-a", true)
                .andExpect(jsonPath("$.data.lockedByUserId").value(REFEREE_A_ID));
    }

    @Test
    void acquireLock_shouldRejectHolderWithFreshTokenBeforeExpire() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);
        // 锁未过期时，同一裁判用新 token 重进（页面被杀死后 75s 内重进）→ 当前行为返回失败，直到锁过期。
        // 这是对现有行为的记录性测试；若后续放开「同用户接管」，需同步调整。
        acquireLock(REFEREE_A_ID, "token-b", false)
                .andExpect(jsonPath("$.data.lockedByUserId").value(REFEREE_A_ID));
    }

    @Test
    void restart_shouldRequireValidLockAndClearIt() throws Exception {
        acquireLock(REFEREE_A_ID, "token-a", true);

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .header("X-Match-Lock-Token", "token-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 重启成功同样清空锁字段（resetMatchResult 级联清锁）
        MatchRecord match = matchRecordMapper.selectById(MATCH_ID);
        assertNull(match.getLockedByUserId());
        assertNull(match.getLockToken());
        assertNull(match.getLockExpireTime());
    }

    @Test
    void reportMeta_shouldNotRequireMatchLock() throws Exception {
        // report-meta 不受执裁锁守卫：无 X-Match-Lock-Token 头也应能写入战报草稿
        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private org.springframework.test.web.servlet.ResultActions acquireLock(String userId,
                                                                           String lockToken,
                                                                           boolean success) throws Exception {
        when(authService.verifyToken(anyString())).thenReturn(userId);
        return mockMvc.perform(post("/api/v1/matches/{id}/lock", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockToken\":\"" + lockToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(success));
    }

    private void heartbeat(String userId, String lockToken, boolean success) throws Exception {
        when(authService.verifyToken(anyString())).thenReturn(userId);
        mockMvc.perform(post("/api/v1/matches/{id}/heartbeat", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockToken\":\"" + lockToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.success").value(success));
    }

    private void release(String userId, String lockToken) throws Exception {
        when(authService.verifyToken(anyString())).thenReturn(userId);
        mockMvc.perform(post("/api/v1/matches/{id}/release", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lockToken\":\"" + lockToken + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void expireLock() {
        MatchRecord update = new MatchRecord();
        update.setId(MATCH_ID);
        update.setLockExpireTime(LocalDateTime.now().minusSeconds(1));
        matchRecordMapper.updateById(update);
    }

    private void prepareMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("lock-test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId(CREATOR_ID);
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player left = new Player();
        left.setId("p-lock-left");
        left.setTournamentId(TOURNAMENT_ID);
        left.setName("Left");
        playerMapper.insert(left);

        Player right = new Player();
        right.setId("p-lock-right");
        right.setTournamentId(TOURNAMENT_ID);
        right.setName("Right");
        playerMapper.insert(right);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(1);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setLeftPlayerId(left.getId());
        match.setRightPlayerId(right.getId());
        match.setStatus(1);
        matchRecordMapper.insert(match);
    }

    private void grantReferee(String userId) {
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(TOURNAMENT_ID);
        grant.setUserId(userId);
        tournamentRefereeGrantMapper.insert(grant);
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
}
