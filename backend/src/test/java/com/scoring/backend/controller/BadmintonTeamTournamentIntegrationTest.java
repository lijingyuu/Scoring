package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:badminton_team_tournament_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class BadmintonTeamTournamentIntegrationTest {

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
    private TournamentTeamMemberMapper tournamentTeamMemberMapper;

    @Autowired
    private TournamentRefereeConfigMapper tournamentRefereeConfigMapper;

    @Autowired
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        tournamentRefereeGrantMapper.delete(new QueryWrapper<>());
        tournamentRefereeConfigMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1"));
    }

    @Test
    void badmintonIndividual_withoutParticipantType_defaultsToIndividualAndVosReturnType() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Badminton individual",
                  "tournamentType": 1,
                  "knockoutSlots": 2,
                  "qualifiersPerGroup": 1,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(0, tournament.getSportType());
        assertEquals(0, tournament.getParticipantType());

        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0));
        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0));
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0));
    }

    @Test
    void badmintonTeam_shouldCreateScheduleAndReturnTeamMembers() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(0, tournament.getSportType());
        assertEquals(1, tournament.getParticipantType());
        assertEquals(2, playerMapper.selectCount(new QueryWrapper<Player>().eq("tournament_id", tournamentId)));
        assertEquals(4, tournamentTeamMemberMapper.selectCount(new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId)));
        assertEquals(1, matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId)));

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sportType").value(0))
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].captainName").value("A Captain"))
                .andExpect(jsonPath("$.data.teams[0].members[0].name").value("A Captain"))
                .andExpect(jsonPath("$.data.teams[0].members[0].captain").value(true));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.matches.length()").value(1));
    }

    @Test
    void badmintonTeam_shouldRejectInvalidMembers() throws Exception {
        expectCreateFails(badmintonTeamWithMembers("[{\"name\": \"Only\", \"captain\": true}]"));
        expectCreateFails(badmintonTeamWithMembers("[{\"name\": \"A1\", \"captain\": false}, {\"name\": \"A2\", \"captain\": false}]"));
        expectCreateFails(badmintonTeamWithMembers("[{\"name\": \"A1\", \"captain\": true}, {\"name\": \"A2\", \"captain\": true}]"));
    }

    @Test
    void volleyballWithoutParticipantType_shouldStillBeTeamAndKeepVolleyballValidation() throws Exception {
        String tournamentId = createAndGetId(volleyballBody());

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getSportType());
        assertEquals(1, tournament.getParticipantType());

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1));

        expectCreateFails(volleyballBody().replace(
                "\"sportType\": 1,",
                "\"sportType\": 1,\n                  \"participantType\": 0,"));
    }

    @Test
    void individualWithTeamsAndTeamWithPlayers_shouldRejectIllegalPayloads() throws Exception {
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 0,
                  "name": "Bad payload",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}]},
                    {"name": "B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "name": "Bad payload",
                  "tournamentType": 0,
                  "players": [{"name": "P1"}, {"name": "P2"}],
                  "teams": [
                    {"name": "A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}]},
                    {"name": "B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
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

    private void expectCreateFails(String body) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private String badmintonTeamBody() {
        return """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "name": "Badminton team",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "Team A", "members": [
                      {"name": "A Member", "captain": false},
                      {"name": "A Captain", "captain": true}
                    ]},
                    {"name": "Team B", "members": [
                      {"name": "B Captain", "captain": true},
                      {"name": "B Member", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
    }

    private String badmintonTeamWithMembers(String firstTeamMembers) {
        return """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "name": "Badminton invalid team",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "Team A", "members": %s},
                    {"name": "Team B", "members": [
                      {"name": "B Captain", "captain": true},
                      {"name": "B Member", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """.formatted(firstTeamMembers);
    }

    private String volleyballBody() {
        return """
                {
                  "sportType": 1,
                  "name": "Volleyball team",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "VA", "members": [
                      {"name": "VA1", "jerseyNumber": 1, "captain": true},
                      {"name": "VA2", "jerseyNumber": 2, "captain": false},
                      {"name": "VA3", "jerseyNumber": 3, "captain": false},
                      {"name": "VA4", "jerseyNumber": 4, "captain": false},
                      {"name": "VA5", "jerseyNumber": 5, "captain": false},
                      {"name": "VA6", "jerseyNumber": 6, "captain": false}
                    ]},
                    {"name": "VB", "members": [
                      {"name": "VB1", "jerseyNumber": 1, "captain": true},
                      {"name": "VB2", "jerseyNumber": 2, "captain": false},
                      {"name": "VB3", "jerseyNumber": 3, "captain": false},
                      {"name": "VB4", "jerseyNumber": 4, "captain": false},
                      {"name": "VB5", "jerseyNumber": 5, "captain": false},
                      {"name": "VB6", "jerseyNumber": 6, "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """;
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