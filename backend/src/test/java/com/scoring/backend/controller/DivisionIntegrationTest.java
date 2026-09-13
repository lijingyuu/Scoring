package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.UserMapper;
 import com.scoring.backend.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
 import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
 import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 组别（division）层级验收测试：
 * 1. 多组别创建：各组别独立赛制/规则/选手/赛程
 * 2. 组别级 API 与旧赛事级 API 兼容层
 * 3. 状态两级聚合：全部组别完赛才完赛
 * 4. 校验拦截：排球多组别 400、组别重名 400
 */
@SpringBootTest(classes = com.scoring.backend.ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:division_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class DivisionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MatchRecordMapper matchRecordMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private TournamentDivisionMapper tournamentDivisionMapper;
 
     @Autowired
     private com.scoring.backend.mapper.TournamentRankingConfigMapper tournamentRankingConfigMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-division");
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser());
    }

    private static final String THREE_DIVISION_BODY = """
            {
              "name": "南京大学羽毛球新生杯",
              "location": "南京",
              "sportType": 0,
              "participantType": 0,
              "refereePassword": "12345678",
              "divisions": [
                {
                  "name": "男单组",
                  "tournamentType": 0,
                  "players": [
                    {"name": "男单甲", "seed": 1},
                    {"name": "男单乙", "seed": 2},
                    {"name": "男单丙", "seed": 3},
                    {"name": "男单丁", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                },
                {
                  "name": "女单组",
                  "tournamentType": 2,
                  "roundRobinRounds": 1,
                  "players": [
                    {"name": "女单甲"},
                    {"name": "女单乙"},
                    {"name": "女单丙"}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": true, "capPoint": 15}
                },
                {
                  "name": "男双组",
                  "tournamentType": 0,
                  "players": [
                    {"name": "双打A组"}, {"name": "双打B组"}, {"name": "双打C组"}, {"name": "双打D组"}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 15, "enableDeuce": true, "capPoint": 21}
                }
              ]
            }
            """;

    @Test
    void multiDivisionCreate_shouldCreateIndependentDivisions() throws Exception {
        String tournamentId = createAndGetId(THREE_DIVISION_BODY);

        // 组别列表：3 个，按创建顺序
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("sort_order"));
        assertEquals(3, divisions.size());
        assertEquals("男单组", divisions.get(0).getName());
        assertEquals("女单组", divisions.get(1).getName());
        assertEquals("男双组", divisions.get(2).getName());

        // 每组别独立规则
        assertEquals(21, divisions.get(0).getPointsToWin());
        assertEquals(11, divisions.get(1).getPointsToWin());
        assertEquals(15, divisions.get(2).getPointsToWin());
        assertEquals(2, divisions.get(1).getTournamentType());

        // 选手归属组别：男单 4 人 + 女单 3 人 + 男双 4 人
        assertEquals(4L, countPlayers(divisions.get(0).getId()));
        assertEquals(3L, countPlayers(divisions.get(1).getId()));
        assertEquals(4L, countPlayers(divisions.get(2).getId()));

        // 比赛归属组别：男单 4 人淘汰 = 半决赛2 + 决赛1 = 3 场；女单循环 3 场；男双 3 场
        assertEquals(3L, countMatches(divisions.get(0).getId()));
        assertEquals(3L, countMatches(divisions.get(1).getId()));
        assertEquals(3L, countMatches(divisions.get(2).getId()));

        // 赛事详情带组别摘要
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.divisions.length()").value(3))
                .andExpect(jsonPath("$.data.divisions[0].name").value("男单组"))
                .andExpect(jsonPath("$.data.divisions[0].playerCount").value(4));

        // 组别详情 API
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions/{divisionId}", tournamentId, divisions.get(1).getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.divisionName").value("女单组"))
                .andExpect(jsonPath("$.data.pointsToWin").value(11))
                .andExpect(jsonPath("$.data.players.length()").value(3));

        // 组别级 groups API 只返回该组别数据（女单循环）
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions/{divisionId}/groups", tournamentId, divisions.get(1).getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].players.length()").value(3))
                .andExpect(jsonPath("$.data.groups[0].matches.length()").value(3));

        // 组别级 bracket API（男单淘汰）
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions/{divisionId}/bracket", tournamentId, divisions.get(0).getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches.length()").value(3))
                .andExpect(jsonPath("$.data.players.length()").value(4));
    }

    @Test
    void legacyFlatPayload_shouldCreateSingleDefaultDivision() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "排球新生杯",
                  "location": "体育馆",
                  "sportType": 1,
                  "participantType": 1,
                  "tournamentType": 0,
                  "teams": [
                    {"name": "队一", "members": [{"name": "a1", "jerseyNumber": 1, "captain": true}, {"name": "a2", "jerseyNumber": 2}, {"name": "a3", "jerseyNumber": 3}, {"name": "a4", "jerseyNumber": 4}, {"name": "a5", "jerseyNumber": 5}, {"name": "a6", "jerseyNumber": 6}]},
                    {"name": "队二", "members": [{"name": "b1", "jerseyNumber": 1, "captain": true}, {"name": "b2", "jerseyNumber": 2}, {"name": "b3", "jerseyNumber": 3}, {"name": "b4", "jerseyNumber": 4}, {"name": "b5", "jerseyNumber": 5}, {"name": "b6", "jerseyNumber": 6}]}
                  ],
                  "rule": {"bestOf": 5, "gamesToWin": 3, "pointsToWin": 25, "decidingPointsToWin": 15, "enableDeuce": true, "capPoint": 27}
                }
                """);

        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId));
        assertEquals(1, divisions.size());
        assertEquals("默认组别", divisions.get(0).getName());
        assertEquals(3, divisions.get(0).getGamesToWin());

        // 旧详情接口照常工作且平铺字段来自默认组别
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.divisions.length()").value(1))
                .andExpect(jsonPath("$.data.gamesToWin").value(3));
    }

    @Test
    void volleyballWithMultipleDivisions_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "排球多组别",
                                  "sportType": 1,
                                  "participantType": 1,
                                  "divisions": [
                                    {"name": "A组", "teams": [{"name": "队一", "members": [{"name":"a1","jerseyNumber":1,"captain":true},{"name":"a2","jerseyNumber":2},{"name":"a3","jerseyNumber":3},{"name":"a4","jerseyNumber":4},{"name":"a5","jerseyNumber":5},{"name":"a6","jerseyNumber":6}]}, {"name": "队二", "members": [{"name":"g1","jerseyNumber":1,"captain":true},{"name":"g2","jerseyNumber":2},{"name":"g3","jerseyNumber":3},{"name":"g4","jerseyNumber":4},{"name":"g5","jerseyNumber":5},{"name":"g6","jerseyNumber":6}]}]},
                                    {"name": "B组", "teams": [{"name": "队三", "members": [{"name":"m1","jerseyNumber":1,"captain":true},{"name":"m2","jerseyNumber":2},{"name":"m3","jerseyNumber":3},{"name":"m4","jerseyNumber":4},{"name":"m5","jerseyNumber":5},{"name":"m6","jerseyNumber":6}]}, {"name": "队四", "members": [{"name":"s1","jerseyNumber":1,"captain":true},{"name":"s2","jerseyNumber":2},{"name":"s3","jerseyNumber":3},{"name":"s4","jerseyNumber":4},{"name":"s5","jerseyNumber":5},{"name":"s6","jerseyNumber":6}]}]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void duplicateDivisionNames_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "重名组别赛事",
                                  "sportType": 0,
                                  "participantType": 0,
                                  "divisions": [
                                    {"name": "男单组", "tournamentType": 0, "players": [{"name": "a"}, {"name": "b"}]},
                                    {"name": "男单组", "tournamentType": 0, "players": [{"name": "c"}, {"name": "d"}]}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void legacyEndpoints_onMultiDivisionTournament_shouldReturnFirstDivision() throws Exception {
        String tournamentId = createAndGetId(THREE_DIVISION_BODY);
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("sort_order"));

        // 旧 bracket 接口 → 第一个组别（男单）的数据
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matches.length()").value(3))
                .andExpect(jsonPath("$.data.players.length()").value(4))
                .andExpect(jsonPath("$.data.divisionId").value(divisions.get(0).getId()))
                .andExpect(jsonPath("$.data.divisionName").value("男单组"));

        // 旧 groups 接口 → 第一个组别
        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.divisionName").value("男单组"))
                .andExpect(jsonPath("$.data.groups.length()").value(0));
    }

    @Test
    void statusAggregation_allDivisionsFinished_shouldFinishTournament() throws Exception {
        // 两个循环赛组别，各 3 人（每组 3 场）
        String tournamentId = createAndGetId("""
                {
                  "name": "聚合状态赛",
                  "sportType": 0,
                  "participantType": 0,
                  "divisions": [
                    {
                      "name": "A组",
                      "tournamentType": 2,
                      "roundRobinRounds": 1,
                      "players": [{"name": "A1"}, {"name": "A2"}, {"name": "A3"}],
                      "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": true, "capPoint": 15}
                    },
                    {
                      "name": "B组",
                      "tournamentType": 2,
                      "roundRobinRounds": 1,
                      "players": [{"name": "B1"}, {"name": "B2"}, {"name": "B3"}],
                      "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": true, "capPoint": 15}
                    }
                  ]
                }
                """);
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("sort_order"));
        assertEquals(2, divisions.size());

        // A 组全部完赛
        finishAllMatches(divisions.get(0).getId(), "user-division");

        TournamentDivision divisionA = tournamentDivisionMapper.selectById(divisions.get(0).getId());
        TournamentDivision divisionB = tournamentDivisionMapper.selectById(divisions.get(1).getId());
        assertEquals(2, divisionA.getStatus());
        // 已生成赛程的组别创建即置 1（进行中）
        assertEquals(1, divisionB.getStatus());

        // 赛事仍在进行中
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        // B 组全部完赛 → 赛事完赛
        finishAllMatches(divisions.get(1).getId(), "user-division");

        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2));

        // 组别摘要状态
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value(2))
                .andExpect(jsonPath("$.data[1].status").value(2));
    }

    @Test
    void divisionRules_resolvePerDivision() throws Exception {
        String tournamentId = createAndGetId(THREE_DIVISION_BODY);
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("sort_order"));

        // 女单组（11 分/局 1 局）的比赛 record 应返回组别规则
        MatchRecord womenMatch = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisions.get(1).getId())).get(0);
        assertNotNull(womenMatch);
        mockMvc.perform(get("/api/v1/matches/{id}/record", womenMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsToWin").value(11))
                .andExpect(jsonPath("$.data.bestOf").value(1));

        // 男单组（21 分 3 局）对比
        MatchRecord menMatch = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisions.get(0).getId())).get(0);
        mockMvc.perform(get("/api/v1/matches/{id}/record", menMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pointsToWin").value(21))
                .andExpect(jsonPath("$.data.bestOf").value(3));
    }

     @Test
     void webAdminPayloadShape_shouldBeAccepted() throws Exception {
         // 精确模拟 web 管理端多组别 payload：type0 带 knockoutRounds、rule 带 decidingPointsToWin、不传 rankingTemplate
         String tournamentId = createAndGetId("""
                 {
                   "name": "web端多组别",
                   "location": "web",
                   "sportType": 0,
                   "participantType": 0,
                   "teamMatchTemplate": 0,
                   "refereePassword": "12345678",
                   "divisions": [
                     {
                       "name": "男单组",
                       "tournamentType": 0,
                       "knockoutRounds": 2,
                       "thirdPlaceEnabled": true,
                       "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "decidingPointsToWin": null, "enableDeuce": true, "capPoint": 30},
                       "players": [{"name": "a", "seed": 1}, {"name": "b"}, {"name": "c"}, {"name": "d"}]
                     },
                     {
                       "name": "女单组",
                       "tournamentType": 1,
                       "knockoutSlots": 4,
                       "qualifiersPerGroup": 2,
                       "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "decidingPointsToWin": null, "enableDeuce": true, "capPoint": 15},
                       "players": [{"name": "e"}, {"name": "f"}, {"name": "g"}, {"name": "h"}]
                     }
                   ]
                 }
                 """);
 
         List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                 new QueryWrapper<TournamentDivision>()
                         .eq("tournament_id", tournamentId)
                         .orderByAsc("sort_order"));
         assertEquals(2, divisions.size());
         // type0 + knockoutRounds=2 → 4 人恰好落在 (2,4]
         assertEquals(2, divisions.get(0).getKnockoutRounds());
         // 半决赛2 + 决赛1 + 季军赛1 = 4 场
         assertEquals(4, matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>()
                 .eq("division_id", divisions.get(0).getId())));
         assertEquals(Boolean.TRUE, divisions.get(0).getThirdPlaceEnabled());
     }
 
     @Test
     void miniProgramPayloadShape_shouldBeAccepted() throws Exception {
         // 精确模拟小程序多组别 payload：type0 不带 knockoutRounds（后端自适应）、带共享 rankingTemplate
         String tournamentId = createAndGetId("""
                 {
                   "name": "小程序多组别",
                   "location": "mp",
                   "sportType": 0,
                   "participantType": 0,
                   "teamMatchTemplate": 0,
                   "refereePassword": "12345678",
                   "divisions": [
                     {
                       "name": "男单组",
                       "tournamentType": 0,
                       "thirdPlaceEnabled": false,
                       "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                       "rankingTemplate": "BADMINTON_COMMON_1",
                       "players": [{"name": "a"}, {"name": "b"}, {"name": "c"}, {"name": "d"}, {"name": "e"}]
                     },
                     {
                       "name": "循环组",
                       "tournamentType": 2,
                       "roundRobinRounds": 1,
                       "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": true, "capPoint": 15},
                       "rankingTemplate": "BADMINTON_COMMON_1",
                       "players": [{"name": "x"}, {"name": "y"}, {"name": "z"}]
                     }
                   ]
                 }
                 """);
 
         List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                 new QueryWrapper<TournamentDivision>()
                         .eq("tournament_id", tournamentId)
                         .orderByAsc("sort_order"));
         assertEquals(2, divisions.size());
         // 5 人 → 后端自适应 3 轮（容量 8，轮空 3）
         assertEquals(3, divisions.get(0).getKnockoutRounds());
         // type2 组别落库了共享排名模板
         com.scoring.backend.domain.entity.TournamentRankingConfig rankingConfig =
                 tournamentRankingConfigMapper.selectOne(new QueryWrapper<com.scoring.backend.domain.entity.TournamentRankingConfig>()
                         .eq("division_id", divisions.get(1).getId()));
         assertNotNull(rankingConfig);
         assertTrue(rankingConfig.getConfigJson().contains("BADMINTON_COMMON_1"));
     }
 
    // ======================== helpers ========================

    private long countPlayers(String divisionId) {
        return playerMapper.selectCount(new QueryWrapper<Player>().eq("division_id", divisionId));
    }

    private long countMatches(String divisionId) {
        return matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>().eq("division_id", divisionId));
    }

    private void finishAllMatches(String divisionId, String userId) throws Exception {
        List<MatchRecord> matches = matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .orderByAsc("round_num", "match_index"));
        for (MatchRecord match : matches) {
            if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
                continue;
            }
            mockMvc.perform(put("/api/v1/matches/{id}/finish", match.getId())
                            .header("Authorization", "Bearer test-token")
                            .with(withMatchLock(matchRecordMapper, match.getId(), userId))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(buildFinishPayload())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    private Map<String, Object> buildFinishPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", 1);
        payload.put("rightScore", 0);
        payload.put("leftGameWins", 1);
        payload.put("rightGameWins", 0);
        payload.put("gameScores", List.of(gameScore(1)));
        return payload;
    }

    private Map<String, Object> gameScore(int gameNo) {
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("gameNo", gameNo);
        score.put("leftScore", 11);
        score.put("rightScore", 5);
        score.put("winnerSide", "left");
        return score;
    }

    private static RequestPostProcessor withMatchLock(MatchRecordMapper matchRecordMapper, String matchId, String userId) {
        return MatchLockTestSupport.withMatchLock(matchRecordMapper, matchId, userId);
    }

    private String createAndGetId(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private User buildUser() {
        User user = new User();
        user.setId("user-division");
        user.setOpenid("openid-division");
        user.setNickname("division");
        user.setProfileCompleted(true);
        return user;
    }
}
