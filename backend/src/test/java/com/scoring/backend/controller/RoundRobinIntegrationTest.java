package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:round_robin_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class RoundRobinIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TournamentMapper tournamentMapper;
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
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1"));
    }

    // ==================== 创建 ====================

    @Test
    void createBadmintonSingleRoundRobin_shouldGenerate6MatchesFor4Players() throws Exception {
        String tournamentId = createBadmintonRoundRobin(4, 1);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(2, tournament.getTournamentType());
        assertEquals(1, tournament.getRoundRobinRounds());
        assertEquals(1, tournament.getStatus());

        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        // 4 players → C(4,2) = 6 matches
        assertEquals(6, matches.size());
        // All matches should have stageType=1 (league)
        assertEquals(6, matches.stream().filter(m -> m.getStageType() == 1).count());
        // All should be pending
        assertEquals(6, matches.stream().filter(m -> m.getStatus() == 0).count());
    }

    @Test
    void createBadmintonDoubleRoundRobin_shouldGenerate12MatchesFor4Players() throws Exception {
        String tournamentId = createBadmintonRoundRobin(4, 2);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(2, tournament.getTournamentType());
        assertEquals(2, tournament.getRoundRobinRounds());

        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        // 4 players × 2 rounds → 12 matches
        assertEquals(12, matches.size());
    }

    @Test
    void createVolleyballSingleRoundRobin_shouldSucceed() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "排球循环赛",
                                  "location": "体育馆",
                                  "tournamentType": 2,
                                  "roundRobinRounds": 1,
                                  "players": [],
                                  "teams": [
                                    {"name": "A队", "members": [{"name":"A1","jerseyNumber":1,"captain":true},{"name":"A2","jerseyNumber":2},{"name":"A3","jerseyNumber":3},{"name":"A4","jerseyNumber":4},{"name":"A5","jerseyNumber":5},{"name":"A6","jerseyNumber":6}]},
                                    {"name": "B队", "members": [{"name":"B1","jerseyNumber":1,"captain":true},{"name":"B2","jerseyNumber":2},{"name":"B3","jerseyNumber":3},{"name":"B4","jerseyNumber":4},{"name":"B5","jerseyNumber":5},{"name":"B6","jerseyNumber":6}]},
                                    {"name": "C队", "members": [{"name":"C1","jerseyNumber":1,"captain":true},{"name":"C2","jerseyNumber":2},{"name":"C3","jerseyNumber":3},{"name":"C4","jerseyNumber":4},{"name":"C5","jerseyNumber":5},{"name":"C6","jerseyNumber":6}]}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(2, tournament.getTournamentType());
        assertEquals(1, tournament.getRoundRobinRounds());

        // 3 teams → C(3,2) = 3 matches
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertEquals(3, matches.size());
    }

    // ==================== 完赛与赛事结束判定 ====================

    @Test
    void finishOneMatch_shouldNotEndRoundRobinTournament() throws Exception {
        String tournamentId = createBadmintonRoundRobin(3, 1);
        List<MatchRecord> matches = loadMatches(tournamentId);
        assertEquals(3, matches.size()); // C(3,2)=3

        // Finish first match
        finishMatch(matches.get(0).getId(), "left", matches.get(0).getLeftPlayerId(), 2, 0);

        // Tournament should still be running
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(1, tournament.getStatus());

        // Match should be finished
        MatchRecord finished = matchRecordMapper.selectById(matches.get(0).getId());
        assertEquals(2, finished.getStatus());
    }

    @Test
    void finishAllMatches_shouldEndRoundRobinTournament() throws Exception {
        String tournamentId = createBadmintonRoundRobin(3, 1);
        List<MatchRecord> matches = loadMatches(tournamentId);

        // Finish all 3 matches
        for (MatchRecord match : matches) {
            finishMatch(match.getId(), "left", match.getLeftPlayerId(), 2, 0);
        }

        // Tournament should be ended
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(2, tournament.getStatus());
    }

    @Test
    void finishAllButOne_shouldNotEndTournament() throws Exception {
        String tournamentId = createBadmintonRoundRobin(3, 1);
        List<MatchRecord> matches = loadMatches(tournamentId);

        // Finish first 2 matches
        finishMatch(matches.get(0).getId(), "left", matches.get(0).getLeftPlayerId(), 2, 0);
        finishMatch(matches.get(1).getId(), "left", matches.get(1).getLeftPlayerId(), 2, 0);

        // Tournament should still be running
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(1, tournament.getStatus());
    }

    @Test
    void updateScore_shouldNotPropagateInRoundRobin() throws Exception {
        String tournamentId = createBadmintonRoundRobin(3, 1);
        List<MatchRecord> matches = loadMatches(tournamentId);
        MatchRecord target = matches.get(0);

        mockMvc.perform(put("/api/v1/matches/{id}/score", target.getId())
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerId": "%s",
                                  "scoreDisplay": "2:0"
                                }
                                """.formatted(target.getLeftPlayerId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Match should be finished
        MatchRecord updated = matchRecordMapper.selectById(target.getId());
        assertEquals(2, updated.getStatus());
        assertEquals(target.getLeftPlayerId(), updated.getWinnerId());

        // Tournament should NOT end (only 1 of 3 matches done)
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(1, tournament.getStatus());
    }

    // ==================== 小组数据（getGroups） ====================

    @Test
    void getGroups_shouldReturnSingleGroupForRoundRobin() throws Exception {
        String tournamentId = createBadmintonRoundRobin(4, 1);

        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentType").value(2))
                .andExpect(jsonPath("$.data.roundRobinRounds").value(1))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].groupNo").value(1))
                .andExpect(jsonPath("$.data.groups[0].players.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].matches.length()").value(6));
    }

    // ==================== 积分榜 ====================

    @Test
    void getStandings_shouldCalculateForRoundRobin() throws Exception {
        String tournamentId = createBadmintonRoundRobin(3, 1);
        List<MatchRecord> matches = loadMatches(tournamentId);
        assertEquals(3, matches.size()); // C(3,2)=3

        // Find P1 (seed=1) by checking which player is in ALL matches
        // Circle method with 3 players + bye produces: [P2vP3, P1vP3, P1vP2]
        // We want P1 to win 2 matches.
        List<Player> players = loadPlayers(tournamentId);
        String p1Id = players.stream()
                .filter(p -> Integer.valueOf(1).equals(p.getSeedRank()))
                .findFirst().orElseThrow().getId();

        // Win each match where P1 appears
        for (MatchRecord m : matches) {
            boolean p1IsLeft = p1Id.equals(m.getLeftPlayerId());
            boolean p1IsRight = p1Id.equals(m.getRightPlayerId());
            if (p1IsLeft || p1IsRight) {
                String winnerSide = p1IsLeft ? "left" : "right";
                finishMatch(m.getId(), winnerSide, p1Id, 2, 0);
            } else {
                finishMatch(m.getId(), "left", m.getLeftPlayerId(), 2, 0);
            }
        }

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].standings[0].playerName").isNotEmpty())
                .andExpect(jsonPath("$.data.groups[0].standings[0].rank").value(1))
                .andExpect(jsonPath("$.data.groups[0].standings[0].matchWins").value(2));
    }

    // ==================== 参数校验 ====================

    @Test
    void createRoundRobin_invalidRounds_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "非法循环赛",
                                  "location": "测试",
                                  "tournamentType": 2,
                                  "roundRobinRounds": 3,
                                  "players": [{"name":"A"},{"name":"B"}],
                                  "rule": {"bestOf":3,"gamesToWin":2,"pointsToWin":21,"enableDeuce":true,"capPoint":30}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("roundRobinRounds must be 1 or 2"));
    }

    @Test
    void createRoundRobin_tooFewPlayers_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "单人选",
                                  "location": "测试",
                                  "tournamentType": 2,
                                  "roundRobinRounds": 1,
                                  "players": [{"name":"A"}],
                                  "rule": {"bestOf":3,"gamesToWin":2,"pointsToWin":21,"enableDeuce":true,"capPoint":30}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    // ==================== helpers ====================

    private String createBadmintonRoundRobin(int playerCount, int rounds) throws Exception {
        StringBuilder players = new StringBuilder("[");
        for (int i = 1; i <= playerCount; i++) {
            if (i > 1) players.append(",");
            players.append("{\"name\":\"P").append(i).append("\",\"seed\":").append(i).append("}");
        }
        players.append("]");

        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "循环赛测试",
                                  "location": "测试馆",
                                  "tournamentType": 2,
                                  "roundRobinRounds": %d,
                                  "players": %s,
                                  "rule": {"bestOf":3,"gamesToWin":2,"pointsToWin":21,"enableDeuce":true,"capPoint":30}
                                }
                                """.formatted(rounds, players.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private List<MatchRecord> loadMatches(String tournamentId) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("round_num", "match_index"));
    }

    private List<Player> loadPlayers(String tournamentId) {
        return playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", tournamentId));
    }

    private void finishMatch(String matchId, String winnerSide, String winnerId, int leftWins, int rightWins) throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/score", matchId)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerId": "%s",
                                  "scoreDisplay": "%d:%d"
                                }
                                """.formatted(winnerId, leftWins, rightWins)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
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
