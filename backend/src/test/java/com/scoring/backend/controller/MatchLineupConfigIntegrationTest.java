package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:volleyball_lineup_config_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchLineupConfigIntegrationTest {

    private static final String TOURNAMENT_ID = "t-1";
    private static final String MATCH_ID = "m-1";
    private static final String LEFT_TEAM_ID = "p-left";
    private static final String RIGHT_TEAM_ID = "p-right";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private TournamentTeamMemberMapper tournamentTeamMemberMapper;

    @Autowired
    private MatchRecordMapper matchRecordMapper;

    @Autowired
    private MatchLineupConfigMapper matchLineupConfigMapper;

    @Autowired
    private MatchReportMetaMapper matchReportMetaMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchEventMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        matchReportMetaMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1", "openid-user-1"));
        prepareMatch();
    }

    @Test
    void saveGameOne_shouldCreateAndAllowOverwrite() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "left",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(1, 4),
                                List.of(2, 3),
                                "l7",
                                "",
                                "r7",
                                "r8"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchLineupConfig created = matchLineupConfigMapper.selectOne(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", MATCH_ID)
                        .eq("game_no", 1)
        );
        assertNotNull(created);
        assertEquals("left", created.getServeSide());

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "right",
                                List.of("l6", "l5", "l4", "l3", "l2", "l1"),
                                List.of("r6", "r5", "r4", "r3", "r2", "r1"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchLineupConfig updated = matchLineupConfigMapper.selectOne(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", MATCH_ID)
                        .eq("game_no", 1)
        );
        assertEquals("right", updated.getServeSide());
        assertEquals("[\"l6\",\"l5\",\"l4\",\"l3\",\"l2\",\"l1\"]", updated.getLeftCourtJson());
        assertEquals("[]", updated.getLeftMiddlePairIndexesJson());
    }

    @Test
    void getGameTwoWithoutOwnConfig_shouldInheritGameOneAndFlipServeSide() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "left",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(0, 5),
                                List.of(2, 3),
                                "l7",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .param("gameNo", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.gameNo").value(2))
                .andExpect(jsonPath("$.data.exists").value(false))
                .andExpect(jsonPath("$.data.effectiveFromGameNo").value(1))
                .andExpect(jsonPath("$.data.config.serveSide").value("right"))
                .andExpect(jsonPath("$.data.config.left.court[0]").value("l1"))
                .andExpect(jsonPath("$.data.config.left.middlePairIndexes[0]").value(0))
                .andExpect(jsonPath("$.data.config.left.middlePairIndexes[1]").value(5))
                .andExpect(jsonPath("$.data.config.left.libero1Id").value("l7"));
    }

    @Test
    void saveLineupConfig_shouldRejectLiberoInsideStartingSix() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "left",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(0, 5),
                                List.of(1, 4),
                                "l1",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("left libero1 cannot be in starting six"));
    }

    @Test
    void saveLineupConfig_alreadyFinishedOrRetired_shouldReject() throws Exception {
        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setStatus(2);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDefaultLineupPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match already finished"));

        match.setStatus(3);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDefaultLineupPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match already finished"));
    }

    @Test
    void saveLineupConfig_withMissingOpponent_shouldReject() throws Exception {
        matchRecordMapper.update(null, new UpdateWrapper<MatchRecord>()
                .set("right_player_id", null)
                .eq("id", MATCH_ID));

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildDefaultLineupPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("match participants are incomplete"));
    }

    @Test
    void savePreviousGameAfterLaterGameSaved_shouldBeLocked() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "left",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                2,
                                "right",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "right",
                                List.of("l6", "l5", "l4", "l3", "l2", "l1"),
                                List.of("r6", "r5", "r4", "r3", "r2", "r1"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("this game is already locked by later lineup config"));
    }

    @Test
    void restartMatch_afterCompletedFlow_shouldClearLockAndAllowGameOneAgain() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "left",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                2,
                                "right",
                                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord finished = new MatchRecord();
        finished.setId(MATCH_ID);
        finished.setLeftGameWins(2);
        finished.setRightGameWins(0);
        finished.setGameScores("[{\"gameNo\":1,\"leftScore\":25,\"rightScore\":18,\"winnerSide\":\"left\"}]");
        finished.setStatus(2);
        matchRecordMapper.updateById(finished);

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/lineup-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildLineupPayload(
                                1,
                                "right",
                                List.of("l6", "l5", "l4", "l3", "l2", "l1"),
                                List.of("r6", "r5", "r4", "r3", "r2", "r1"),
                                List.of(),
                                List.of(),
                                "",
                                "",
                                "",
                                ""
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord restarted = matchRecordMapper.selectById(MATCH_ID);
        assertNotNull(restarted);
        assertEquals(0, restarted.getStatus());
        assertNull(restarted.getGameScores());
        assertEquals(0, matchLineupConfigMapper.selectCount(new QueryWrapper<MatchLineupConfig>().eq("match_id", MATCH_ID).gt("game_no", 1)));
    }

    @Test
    void restartMatch_shouldClearPropagatedDownstreamBracketState() throws Exception {
        prepareDownstreamMatch("m-final", "right");
        prepareDownstreamMatch("m-champion", null);

        MatchRecord updateSource = new MatchRecord();
        updateSource.setId(MATCH_ID);
        updateSource.setWinnerId(LEFT_TEAM_ID);
        updateSource.setStatus(2);
        updateSource.setNextMatchId("m-final");
        updateSource.setNextMatchSlot("left");
        matchRecordMapper.updateById(updateSource);

        MatchRecord updateFinal = new MatchRecord();
        updateFinal.setId("m-final");
        updateFinal.setLeftPlayerId(LEFT_TEAM_ID);
        updateFinal.setRightPlayerId(RIGHT_TEAM_ID);
        updateFinal.setWinnerId(LEFT_TEAM_ID);
        updateFinal.setScoreDisplay("2:0");
        updateFinal.setLeftGameWins(2);
        updateFinal.setRightGameWins(0);
        updateFinal.setGameScores("[{\"gameNo\":1,\"leftScore\":25,\"rightScore\":18,\"winnerSide\":\"left\"}]");
        updateFinal.setStatus(2);
        updateFinal.setNextMatchId("m-champion");
        updateFinal.setNextMatchSlot("right");
        matchRecordMapper.updateById(updateFinal);

        MatchRecord updateChampion = new MatchRecord();
        updateChampion.setId("m-champion");
        updateChampion.setRightPlayerId(LEFT_TEAM_ID);
        updateChampion.setWinnerId(LEFT_TEAM_ID);
        updateChampion.setScoreDisplay("2:1");
        updateChampion.setLeftGameWins(1);
        updateChampion.setRightGameWins(2);
        updateChampion.setStatus(2);
        matchRecordMapper.updateById(updateChampion);

        MatchLineupConfig finalLineup = new MatchLineupConfig();
        finalLineup.setMatchId("m-final");
        finalLineup.setGameNo(1);
        finalLineup.setLeftCourtJson("[\"l1\",\"l2\",\"l3\",\"l4\",\"l5\",\"l6\"]");
        finalLineup.setRightCourtJson("[\"r1\",\"r2\",\"r3\",\"r4\",\"r5\",\"r6\"]");
        finalLineup.setLeftMiddlePairIndexesJson("[]");
        finalLineup.setRightMiddlePairIndexesJson("[]");
        finalLineup.setServeSide("left");
        matchLineupConfigMapper.insert(finalLineup);

        MatchReportMeta finalReport = new MatchReportMeta();
        finalReport.setMatchId("m-final");
        finalReport.setMetaJson("{}");
        matchReportMetaMapper.insert(finalReport);

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord source = matchRecordMapper.selectById(MATCH_ID);
        MatchRecord finalMatch = matchRecordMapper.selectById("m-final");
        MatchRecord champion = matchRecordMapper.selectById("m-champion");
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);

        assertNull(source.getWinnerId());
        assertEquals(0, source.getStatus());
        assertNull(finalMatch.getLeftPlayerId());
        assertEquals(RIGHT_TEAM_ID, finalMatch.getRightPlayerId());
        assertNull(finalMatch.getWinnerId());
        assertNull(finalMatch.getScoreDisplay());
        assertEquals(0, finalMatch.getStatus());
        assertNull(champion.getRightPlayerId());
        assertNull(champion.getWinnerId());
        assertEquals(0, champion.getStatus());
        assertEquals(1, tournament.getStatus());
        assertEquals(0, matchLineupConfigMapper.selectCount(new QueryWrapper<MatchLineupConfig>().eq("match_id", "m-final")));
        assertEquals(0, matchReportMetaMapper.selectCount(new QueryWrapper<MatchReportMeta>().eq("match_id", "m-final")));
    }

    private void prepareMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("test");
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
        tournament.setCapPoint(99);
        tournament.setCreatorUserId("user-1");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player leftTeam = new Player();
        leftTeam.setId(LEFT_TEAM_ID);
        leftTeam.setTournamentId(TOURNAMENT_ID);
        leftTeam.setName("Left Team");
        playerMapper.insert(leftTeam);

        Player rightTeam = new Player();
        rightTeam.setId(RIGHT_TEAM_ID);
        rightTeam.setTournamentId(TOURNAMENT_ID);
        rightTeam.setName("Right Team");
        playerMapper.insert(rightTeam);

        for (int i = 1; i <= 8; i++) {
            tournamentTeamMemberMapper.insert(buildMember("l" + i, LEFT_TEAM_ID, "L" + i, i));
            tournamentTeamMemberMapper.insert(buildMember("r" + i, RIGHT_TEAM_ID, "R" + i, i));
        }

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(1);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setLeftPlayerId(LEFT_TEAM_ID);
        match.setRightPlayerId(RIGHT_TEAM_ID);
        match.setStatus(1);
        matchRecordMapper.insert(match);
    }

    private void prepareDownstreamMatch(String id, String nextSlot) {
        MatchRecord match = new MatchRecord();
        match.setId(id);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(2);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setStatus(0);
        if (nextSlot != null) {
            match.setNextMatchId("m-champion");
            match.setNextMatchSlot(nextSlot);
        }
        matchRecordMapper.insert(match);
    }

    private TournamentTeamMember buildMember(String id, String participantId, String name, int jerseyNumber) {
        TournamentTeamMember member = new TournamentTeamMember();
        member.setId(id);
        member.setTournamentId(TOURNAMENT_ID);
        member.setParticipantId(participantId);
        member.setName(name);
        member.setJerseyNumber(jerseyNumber);
        member.setLibero(false);
        member.setCaptain(jerseyNumber == 1);
        member.setDisplayOrder(jerseyNumber);
        return member;
    }

    private User buildUser(String id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        return user;
    }

    private Map<String, Object> buildDefaultLineupPayload() {
        return buildLineupPayload(
                1,
                "left",
                List.of("l1", "l2", "l3", "l4", "l5", "l6"),
                List.of("r1", "r2", "r3", "r4", "r5", "r6"),
                List.of(),
                List.of(),
                "",
                "",
                "",
                ""
        );
    }

    private Map<String, Object> buildLineupPayload(int gameNo,
                                                   String serveSide,
                                                   List<String> leftCourt,
                                                   List<String> rightCourt,
                                                   List<Integer> leftPairIndexes,
                                                   List<Integer> rightPairIndexes,
                                                   String leftLibero1Id,
                                                   String leftLibero2Id,
                                                   String rightLibero1Id,
                                                   String rightLibero2Id) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("gameNo", gameNo);
        root.put("serveSide", serveSide);
        root.put("left", buildTeamPayload(leftCourt, leftPairIndexes, leftLibero1Id, leftLibero2Id));
        root.put("right", buildTeamPayload(rightCourt, rightPairIndexes, rightLibero1Id, rightLibero2Id));
        return root;
    }

    private Map<String, Object> buildTeamPayload(List<String> court,
                                                 List<Integer> middlePairIndexes,
                                                 String libero1Id,
                                                 String libero2Id) {
        Map<String, Object> team = new LinkedHashMap<>();
        team.put("court", court);
        team.put("middlePairIndexes", middlePairIndexes);
        team.put("libero1Id", libero1Id);
        team.put("libero2Id", libero2Id);
        return team;
    }
}
