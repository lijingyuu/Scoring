package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TournamentMapper;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 排球自定义胜分（常规局/决胜局/追分开关）的后端链路验证：
 * 创建落库 → 赛事详情 VO → 比赛记录 VO（记分页判定依据）→ 分轮规则。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:volleyball_rule_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class VolleyballRuleConfigIntegrationTest {

    @Autowired
    private com.scoring.backend.mapper.TournamentDivisionMapper tournamentDivisionMapper;

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TournamentMapper tournamentMapper;
    @Autowired
    private MatchRecordMapper matchRecordMapper;
    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1"));
    }

    @Test
    void roundRobin_customRule_persistedAndExposedViaDetailAndRecord() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 1,
                  "name": "排球循环赛自定义胜分",
                  "tournamentType": 2,
                  "roundRobinRounds": 1,
                  "players": [],
                  "teams": [
                    {"name": "A队", "members": [
                      {"name":"A1","jerseyNumber":1,"captain":true},{"name":"A2","jerseyNumber":2},
                      {"name":"A3","jerseyNumber":3},{"name":"A4","jerseyNumber":4},
                      {"name":"A5","jerseyNumber":5},{"name":"A6","jerseyNumber":6}]},
                    {"name": "B队", "members": [
                      {"name":"B1","jerseyNumber":1,"captain":true},{"name":"B2","jerseyNumber":2},
                      {"name":"B3","jerseyNumber":3},{"name":"B4","jerseyNumber":4},
                      {"name":"B5","jerseyNumber":5},{"name":"B6","jerseyNumber":6}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": 12, "enableDeuce": false, "capPoint": 99}
                }
                """);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(21, tournament.getPointsToWin());
        assertEquals(12, tournament.getDecidingPointsToWin());
        assertEquals(false, tournament.getEnableDeuce());
        assertEquals(99, tournament.getCapPoint());

        // 详情页展示依据
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsToWin").value(21))
                .andExpect(jsonPath("$.data.decidingPointsToWin").value(12))
                .andExpect(jsonPath("$.data.enableDeuce").value(false))
                .andExpect(jsonPath("$.data.capPoint").value(99));

        // 记分页判定依据
        String matchId = firstMatchId(tournamentId);
        mockMvc.perform(get("/api/v1/matches/{id}/record", matchId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsToWin").value(21))
                .andExpect(jsonPath("$.data.decidingPointsToWin").value(12))
                .andExpect(jsonPath("$.data.enableDeuce").value(false))
                .andExpect(jsonPath("$.data.capPoint").value(99));
    }

    @Test
    void groupKnockout_roundRules_propagateToMatchRecord() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 1,
                  "name": "排球小组淘汰自定义胜分",
                  "tournamentType": 1,
                  "knockoutSlots": 2,
                  "qualifiersPerGroup": 1,
                  "players": [],
                  "teams": [
                    {"name": "A队", "members": [
                      {"name":"A1","jerseyNumber":1,"captain":true},{"name":"A2","jerseyNumber":2},
                      {"name":"A3","jerseyNumber":3},{"name":"A4","jerseyNumber":4},
                      {"name":"A5","jerseyNumber":5},{"name":"A6","jerseyNumber":6}]},
                    {"name": "B队", "members": [
                      {"name":"B1","jerseyNumber":1,"captain":true},{"name":"B2","jerseyNumber":2},
                      {"name":"B3","jerseyNumber":3},{"name":"B4","jerseyNumber":4},
                      {"name":"B5","jerseyNumber":5},{"name":"B6","jerseyNumber":6}]},
                    {"name": "C队", "members": [
                      {"name":"C1","jerseyNumber":1,"captain":true},{"name":"C2","jerseyNumber":2},
                      {"name":"C3","jerseyNumber":3},{"name":"C4","jerseyNumber":4},
                      {"name":"C5","jerseyNumber":5},{"name":"C6","jerseyNumber":6}]},
                    {"name": "D队", "members": [
                      {"name":"D1","jerseyNumber":1,"captain":true},{"name":"D2","jerseyNumber":2},
                      {"name":"D3","jerseyNumber":3},{"name":"D4","jerseyNumber":4},
                      {"name":"D5","jerseyNumber":5},{"name":"D6","jerseyNumber":6}]}
                  ],
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 0, "roundNum": 0, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": 12, "enableDeuce": false, "capPoint": 99}},
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 5, "gamesToWin": 3, "pointsToWin": 25, "decidingPointsToWin": 15, "enableDeuce": true, "capPoint": 99}}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": 12, "enableDeuce": false, "capPoint": 99}
                }
                """);

        // 顶层保存小组规则（详情页展示）
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roundRuleEnabled").value(true))
                .andExpect(jsonPath("$.data.pointsToWin").value(21))
                .andExpect(jsonPath("$.data.decidingPointsToWin").value(12));

        // 小组赛 record 读取分轮规则（stageType=0）
        String matchId = firstMatchId(tournamentId);
        mockMvc.perform(get("/api/v1/matches/{id}/record", matchId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsToWin").value(21))
                .andExpect(jsonPath("$.data.decidingPointsToWin").value(12))
                .andExpect(jsonPath("$.data.enableDeuce").value(false));
    }

    @Test
    void volleyball_decidingPointsGreaterThanPointsToWin_rejected() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "非法决胜局胜分",
                                  "tournamentType": 2,
                                  "roundRobinRounds": 1,
                                  "players": [],
                                  "teams": [
                                    {"name": "A队", "members": [
                                      {"name":"A1","jerseyNumber":1,"captain":true},{"name":"A2","jerseyNumber":2},
                                      {"name":"A3","jerseyNumber":3},{"name":"A4","jerseyNumber":4},
                                      {"name":"A5","jerseyNumber":5},{"name":"A6","jerseyNumber":6}]},
                                    {"name": "B队", "members": [
                                      {"name":"B1","jerseyNumber":1,"captain":true},{"name":"B2","jerseyNumber":2},
                                      {"name":"B3","jerseyNumber":3},{"name":"B4","jerseyNumber":4},
                                      {"name":"B5","jerseyNumber":5},{"name":"B6","jerseyNumber":6}]}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": 30, "enableDeuce": true, "capPoint": 99}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private String createAndGetId(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private String firstMatchId(String tournamentId) {
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertEquals(false, matches.isEmpty());
        return matches.get(0).getId();
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
