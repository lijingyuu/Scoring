package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
    private TeamMatchItemMapper teamMatchItemMapper;

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
        teamMatchItemMapper.delete(new QueryWrapper<>());
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
        assertEquals(0, tournament.getTeamMatchTemplate());

        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(0))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(0));
        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(0))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(0));
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(0))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(0));
    }

    @Test
    void badmintonTeam_shouldCreateScheduleAndReturnTeamMembers() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(0, tournament.getSportType());
        assertEquals(1, tournament.getParticipantType());
        assertEquals(1, tournament.getTeamMatchTemplate());
        assertEquals(2, playerMapper.selectCount(new QueryWrapper<Player>().eq("tournament_id", tournamentId)));
        assertEquals(4, tournamentTeamMemberMapper.selectCount(new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId)));
        assertEquals(1, matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId)));

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.sportType").value(0))
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(1))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(5))
                .andExpect(jsonPath("$.data.teamMatchItems[0].code").value("MS"))
                .andExpect(jsonPath("$.data.teamMatchItems[2].playerCount").value(2))
                .andExpect(jsonPath("$.data.teamMatchItems[4].code").value("XD"))
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].captainName").value("A Captain"))
                .andExpect(jsonPath("$.data.teams[0].members[0].name").value("A Captain"))
                .andExpect(jsonPath("$.data.teams[0].members[0].captain").value(true));

        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(1))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(5));

        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(1))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(5));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(1))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(5))
                .andExpect(jsonPath("$.data.matches.length()").value(1));
    }

    @Test
    void badmintonTeam_shouldAcceptExplicitSudirmanTemplateAndRejectReservedTemplates() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody().replace(
                "\"participantType\": 1,",
                "\"participantType\": 1,\n                  \"teamMatchTemplate\": 1,"));

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getTeamMatchTemplate());

        expectCreateFails(badmintonTeamBody().replace(
                "\"participantType\": 1,",
                "\"participantType\": 1,\n                  \"teamMatchTemplate\": 3,"));
    }

    @Test
    void badmintonRelay_shouldRejectInvalidPointsToWin() throws Exception {
        expectCreateFails(badmintonRelayBody().replace("\"pointsToWin\": 10", "\"pointsToWin\": 0"));
        expectCreateFails(badmintonRelayBody().replace("\"pointsToWin\": 10", "\"pointsToWin\": 150"));
    }

    @Test
    void badmintonRelay_shouldCreateCircularDoubleLineup() throws Exception {
        String tournamentId = createAndGetId(badmintonRelayBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        mockMvc.perform(get("/api/v1/matches/{id}/team-lineup", match.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(2))
                .andExpect(jsonPath("$.data.relayBaseScore").value(10))
                .andExpect(jsonPath("$.data.relayMemberCount").value(6))
                .andExpect(jsonPath("$.data.relayTargetScore").value(60))
                .andExpect(jsonPath("$.data.items.length()").value(0));

        List<TournamentTeamMember> leftMembers = membersByParticipant(tournamentId, match.getLeftPlayerId());
        List<TournamentTeamMember> rightMembers = membersByParticipant(tournamentId, match.getRightPlayerId());
        String body = """
                {
                  "items": [
                    {"itemCode": "R1", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R2", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R4", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R5", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R6", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), leftMembers.get(1).getId(), rightMembers.get(0).getId(), rightMembers.get(1).getId(),
                leftMembers.get(1).getId(), leftMembers.get(2).getId(), rightMembers.get(1).getId(), rightMembers.get(2).getId(),
                leftMembers.get(2).getId(), leftMembers.get(3).getId(), rightMembers.get(2).getId(), rightMembers.get(3).getId(),
                leftMembers.get(3).getId(), leftMembers.get(4).getId(), rightMembers.get(3).getId(), rightMembers.get(4).getId(),
                leftMembers.get(4).getId(), leftMembers.get(5).getId(), rightMembers.get(4).getId(), rightMembers.get(5).getId(),
                leftMembers.get(5).getId(), leftMembers.get(0).getId(), rightMembers.get(5).getId(), rightMembers.get(0).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.relayMemberCount").value(6))
                .andExpect(jsonPath("$.data.relayTargetScore").value(60))
                .andExpect(jsonPath("$.data.items.length()").value(6))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("R1"))
                .andExpect(jsonPath("$.data.items[5].leftMembers[1].name").value(leftMembers.get(0).getName()));

        assertEquals(6, teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>().eq("match_id", match.getId())));
    }

    @Test
    void badmintonRelay_shouldRejectWrongSegmentCount() throws Exception {
        String tournamentId = createAndGetId(badmintonRelayBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        // Submit only 3 segments when 6 are required
        List<TournamentTeamMember> leftMembers = membersByParticipant(tournamentId, match.getLeftPlayerId());
        List<TournamentTeamMember> rightMembers = membersByParticipant(tournamentId, match.getRightPlayerId());
        String body = """
                {
                  "items": [
                    {"itemCode": "R1", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R2", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), leftMembers.get(1).getId(), rightMembers.get(0).getId(), rightMembers.get(1).getId(),
                leftMembers.get(1).getId(), leftMembers.get(2).getId(), rightMembers.get(1).getId(), rightMembers.get(2).getId(),
                leftMembers.get(2).getId(), leftMembers.get(0).getId(), rightMembers.get(2).getId(), rightMembers.get(0).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void badmintonRelay_shouldRejectBrokenChain() throws Exception {
        String tournamentId = createAndGetId(badmintonRelayBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        List<TournamentTeamMember> leftMembers = membersByParticipant(tournamentId, match.getLeftPlayerId());
        List<TournamentTeamMember> rightMembers = membersByParticipant(tournamentId, match.getRightPlayerId());
        // R3 has [A3, A4] but R4 should start with A4. We break it: R3 has [A3, A5], R4 has [A4, A5]
        String body = """
                {
                  "items": [
                    {"itemCode": "R1", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R2", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R4", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R5", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R6", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), leftMembers.get(1).getId(), rightMembers.get(0).getId(), rightMembers.get(1).getId(),
                leftMembers.get(1).getId(), leftMembers.get(2).getId(), rightMembers.get(1).getId(), rightMembers.get(2).getId(),
                // R3: broken — second member A5 instead of A3
                leftMembers.get(2).getId(), leftMembers.get(4).getId(), rightMembers.get(2).getId(), rightMembers.get(4).getId(),
                leftMembers.get(3).getId(), leftMembers.get(4).getId(), rightMembers.get(3).getId(), rightMembers.get(4).getId(),
                leftMembers.get(4).getId(), leftMembers.get(5).getId(), rightMembers.get(4).getId(), rightMembers.get(5).getId(),
                leftMembers.get(5).getId(), leftMembers.get(0).getId(), rightMembers.get(5).getId(), rightMembers.get(0).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void badmintonRelay_shouldFinishAndPropagateWinner() throws Exception {
        String tournamentId = createAndGetId(badmintonRelayBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        // Save lineup first
        List<TournamentTeamMember> leftMembers = membersByParticipant(tournamentId, match.getLeftPlayerId());
        List<TournamentTeamMember> rightMembers = membersByParticipant(tournamentId, match.getRightPlayerId());
        String lineupBody = relayLineupBody6(leftMembers, rightMembers);
        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Finish the relay match
        mockMvc.perform(put("/api/v1/matches/{id}/finish", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "left",
                                  "leftScore": 60,
                                  "rightScore": 35,
                                  "leftGameWins": 1,
                                  "rightGameWins": 0,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": 60, "rightScore": 35, "winnerSide": "left"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Verify match is finished with correct score
        MatchRecord finished = matchRecordMapper.selectById(match.getId());
        assertEquals(2, finished.getStatus());
        assertEquals(match.getLeftPlayerId(), finished.getWinnerId());
        // scoreDisplay is from buildScoreDisplay: "60:35"
        assertEquals("60:35", finished.getScoreDisplay());
    }

    private String relayLineupBody6(List<TournamentTeamMember> left, List<TournamentTeamMember> right) {
        return """
                {
                  "items": [
                    {"itemCode": "R1", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R2", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R4", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R5", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "R6", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                left.get(0).getId(), left.get(1).getId(), right.get(0).getId(), right.get(1).getId(),
                left.get(1).getId(), left.get(2).getId(), right.get(1).getId(), right.get(2).getId(),
                left.get(2).getId(), left.get(3).getId(), right.get(2).getId(), right.get(3).getId(),
                left.get(3).getId(), left.get(4).getId(), right.get(3).getId(), right.get(4).getId(),
                left.get(4).getId(), left.get(5).getId(), right.get(4).getId(), right.get(5).getId(),
                left.get(5).getId(), left.get(0).getId(), right.get(5).getId(), right.get(0).getId()
        );
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


    @Test
    void badmintonTeamLineup_shouldLoadAndSaveSudirmanItems() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        mockMvc.perform(get("/api/v1/matches/{id}/team-lineup", match.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(1))
                .andExpect(jsonPath("$.data.matchStatus").value(0))
                .andExpect(jsonPath("$.data.stageType").value(1))
                .andExpect(jsonPath("$.data.tournamentType").value(0))
                .andExpect(jsonPath("$.data.leftTeam.members.length()").value(2))
                .andExpect(jsonPath("$.data.items.length()").value(5))
                .andExpect(jsonPath("$.data.items[0].itemCode").value("MS"))
                .andExpect(jsonPath("$.data.items[0].leftMembers.length()").value(0));

        TournamentTeamMember leftCaptainMember = memberByCaptain(tournamentId, match.getLeftPlayerId(), true);
        TournamentTeamMember leftRegularMember = memberByCaptain(tournamentId, match.getLeftPlayerId(), false);
        TournamentTeamMember rightCaptainMember = memberByCaptain(tournamentId, match.getRightPlayerId(), true);
        TournamentTeamMember rightRegularMember = memberByCaptain(tournamentId, match.getRightPlayerId(), false);
        String leftCaptain = leftCaptainMember.getId();
        String leftMember = leftRegularMember.getId();
        String rightCaptain = rightCaptainMember.getId();
        String rightMember = rightRegularMember.getId();

        String body = """
                {
                  "items": [
                    {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "MD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "WD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "XD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftCaptain, rightCaptain,
                leftMember, rightMember,
                leftCaptain, leftMember, rightCaptain, rightMember,
                leftMember, leftCaptain, rightMember, rightCaptain,
                leftCaptain, leftMember, rightCaptain, rightMember
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].leftMembers[0].name").value(leftCaptainMember.getName()))
                .andExpect(jsonPath("$.data.items[2].leftMembers.length()").value(2));

        assertEquals(5, teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>().eq("match_id", match.getId())));

        TeamMatchItem savedMs = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", match.getId())
                .eq("item_code", "MS"));
        assertNotNull(savedMs);
        savedMs.setChildMatchId("child-match-1");
        savedMs.setStatus(2);
        savedMs.setWinnerSide("left");
        teamMatchItemMapper.updateById(savedMs);

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].childMatchId").value("child-match-1"))
                .andExpect(jsonPath("$.data.items[0].status").value(2))
                .andExpect(jsonPath("$.data.items[0].winnerSide").value("left"));

        TeamMatchItem updatedMs = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", match.getId())
                .eq("item_code", "MS"));
        assertEquals(savedMs.getId(), updatedMs.getId());
        assertEquals("child-match-1", updatedMs.getChildMatchId());
        assertEquals(2, updatedMs.getStatus());
        assertEquals("left", updatedMs.getWinnerSide());
    }


    @Test
    void badmintonTeamChildMatch_shouldStartAndFinishWithoutEndingTournament() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);

        TournamentTeamMember leftCaptainMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), true);
        TournamentTeamMember leftRegularMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), false);
        TournamentTeamMember rightCaptainMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), true);
        TournamentTeamMember rightRegularMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), false);
        String body = sudirmanLineupBody(
                leftCaptainMember.getId(), leftRegularMember.getId(),
                rightCaptainMember.getId(), rightRegularMember.getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String startResponse = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatch.getId(), "MS")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemCode").value("MS"))
                .andExpect(jsonPath("$.data.leftName").value(leftCaptainMember.getName()))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String childMatchId = objectMapper.readTree(startResponse).path("data").path("childMatchId").asText();
        assertFalse(childMatchId.isBlank());

        MatchRecord childMatch = matchRecordMapper.selectById(childMatchId);
        assertNotNull(childMatch);
        assertEquals(parentMatch.getLeftPlayerId(), childMatch.getLeftPlayerId());
        assertEquals(parentMatch.getRightPlayerId(), childMatch.getRightPlayerId());

        mockMvc.perform(put("/api/v1/matches/{id}/finish", childMatchId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "left",
                                  "leftScore": 0,
                                  "rightScore": 0,
                                  "leftGameWins": 2,
                                  "rightGameWins": 0,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": 21, "rightScore": 10, "winnerSide": "left"},
                                    {"gameNo": 2, "leftScore": 21, "rightScore": 12, "winnerSide": "left"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/team-lineup", parentMatch.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].status").value(2))
                .andExpect(jsonPath("$.data.items[0].winnerSide").value("left"))
                .andExpect(jsonPath("$.data.items[0].childMatchId").value(childMatchId))
                .andExpect(jsonPath("$.data.items[0].childScoreDisplay").value("21:10, 21:12"));

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getStatus());
    }

    @Test
    void badmintonTeamChildMatches_shouldSettleParentOnlyAfterExplicitKnockoutDecision() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);

        TournamentTeamMember leftCaptainMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), true);
        TournamentTeamMember leftRegularMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), false);
        TournamentTeamMember rightCaptainMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), true);
        TournamentTeamMember rightRegularMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), false);
        String body = sudirmanLineupBody(
                leftCaptainMember.getId(), leftRegularMember.getId(),
                rightCaptainMember.getId(), rightRegularMember.getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        finishTeamItem(parentMatch.getId(), "MS", "left");
        MatchRecord parentAfterOne = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, parentAfterOne.getStatus());

        finishTeamItem(parentMatch.getId(), "WS", "left");
        finishTeamItem(parentMatch.getId(), "MD", "left");

        MatchRecord parentAtThreeWins = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, parentAtThreeWins.getStatus());
        assertNull(parentAtThreeWins.getWinnerId());

        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord finishedParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, finishedParent.getStatus());
        assertEquals(parentMatch.getLeftPlayerId(), finishedParent.getWinnerId());
        assertEquals("3:0", finishedParent.getScoreDisplay());
        assertEquals(3, finishedParent.getLeftGameWins());
        assertEquals(0, finishedParent.getRightGameWins());

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(2, tournament.getStatus());
    }

    @Test
    void badmintonTeamLineup_shouldRejectMissingItemAndWrongSideMember() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        String leftCaptain = memberByCaptain(tournamentId, match.getLeftPlayerId(), true).getId();
        String rightCaptain = memberByCaptain(tournamentId, match.getRightPlayerId(), true).getId();

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]}
                                ]}
                                """.formatted(leftCaptain, rightCaptain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                                  {"itemCode": "WS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                                  {"itemCode": "MD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                                  {"itemCode": "WD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                                  {"itemCode": "XD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                                ]}
                                """.formatted(
                                rightCaptain, rightCaptain,
                                leftCaptain, rightCaptain,
                                leftCaptain, leftCaptain, rightCaptain, rightCaptain,
                                leftCaptain, leftCaptain, rightCaptain, rightCaptain,
                                leftCaptain, leftCaptain, rightCaptain, rightCaptain
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]}
                                ]}
                                """.formatted(leftCaptain, rightCaptain, leftCaptain, rightCaptain)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }


    private void finishTeamItem(String parentMatchId, String itemCode, String winnerSide) throws Exception {
        String startResponse = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatchId, itemCode)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        String childMatchId = objectMapper.readTree(startResponse).path("data").path("childMatchId").asText();
        assertFalse(childMatchId.isBlank());

        int leftWins = "left".equals(winnerSide) ? 2 : 0;
        int rightWins = "right".equals(winnerSide) ? 2 : 0;
        String firstGameWinner = winnerSide;
        String secondGameWinner = winnerSide;
        int firstLeft = "left".equals(winnerSide) ? 21 : 10;
        int firstRight = "left".equals(winnerSide) ? 10 : 21;
        int secondLeft = "left".equals(winnerSide) ? 21 : 12;
        int secondRight = "left".equals(winnerSide) ? 12 : 21;

        mockMvc.perform(put("/api/v1/matches/{id}/finish", childMatchId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  \"winnerSide\": \"%s\",
                                  \"leftScore\": 0,
                                  \"rightScore\": 0,
                                  \"leftGameWins\": %d,
                                  \"rightGameWins\": %d,
                                  \"gameScores\": [
                                    {\"gameNo\": 1, \"leftScore\": %d, \"rightScore\": %d, \"winnerSide\": \"%s\"},
                                    {\"gameNo\": 2, \"leftScore\": %d, \"rightScore\": %d, \"winnerSide\": \"%s\"}
                                  ]
                                }
                                """.formatted(winnerSide, leftWins, rightWins, firstLeft, firstRight, firstGameWinner, secondLeft, secondRight, secondGameWinner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String sudirmanLineupBody(String leftCaptain, String leftMember, String rightCaptain, String rightMember) {
        return """
                {
                  "items": [
                    {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "MD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "WD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "XD", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftCaptain, rightCaptain,
                leftMember, rightMember,
                leftCaptain, leftMember, rightCaptain, rightMember,
                leftMember, leftCaptain, rightMember, rightCaptain,
                leftCaptain, leftMember, rightCaptain, rightMember
        );
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

    private String badmintonRelayBody() {
        return """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 2,
                  "name": "Badminton relay",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "Team A", "members": [
                      {"name": "A1", "captain": true},
                      {"name": "A2", "captain": false},
                      {"name": "A3", "captain": false},
                      {"name": "A4", "captain": false},
                      {"name": "A5", "captain": false},
                      {"name": "A6", "captain": false}
                    ]},
                    {"name": "Team B", "members": [
                      {"name": "B1", "captain": true},
                      {"name": "B2", "captain": false},
                      {"name": "B3", "captain": false},
                      {"name": "B4", "captain": false},
                      {"name": "B5", "captain": false},
                      {"name": "B6", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 10, "enableDeuce": false, "capPoint": 6}
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


    private TournamentTeamMember memberByCaptain(String tournamentId, String participantId, boolean captain) {
        TournamentTeamMember member = tournamentTeamMemberMapper.selectOne(new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournamentId)
                .eq("participant_id", participantId)
                .eq("is_captain", captain));
        assertNotNull(member);
        return member;
    }

    private List<TournamentTeamMember> membersByParticipant(String tournamentId, String participantId) {
        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournamentId)
                .eq("participant_id", participantId)
                .orderByAsc("display_order", "id"));
        assertEquals(6, members.size());
        return members;
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
