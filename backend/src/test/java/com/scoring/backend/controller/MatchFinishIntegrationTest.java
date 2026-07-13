package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:match_finish_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchFinishIntegrationTest {

    private static final String TOURNAMENT_ID = "t-finish-1";
    private static final String MATCH_ID = "m-finish-1";
    private static final String NEXT_MATCH_ID = "m-finish-2";
    private static final String LEFT_ID = "p-left";
    private static final String RIGHT_ID = "p-right";

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
        when(authService.verifyToken(anyString())).thenReturn("user-creator");
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-creator"));
    }

    @Test
    void finishMatch_withValid3to1_shouldUpdateMatchAndPropagateWinner() throws Exception {
        prepareMatchWithNextMatch();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 1, List.of(
                                        buildGameScore(1, 25, 20, "left"),
                                        buildGameScore(2, 21, 25, "right"),
                                        buildGameScore(3, 25, 22, "left"),
                                        buildGameScore(4, 25, 18, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Verify source match updated
        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, source.getStatus());
        assertEquals(LEFT_ID, source.getWinnerId());
        assertEquals(3, source.getLeftGameWins());
        assertEquals(1, source.getRightGameWins());
        assertNotNull(source.getScoreDisplay());
        assertNotNull(source.getGameScores());

        // Verify winner propagated to next match
        MatchRecord next = matchRecordMapper.selectById(NEXT_MATCH_ID);
        assertEquals(LEFT_ID, next.getLeftPlayerId());
    }

    @Test
    void finishMatch_finalMatch_shouldEndTournament() throws Exception {
        prepareFinalMatch();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Verify tournament ended
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(2, tournament.getStatus());
    }

    @Test
    void finishMatch_withRetirement_shouldNotRequireGameScores() throws Exception {
        prepareFinalMatch();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "right");
        payload.put("leftScore", 0);
        payload.put("rightScore", 3);
        payload.put("leftGameWins", 0);
        payload.put("rightGameWins", 3);
        payload.put("retiredSide", "left");

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        assertEquals(2, source.getStatus());
        assertEquals("left", source.getRetiredSide());
        assertEquals(RIGHT_ID, source.getWinnerId());
    }

    @Test
    void finishMatch_withMissingOpponent_shouldReject() throws Exception {
        prepareFinalMatch();
        matchRecordMapper.update(null, new UpdateWrapper<MatchRecord>()
                .set("right_player_id", null)
                .eq("id", MATCH_ID));

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match participants are incomplete"));
    }

    @Test
    void finishMatch_alreadyFinishedOrRetired_shouldReject() throws Exception {
        prepareFinalMatch();
        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setStatus(2);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match already finished"));

        match.setStatus(3);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match already finished"));
    }

    @Test
    void finishMatch_negativeGameWins_shouldReject() throws Exception {
        prepareFinalMatch();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", -1); // negative
        payload.put("rightScore", 0);
        payload.put("leftGameWins", -1);
        payload.put("rightGameWins", 0);
        payload.put("gameScores", List.of(
                buildGameScore(1, 25, 20, "left")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void finishMatch_wrongWinnerSide_shouldReject() throws Exception {
        prepareFinalMatch();

        // winnerSide=left but right has more game wins
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", 1);
        payload.put("rightScore", 3);
        payload.put("leftGameWins", 1);
        payload.put("rightGameWins", 3);
        payload.put("gameScores", List.of(
                buildGameScore(1, 20, 25, "right"),
                buildGameScore(2, 25, 20, "left"),
                buildGameScore(3, 22, 25, "right"),
                buildGameScore(4, 18, 25, "right")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void finishMatch_gameScoreDraw_shouldReject() throws Exception {
        prepareFinalMatch();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 25, "left")  // draw
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void finishMatch_winnerSideMismatch_shouldReject() throws Exception {
        prepareFinalMatch();

        // Total wins: 3 for left, but one game's winnerSide says right for a left-win game
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", 3);
        payload.put("rightScore", 0);
        payload.put("leftGameWins", 3);
        payload.put("rightGameWins", 0);
        payload.put("gameScores", List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 25, 20, "left"),
                buildGameScore(3, 25, 18, "right")  // left won this game by score but winnerSide says right
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void finishMatch_missingGameScores_shouldReject() throws Exception {
        prepareFinalMatch();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", 3);
        payload.put("rightScore", 0);
        payload.put("leftGameWins", 3);
        payload.put("rightGameWins", 0);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }


    @Test
    void finishMatch_roundRobin_shouldEndTournamentOnlyAfterAllMatchesFinished() throws Exception {
        prepareRoundRobinMatches();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(1, tournament.getStatus());

        mockMvc.perform(put("/api/v1/matches/{id}/finish", NEXT_MATCH_ID)
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildFinishPayload(
                                "left", 3, 0, List.of(
                                        buildGameScore(1, 25, 18, "left"),
                                        buildGameScore(2, 25, 20, "left"),
                                        buildGameScore(3, 25, 15, "left")
                                )))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(2, tournament.getStatus());
    }

    // ==================== helpers ====================


    private void prepareRoundRobinMatches() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("round-robin-test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(2);
        tournament.setRoundRobinRounds(1);
        tournament.setCurrentStage(0);
        tournament.setKnockoutGenerated(false);
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId("user-creator");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player left = new Player();
        left.setId(LEFT_ID);
        left.setTournamentId(TOURNAMENT_ID);
        left.setName("Left Team");
        playerMapper.insert(left);

        Player right = new Player();
        right.setId(RIGHT_ID);
        right.setTournamentId(TOURNAMENT_ID);
        right.setName("Right Team");
        playerMapper.insert(right);

        Player third = new Player();
        third.setId("p-third");
        third.setTournamentId(TOURNAMENT_ID);
        third.setName("Third Team");
        playerMapper.insert(third);

        MatchRecord firstMatch = new MatchRecord();
        firstMatch.setId(MATCH_ID);
        firstMatch.setTournamentId(TOURNAMENT_ID);
        firstMatch.setRoundNum(1);
        firstMatch.setMatchIndex(0);
        firstMatch.setStageType(1);
        firstMatch.setLeftPlayerId(LEFT_ID);
        firstMatch.setRightPlayerId(RIGHT_ID);
        firstMatch.setStatus(1);
        matchRecordMapper.insert(firstMatch);

        MatchRecord secondMatch = new MatchRecord();
        secondMatch.setId(NEXT_MATCH_ID);
        secondMatch.setTournamentId(TOURNAMENT_ID);
        secondMatch.setRoundNum(2);
        secondMatch.setMatchIndex(0);
        secondMatch.setStageType(1);
        secondMatch.setLeftPlayerId(LEFT_ID);
        secondMatch.setRightPlayerId("p-third");
        secondMatch.setStatus(1);
        matchRecordMapper.insert(secondMatch);
    }
    private void prepareMatchWithNextMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("finish-test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId("user-creator");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player left = new Player();
        left.setId(LEFT_ID);
        left.setTournamentId(TOURNAMENT_ID);
        left.setName("Left Team");
        playerMapper.insert(left);

        Player right = new Player();
        right.setId(RIGHT_ID);
        right.setTournamentId(TOURNAMENT_ID);
        right.setName("Right Team");
        playerMapper.insert(right);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(1);
        match.setMatchIndex(0);
        match.setStageType(1);
        match.setLeftPlayerId(LEFT_ID);
        match.setRightPlayerId(RIGHT_ID);
        match.setStatus(1);
        match.setNextMatchId(NEXT_MATCH_ID);
        match.setNextMatchSlot("left");
        matchRecordMapper.insert(match);

        MatchRecord next = new MatchRecord();
        next.setId(NEXT_MATCH_ID);
        next.setTournamentId(TOURNAMENT_ID);
        next.setRoundNum(2);
        next.setMatchIndex(0);
        next.setStageType(1);
        next.setStatus(0);
        matchRecordMapper.insert(next);
    }

    private void prepareFinalMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("final-test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId("user-creator");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player left = new Player();
        left.setId(LEFT_ID);
        left.setTournamentId(TOURNAMENT_ID);
        left.setName("Left Team");
        playerMapper.insert(left);

        Player right = new Player();
        right.setId(RIGHT_ID);
        right.setTournamentId(TOURNAMENT_ID);
        right.setName("Right Team");
        playerMapper.insert(right);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(2);
        match.setMatchIndex(0);
        match.setStageType(1);
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

    private Map<String, Object> buildFinishPayload(String winnerSide, int leftScore, int rightScore,
                                                    List<Map<String, Object>> gameScores) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", winnerSide);
        payload.put("leftScore", leftScore);
        payload.put("rightScore", rightScore);
        payload.put("leftGameWins", leftScore);
        payload.put("rightGameWins", rightScore);
        payload.put("gameScores", gameScores);
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

