package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
        "spring.datasource.url=jdbc:h2:mem:round_robin_tournament_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class RoundRobinTournamentIntegrationTest {

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

    @Test
    void groupsAndStandings_shouldExposeRoundRobinAsSingleVirtualGroup() throws Exception {
        String tournamentId = createBadmintonRoundRobinTournament(4, 1);

        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentType").value(2))
                .andExpect(jsonPath("$.data.roundRobinRounds").value(1))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].groupNo").value(1))
                .andExpect(jsonPath("$.data.groups[0].players.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].matches.length()").value(6))
                .andExpect(jsonPath("$.data.groups[0].matches[0].roundNum").value(1))
                .andExpect(jsonPath("$.data.groups[0].matches[0].leftPlayerId").isNotEmpty())
                .andExpect(jsonPath("$.data.groups[0].matches[0].rightPlayerId").isNotEmpty());

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups.length()").value(1))
                .andExpect(jsonPath("$.data.groups[0].groupNo").value(1))
                .andExpect(jsonPath("$.data.groups[0].standings.length()").value(4))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(false));
    }


    @Test
    void displayRanks_shouldShowDashBeforeAnyFinishedMatch() throws Exception {
        String tournamentId = createBadmintonRoundRobinTournament(4, 1);

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("-"))
                .andExpect(jsonPath("$.data.groups[0].standings[3].displayRankText").value("-"));
    }

    @Test
    void displayRanks_shouldActivateAfterFirstFinishedMatch() throws Exception {
        String tournamentId = createBadmintonRoundRobinTournament(4, 1);
        String firstMatchId = firstRoundMatchId(tournamentId, 0);

        finishMatch(firstMatchId, "left", List.of(
                buildGameScore(1, 21, 10, "left"),
                buildGameScore(2, 21, 11, "left")
        ));

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[1].displayRankText").value("2"))
                .andExpect(jsonPath("$.data.groups[0].standings[2].displayRankText").value("2"))
                .andExpect(jsonPath("$.data.groups[0].standings[3].displayRankText").value("4"));
    }

    @Test
    void displayRanks_shouldUseSharedRankWhenTieCannotBeResolvedByHeadToHead() throws Exception {
        String tournamentId = createBadmintonRoundRobinTournament(4, 1);
        String matchOne = firstRoundMatchId(tournamentId, 0);
        String matchTwo = firstRoundMatchId(tournamentId, 1);

        finishMatch(matchOne, "left", List.of(
                buildGameScore(1, 21, 10, "left"),
                buildGameScore(2, 21, 11, "left")
        ));
        finishMatch(matchTwo, "left", List.of(
                buildGameScore(1, 21, 10, "left"),
                buildGameScore(2, 21, 11, "left")
        ));

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[1].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[2].displayRankText").value("3"))
                .andExpect(jsonPath("$.data.groups[0].standings[3].displayRankText").value("3"));
    }

    @Test
    void displayRanks_shouldBreakTieWhenHeadToHeadWinnerExists() throws Exception {
        String tournamentId = createBadmintonRoundRobinTournament(2, 2);
        String firstMatchId = firstRoundMatchId(tournamentId, 0);
        String secondMatchId = secondRoundMatchId(tournamentId, 0);

        finishMatch(firstMatchId, "left", List.of(
                buildGameScore(1, 21, 10, "left"),
                buildGameScore(2, 21, 11, "left")
        ));
        finishMatch(secondMatchId, "right", List.of(
                buildGameScore(1, 10, 21, "right"),
                buildGameScore(2, 11, 21, "right")
        ));

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[1].displayRankText").value("2"));
    }
    private String createBadmintonRoundRobinTournament(int playerCount, int roundRobinRounds) throws Exception {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            Map<String, Object> player = new HashMap<>();
            player.put("name", "R" + i);
            player.put("seed", i);
            players.add(player);
        }

        Map<String, Object> rule = new HashMap<>();
        rule.put("bestOf", 3);
        rule.put("gamesToWin", 2);
        rule.put("pointsToWin", 21);
        rule.put("enableDeuce", true);
        rule.put("capPoint", 30);

        Map<String, Object> request = new HashMap<>();
        request.put("sportType", 0);
        request.put("name", "round robin");
        request.put("location", "court");
        request.put("tournamentType", 2);
        request.put("roundRobinRounds", roundRobinRounds);
        request.put("players", players);
        request.put("rule", rule);

        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode root = objectMapper.readTree(response);
        return root.path("data").path("tournamentId").asText();
    }


    private void finishMatch(String matchId, String winnerSide, List<Map<String, Object>> gameScores) throws Exception {
        int leftWins = "left".equals(winnerSide) ? gameScores.size() : 0;
        int rightWins = "right".equals(winnerSide) ? gameScores.size() : 0;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", winnerSide);
        payload.put("leftScore", leftWins);
        payload.put("rightScore", rightWins);
        payload.put("leftGameWins", leftWins);
        payload.put("rightGameWins", rightWins);
        payload.put("gameScores", gameScores);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", matchId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String firstRoundMatchId(String tournamentId, int matchIndex) {
        return matchRecordMapper.selectList(new QueryWrapper<com.scoring.backend.domain.entity.MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("round_num", 1)
                        .orderByAsc("match_index"))
                .get(matchIndex)
                .getId();
    }

    private String secondRoundMatchId(String tournamentId, int matchIndex) {
        return matchRecordMapper.selectList(new QueryWrapper<com.scoring.backend.domain.entity.MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("round_num", 2)
                        .orderByAsc("match_index"))
                .get(matchIndex)
                .getId();
    }

    private Map<String, Object> buildGameScore(int gameNo, int leftScore, int rightScore, String winnerSide) {
        Map<String, Object> score = new LinkedHashMap<>();
        score.put("gameNo", gameNo);
        score.put("leftScore", leftScore);
        score.put("rightScore", rightScore);
        score.put("winnerSide", winnerSide);
        return score;
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