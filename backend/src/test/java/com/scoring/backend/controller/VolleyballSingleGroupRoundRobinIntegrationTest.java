package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.scoring.backend.controller.MatchLockTestSupport.withMatchLock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
 * 排球「单组大循环 + 前 4 进淘汰赛」全链路集成测试：
 * 创建侧 qualifiersPerGroup 放开到 {1,2,4} 且 qualifiers>2 仅允许单组；
 * 引擎侧 qualifiersPerGroup>=3 按标准种子序列摆签（4 强：半决赛 1v4、2v3）。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:volleyball_single_group_rr_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class VolleyballSingleGroupRoundRobinIntegrationTest {

    @Autowired
    private com.scoring.backend.mapper.TournamentDivisionMapper tournamentDivisionMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Autowired
    private PlayerMapper playerMapper;

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
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        tournamentRoundRuleMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1", "openid-user-1", true));
    }

    @Test
    void singleGroupRoundRobin_sevenTeams_fullChain_shouldSeedSemifinalOneVFourAndTwoVThree() throws Exception {
        String tournamentId = createVolleyballTeamTournament(7, 4, 4, false);

        // 创建侧：自动单组（组数=1），7 队全落第 1 组
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(4, tournament.getKnockoutSlots());
        assertEquals(4, tournament.getQualifiersPerGroup());
        assertEquals(7, tournament.getGroupSize());
        assertEquals(0, tournament.getCurrentStage());

        List<Player> participants = playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", tournamentId));
        assertEquals(7, participants.size());
        assertEquals(7, participants.stream().filter(p -> Integer.valueOf(1).equals(p.getGroupNo())).count());

        // 单循环 21 场：奇数队每轮 3 场 + 1 队轮空，共 7 轮
        List<MatchRecord> groupMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 0)
                        .orderByAsc("round_num", "match_index"));
        assertEquals(21, groupMatches.size());
        assertEquals(7, groupMatches.stream().map(MatchRecord::getRoundNum).distinct().count());
        for (int round = 1; round <= 7; round++) {
            final int roundNo = round;
            long matchesInRound = groupMatches.stream().filter(m -> Integer.valueOf(roundNo).equals(m.getRoundNum())).count();
            assertEquals(3, matchesInRound, "第 " + round + " 轮应为 3 场（1 队轮空）");
        }

        // 模拟打完小组赛：种子序号小者（强队）全胜 → 排名即种子序，无并列
        finishAllGroupMatchesBetterSeedWins(tournamentId);

        // 小组排名：前 4 名出线
        String standingsResponse = mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(false))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode standings = objectMapper.readTree(standingsResponse).path("data").path("groups").get(0).path("standings");
        Map<Integer, String> rankToPlayerId = new HashMap<>();
        for (JsonNode standing : standings) {
            rankToPlayerId.put(standing.path("rank").asInt(), standing.path("playerId").asText());
        }
        for (int rank = 1; rank <= 7; rank++) {
            assertTrue(rankToPlayerId.containsKey(rank), "缺少第 " + rank + " 名");
        }

        // 淘汰签表预览：半决赛 第1名v第4名、第2名v第3名
        String previewResponse = mockMvc.perform(post("/api/v1/tournaments/{id}/knockout-preview", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matches.length()").value(2))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode previewMatches = objectMapper.readTree(previewResponse).path("data").path("matches");
        assertEquals(rankToPlayerId.get(1), previewMatches.get(0).path("leftPlayer").path("playerId").asText());
        assertEquals(rankToPlayerId.get(4), previewMatches.get(0).path("rightPlayer").path("playerId").asText());
        assertEquals(rankToPlayerId.get(2), previewMatches.get(1).path("leftPlayer").path("playerId").asText());
        assertEquals(rankToPlayerId.get(3), previewMatches.get(1).path("rightPlayer").path("playerId").asText());

        // 生成淘汰赛：4 强签表落库，首轮对阵与预览一致
        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<MatchRecord> knockoutMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 1)
                        .eq("match_role", 0)
                        .orderByAsc("round_num", "match_index"));
        assertEquals(3, knockoutMatches.size());
        assertEquals(rankToPlayerId.get(1), knockoutMatches.get(0).getLeftPlayerId());
        assertEquals(rankToPlayerId.get(4), knockoutMatches.get(0).getRightPlayerId());
        assertEquals(rankToPlayerId.get(2), knockoutMatches.get(1).getLeftPlayerId());
        assertEquals(rankToPlayerId.get(3), knockoutMatches.get(1).getRightPlayerId());
        assertEquals(2, knockoutMatches.get(2).getRoundNum(), "第 3 场应为决赛（round 2），待半决赛打完才有选手");
        assertEquals(Integer.valueOf(0), knockoutMatches.get(2).getStatus());

        Tournament after = tournamentMapper.selectById(tournamentId);
        assertEquals(1, after.getCurrentStage());
        assertEquals(Boolean.TRUE, after.getKnockoutGenerated());
    }

    @Test
    void singleGroupRoundRobin_withThirdPlace_shouldAttachThirdPlaceMatchToSemifinalLosers() throws Exception {
        String tournamentId = createVolleyballTeamTournament(7, 4, 4, true);
        finishAllGroupMatchesBetterSeedWins(tournamentId);

        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<MatchRecord> knockoutMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 1)
                        .orderByAsc("round_num", "match_index"));
        assertEquals(4, knockoutMatches.size(), "2 场半决赛 + 1 场决赛 + 1 场季军赛");

        MatchRecord thirdPlace = knockoutMatches.stream()
                .filter(match -> Integer.valueOf(1).equals(match.getMatchRole()))
                .findFirst()
                .orElseThrow();
        assertEquals(2, thirdPlace.getRoundNum());
        for (MatchRecord match : knockoutMatches) {
            if (Integer.valueOf(1).equals(match.getRoundNum())) {
                assertEquals(thirdPlace.getId(), match.getLoserNextMatchId(),
                        "半决赛负者应进入季军赛: " + match.getId());
            }
        }
    }

    @Test
    void createTournament_qualifiersFourWithMultipleGroups_shouldReject() throws Exception {
        // 8 强 + 每组出线 4 = 2 组：后端必须拒绝且文案清晰
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                badmintonIndividualRequest(8, 8, 4))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("每组出线超过2名当前仅支持单组(大循环)赛制"));
    }

    @Test
    void createTournament_qualifiersThree_shouldReject() throws Exception {
        // 出线名额只放开到 4（{1,2,4}），3 仍然非法
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                badmintonIndividualRequest(4, 4, 3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("qualifiersPerGroup must be 1, 2 or 4"));
    }

    @Test
    void createTournament_qualifiersOneAndTwo_regression_shouldKeepLegacyBehaviour() throws Exception {
        // 回归：qualifiers=1 多组（2 组，各组第 1 出线；组内仍全组单循环）
        String qualsOne = createBadmintonIndividual(6, 2, 1);
        List<Player> qualsOnePlayers = playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", qualsOne));
        assertEquals(2, qualsOnePlayers.stream().map(Player::getGroupNo).distinct().count());
        assertEquals(3, qualsOnePlayers.stream().filter(p -> Integer.valueOf(1).equals(p.getGroupNo())).count());
        assertEquals(3, qualsOnePlayers.stream().filter(p -> Integer.valueOf(2).equals(p.getGroupNo())).count());

        // 回归：qualifiers=2 多组（2 组 × 2 名，交叉对阵路径不受影响）
        String qualsTwoMulti = createBadmintonIndividual(8, 4, 2);
        List<Player> qualsTwoPlayers = playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", qualsTwoMulti));
        assertEquals(2, qualsTwoPlayers.stream().map(Player::getGroupNo).distinct().count());

        // 回归：qualifiers=2 单组（历史"前二内战"回退分支）
        String qualsTwoSingle = createBadmintonIndividual(4, 2, 2);
        List<MatchRecord> groupMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", qualsTwoSingle)
                        .eq("stage_type", 0));
        assertEquals(6, groupMatches.size(), "4 队单组应打 6 场单循环");
        finishAllGroupMatchesBetterSeedWins(qualsTwoSingle);

        Map<Integer, String> rankToPlayerId = loadRankToPlayerId(qualsTwoSingle);
        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", qualsTwoSingle)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<MatchRecord> knockoutMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", qualsTwoSingle)
                        .eq("stage_type", 1));
        assertEquals(1, knockoutMatches.size());
        String left = knockoutMatches.get(0).getLeftPlayerId();
        String right = knockoutMatches.get(0).getRightPlayerId();
        assertTrue((rankToPlayerId.get(1).equals(left) && rankToPlayerId.get(2).equals(right))
                        || (rankToPlayerId.get(2).equals(left) && rankToPlayerId.get(1).equals(right)),
                "单组前二回退：决赛应为第 1 名 vs 第 2 名");
    }

    @Test
    void createTournament_roundRuleForceFullGamesOn_shouldForceOffOnKnockoutScope() throws Exception {
        // 修复 2 防御：手搓 API 在淘汰赛 scope（stageType=1）透传 forceFullGames=true 时，
        // 落库必须被强制写 false；小组赛 scope（stageType=0）正常透传 true。
        // 排球 小组赛+淘汰赛：4 队单组、决冝 1 轮（knockoutSlots=2）→ 分轮规则作用域 {(0,0) 小组赛, (1,1) 决赛}
        Map<String, Object> fullRule = new HashMap<>();
        fullRule.put("bestOf", 3);
        fullRule.put("gamesToWin", 2);
        fullRule.put("forceFullGames", true);

        Map<String, Object> request = new HashMap<>();
        request.put("sportType", 1);
        request.put("name", "淘汰轮强制关闭打满三局");
        request.put("location", "测试球馆");
        request.put("tournamentType", 1);
        request.put("knockoutSlots", 2);
        request.put("qualifiersPerGroup", 2);
        request.put("players", List.of());
        request.put("teams", buildVolleyballTeams(4));
        request.put("rule", Map.of("bestOf", 3, "gamesToWin", 2));
        request.put("roundRuleEnabled", true);
        request.put("roundRules", List.of(
                Map.of("stageType", 0, "roundNum", 0, "rule", fullRule),
                Map.of("stageType", 1, "roundNum", 1, "rule", fullRule)));

        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();

        List<TournamentRoundRule> rules = tournamentRoundRuleMapper.selectList(
                new QueryWrapper<TournamentRoundRule>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("stage_type", "round_num"));
        assertEquals(2, rules.size(), "应落库 小组赛 + 决赛 两条分轮规则");
        assertEquals(0, rules.get(0).getStageType());
        assertEquals(Boolean.TRUE, rules.get(0).getForceFullGames(), "小组赛 scope 透传 forceFullGames=true");
        assertEquals(1, rules.get(1).getStageType());
        assertEquals(Boolean.FALSE, rules.get(1).getForceFullGames(), "淘汰赛 scope 必须强制 forceFullGames=false");
    }

    // ======================== helpers ========================

    /** 排球队伍名单：teamCount 支队伍，每队 6 名队员，种子序号 = 队伍序号。 */
    private List<Map<String, Object>> buildVolleyballTeams(int teamCount) {
        List<Map<String, Object>> teams = new ArrayList<>();
        for (int i = 1; i <= teamCount; i++) {
            List<Map<String, Object>> members = new ArrayList<>();
            for (int j = 1; j <= 6; j++) {
                Map<String, Object> member = new HashMap<>();
                member.put("name", "T" + i + "P" + j);
                member.put("jerseyNumber", j);
                member.put("libero", false);
                member.put("captain", j == 1);
                members.add(member);
            }
            Map<String, Object> team = new HashMap<>();
            team.put("name", "队伍" + i);
            team.put("seed", i);
            team.put("members", members);
            teams.add(team);
        }
        return teams;
    }

    /** 排球队赛事：teamCount 支队伍，每队 6 名队员，种子序号 = 队伍序号。 */
    private String createVolleyballTeamTournament(int teamCount, int knockoutSlots, int qualifiersPerGroup,
                                                  boolean thirdPlaceEnabled) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("sportType", 1);
        request.put("name", "排球单组大循环测试");
        request.put("location", "测试球馆");
        request.put("tournamentType", 1);
        request.put("knockoutSlots", knockoutSlots);
        request.put("qualifiersPerGroup", qualifiersPerGroup);
        request.put("thirdPlaceEnabled", thirdPlaceEnabled);
        request.put("players", List.of());
        request.put("teams", buildVolleyballTeams(teamCount));
        request.put("rule", Map.of("bestOf", 3, "gamesToWin", 2));

        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private Map<String, Object> badmintonIndividualRequest(int playerCount, int knockoutSlots, int qualifiersPerGroup) {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            players.add(Map.of("name", "P" + i, "seed", i));
        }
        Map<String, Object> request = new HashMap<>();
        request.put("sportType", 0);
        request.put("name", "单组出线校验");
        request.put("location", "court");
        request.put("tournamentType", 1);
        request.put("knockoutSlots", knockoutSlots);
        request.put("qualifiersPerGroup", qualifiersPerGroup);
        request.put("players", players);
        request.put("rule", Map.of("bestOf", 3, "gamesToWin", 2, "pointsToWin", 21, "enableDeuce", true, "capPoint", 30));
        return request;
    }

    private String createBadmintonIndividual(int playerCount, int knockoutSlots, int qualifiersPerGroup) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                badmintonIndividualRequest(playerCount, knockoutSlots, qualifiersPerGroup))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    /** 种子序号小者（强队）全胜：胜场数互不相同 → 排名 = 种子序，无并列。 */
    private void finishAllGroupMatchesBetterSeedWins(String tournamentId) throws Exception {
        Map<String, Integer> seedByPlayerId = new HashMap<>();
        for (Player player : playerMapper.selectList(new QueryWrapper<Player>().eq("tournament_id", tournamentId))) {
            seedByPlayerId.put(player.getId(), player.getSeedRank());
        }
        List<MatchRecord> groupMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 0));
        for (MatchRecord match : groupMatches) {
            Integer leftSeed = seedByPlayerId.get(match.getLeftPlayerId());
            Integer rightSeed = seedByPlayerId.get(match.getRightPlayerId());
            String winnerId = leftSeed <= rightSeed ? match.getLeftPlayerId() : match.getRightPlayerId();
            finishGroupMatchRecord(match, winnerId);
        }
    }

    private Map<Integer, String> loadRankToPlayerId(String tournamentId) throws Exception {
        String response = mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode standings = objectMapper.readTree(response).path("data").path("groups").get(0).path("standings");
        Map<Integer, String> rankToPlayerId = new HashMap<>();
        for (JsonNode standing : standings) {
            rankToPlayerId.put(standing.path("rank").asInt(), standing.path("playerId").asText());
        }
        return rankToPlayerId;
    }

    private void finishGroupMatchRecord(MatchRecord match, String winnerId) throws Exception {
        String winnerSide = winnerId.equals(match.getLeftPlayerId()) ? "left" : "right";
        int leftGameWins = "left".equals(winnerSide) ? 2 : 0;
        int rightGameWins = "right".equals(winnerSide) ? 2 : 0;
        mockMvc.perform(put("/api/v1/matches/{id}/finish", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(finishBody(winnerSide, leftGameWins, rightGameWins)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String finishBody(String winnerSide, int leftGameWins, int rightGameWins) {
        StringBuilder gameScores = new StringBuilder();
        for (int i = 1; i <= leftGameWins; i++) {
            gameScores.append(gameScores.isEmpty() ? "" : ",")
                    .append("{\"gameNo\":%d,\"leftScore\":21,\"rightScore\":15,\"winnerSide\":\"left\"}".formatted(i));
        }
        for (int i = 1; i <= rightGameWins; i++) {
            gameScores.append(gameScores.isEmpty() ? "" : ",")
                    .append("{\"gameNo\":%d,\"leftScore\":15,\"rightScore\":21,\"winnerSide\":\"right\"}"
                            .formatted(leftGameWins + i));
        }
        return """
                {
                  "winnerSide": "%s",
                  "leftScore": %d,
                  "rightScore": %d,
                  "leftGameWins": %d,
                  "rightGameWins": %d,
                  "gameScores": [%s]
                }
                """.formatted(winnerSide, leftGameWins, rightGameWins, leftGameWins, rightGameWins, gameScores);
    }

    private User buildUser(String id, String openid, boolean profileCompleted) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(profileCompleted);
        return user;
    }
}
