package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
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

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:match_write_auth_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchWriteAuthIntegrationTest {

    private static final String TOURNAMENT_ID = "t-auth-1";
    private static final String MATCH_ID = "m-auth-1";
    private static final String LEFT_TEAM_ID = "p-auth-left";
    private static final String RIGHT_TEAM_ID = "p-auth-right";
    private static final String CREATOR_ID = "user-creator";
    private static final String OTHER_ID = "user-other";

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
    private MatchEventMapper matchEventMapper;

    @Autowired
    private MatchReportMetaMapper matchReportMetaMapper;

    @Autowired
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn(CREATOR_ID);

        matchEventMapper.delete(new QueryWrapper<>());
        matchReportMetaMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentRefereeGrantMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());

        userMapper.insert(buildUser(CREATOR_ID, "openid-creator"));
        userMapper.insert(buildUser(OTHER_ID, "openid-other"));
        prepareMatch();
    }

    @Test
    void creatorShouldBeAbleToWriteAllMatchEndpoints() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn(CREATOR_ID);

        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/score", buildScorePayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/lineup-config", buildLineupPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/report-meta", buildReportMetaPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/events", buildEventsPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/finish", buildFinishPayload());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void nonCreatorShouldBeRejectedByAllMatchWriteEndpoints() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn(OTHER_ID);

        assertWriteForbidden("/api/v1/matches/" + MATCH_ID + "/score", buildScorePayload());
        assertWriteForbidden("/api/v1/matches/" + MATCH_ID + "/lineup-config", buildLineupPayload());
        assertWriteForbidden("/api/v1/matches/" + MATCH_ID + "/report-meta", buildReportMetaPayload());
        assertWriteForbidden("/api/v1/matches/" + MATCH_ID + "/events", buildEventsPayload());
        assertWriteForbidden("/api/v1/matches/" + MATCH_ID + "/finish", buildFinishPayload());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("only creator or referee can modify this match"));
    }

    @Test
    void refereeShouldBeAbleToWriteAllMatchEndpoints() throws Exception {
        // Grant OTHER_ID as referee
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(TOURNAMENT_ID);
        grant.setUserId(OTHER_ID);
        tournamentRefereeGrantMapper.insert(grant);

        when(authService.verifyToken(anyString())).thenReturn(OTHER_ID);

        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/score", buildScorePayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/lineup-config", buildLineupPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/report-meta", buildReportMetaPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/events", buildEventsPayload());
        assertWriteSuccess("/api/v1/matches/" + MATCH_ID + "/finish", buildFinishPayload());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer referee-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void anonymousShouldBeRejectedByAllMatchWriteEndpoints() throws Exception {
        assertWriteUnauthorized("/api/v1/matches/" + MATCH_ID + "/score", buildScorePayload());
        assertWriteUnauthorized("/api/v1/matches/" + MATCH_ID + "/lineup-config", buildLineupPayload());
        assertWriteUnauthorized("/api/v1/matches/" + MATCH_ID + "/report-meta", buildReportMetaPayload());
        assertWriteUnauthorized("/api/v1/matches/" + MATCH_ID + "/events", buildEventsPayload());
        assertWriteUnauthorized("/api/v1/matches/" + MATCH_ID + "/finish", buildFinishPayload());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    private void assertWriteSuccess(String path, Object payload) throws Exception {
        mockMvc.perform(put(path)
                        .header("Authorization", "Bearer creator-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void assertWriteForbidden(String path, Object payload) throws Exception {
        mockMvc.perform(put(path)
                        .header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("only creator or referee can modify this match"));
    }

    private void assertWriteUnauthorized(String path, Object payload) throws Exception {
        mockMvc.perform(put(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    private void prepareMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("auth-test");
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
        tournament.setCreatorUserId(CREATOR_ID);
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
            tournamentTeamMemberMapper.insert(buildMember("l-auth-" + i, LEFT_TEAM_ID, "L" + i, i));
            tournamentTeamMemberMapper.insert(buildMember("r-auth-" + i, RIGHT_TEAM_ID, "R" + i, i));
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

    private User buildUser(String id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        return user;
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

    private Map<String, Object> buildScorePayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("scoreDisplay", "25:20");
        payload.put("winnerId", LEFT_TEAM_ID);
        return payload;
    }

    private Map<String, Object> buildLineupPayload() {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("gameNo", 1);
        root.put("serveSide", "left");
        root.put("left", buildTeamPayload(
                List.of("l-auth-1", "l-auth-2", "l-auth-3", "l-auth-4", "l-auth-5", "l-auth-6"),
                List.of(1, 4),
                "l-auth-7",
                ""
        ));
        root.put("right", buildTeamPayload(
                List.of("r-auth-1", "r-auth-2", "r-auth-3", "r-auth-4", "r-auth-5", "r-auth-6"),
                List.of(2, 3),
                "r-auth-7",
                ""
        ));
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

    private Map<String, Object> buildReportMetaPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("matchTypeLabel", "测试赛");
        payload.put("matchTimeText", "2026-06-18 18:00");
        payload.put("chiefRefereeName", "主裁");
        payload.put("assistantRefereeName", "副裁");
        return payload;
    }

    private Map<String, Object> buildEventsPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("events", List.of(buildEventItem()));
        return payload;
    }

    private Map<String, Object> buildEventItem() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventSeq", 1);
        item.put("eventType", "timeout");
        item.put("gameNo", 1);
        item.put("leftScore", 8);
        item.put("rightScore", 7);
        item.put("serveSide", "left");
        item.put("payloadJson", "{\"side\":\"left\"}");
        return item;
    }

    private Map<String, Object> buildFinishPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("winnerSide", "left");
        payload.put("leftScore", 2);
        payload.put("rightScore", 0);
        payload.put("leftGameWins", 2);
        payload.put("rightGameWins", 0);
        payload.put("gameScores", List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 25, 18, "left")
        ));
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
