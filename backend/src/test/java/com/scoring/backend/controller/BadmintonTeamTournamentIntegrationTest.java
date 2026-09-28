package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentCustomItem;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentCustomItemMapper;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static com.scoring.backend.controller.MatchLockTestSupport.withMatchLock;

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
    private com.scoring.backend.mapper.TournamentDivisionMapper tournamentDivisionMapper;

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
    private MatchReportMetaMapper matchReportMetaMapper;

    @Autowired
    private TournamentTeamMemberMapper tournamentTeamMemberMapper;

    @Autowired
    private TeamMatchItemMapper teamMatchItemMapper;

    @Autowired
    private TournamentCustomItemMapper tournamentCustomItemMapper;

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
        tournamentCustomItemMapper.delete(new QueryWrapper<>());
        teamMatchItemMapper.delete(new QueryWrapper<>());
        matchReportMetaMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
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
    void badmintonTeamReportSignatures_shouldPersistAndRejectOverwrite() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        grantReferee(tournamentId, "user-1");
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);
        match.setStatus(2);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamLeftCaptainSignature": "data:image/png;base64,left-a",
                                  "teamMatchDateText": "2026-07-28"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/team-lineup", match.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportSignatures.leftCaptain").value("data:image/png;base64,left-a"))
                .andExpect(jsonPath("$.data.reportSignatures.matchDateText").value("2026-07-28"));

        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamLeftCaptainSignature": "data:image/png;base64,left-b"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("战报签名已确认，不能修改"));
    }

    @Test
    void badmintonTeamReport_shouldSealOnlyAfterFinishedAndComplete() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        grantReferee(tournamentId, "user-1");
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        mockMvc.perform(put("/api/v1/matches/{id}/report-seal", match.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("战报只能在比赛结束后封存"));

        match.setStatus(2);
        matchRecordMapper.updateById(match);

        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamLeftCaptainSignature": "data:image/png;base64,left-a",
                                  "teamMatchDateText": "2026-07-28"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/report-seal", match.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("战报签名和日期未填写完整"));

        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamRightCaptainSignature": "data:image/png;base64,right-a",
                                  "teamRefereeSignature": "data:image/png;base64,referee-a"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/report-seal", match.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/team-lineup", match.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportState.status").value("sealed"))
                .andExpect(jsonPath("$.data.reportSignatures.leftParticipant").value("data:image/png;base64,left-a"))
                .andExpect(jsonPath("$.data.reportSignatures.rightParticipant").value("data:image/png;base64,right-a"))
                .andExpect(jsonPath("$.data.reportSignatures.referee").value("data:image/png;base64,referee-a"));

        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "teamRefereeSignature": "data:image/png;base64,referee-b"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("战报已封存，不能修改"));
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
                "\"participantType\": 1,\n                  \"teamMatchTemplate\": 4,"));
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
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
    void legacyRelayMetadata_shouldStillReturnTeamAndRelayTypes() throws Exception {
        String tournamentId = createAndGetId(badmintonRelayBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        TeamMatchItem relayItem = new TeamMatchItem();
        relayItem.setId("legacy-relay-r1");
        relayItem.setTournamentId(tournamentId);
        relayItem.setMatchId(match.getId());
        relayItem.setDisplayOrder(1);
        relayItem.setItemCode("R1");
        relayItem.setItemName("第 1 段");
        relayItem.setPlayerCount(2);
        relayItem.setStatus(2);
        teamMatchItemMapper.insert(relayItem);

        Tournament legacy = tournamentMapper.selectById(tournamentId);
        assertNotNull(legacy);
        legacy.setParticipantType(0);
        legacy.setTeamMatchTemplate(0);
        tournamentMapper.updateById(legacy);

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(1))
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(2));
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Finish the relay match
        mockMvc.perform(put("/api/v1/matches/{id}/finish", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
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
                                  ],
                                  "relaySegmentScores": [
                                    {"gameNo": 1, "leftScore": 10, "rightScore": 3, "winnerSide": "left"},
                                    {"gameNo": 2, "leftScore": 20, "rightScore": 9, "winnerSide": "left"},
                                    {"gameNo": 3, "leftScore": 30, "rightScore": 15, "winnerSide": "left"},
                                    {"gameNo": 4, "leftScore": 40, "rightScore": 22, "winnerSide": "left"},
                                    {"gameNo": 5, "leftScore": 50, "rightScore": 28, "winnerSide": "left"},
                                    {"gameNo": 6, "leftScore": 60, "rightScore": 35, "winnerSide": "left"}
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

        mockMvc.perform(get("/api/v1/matches/{id}/record", match.getId()).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.gameScores.length()").value(6))
                .andExpect(jsonPath("$.data.gameScores[0].leftScore").value(10))
                .andExpect(jsonPath("$.data.gameScores[5].leftScore").value(60))
                .andExpect(jsonPath("$.data.gameScores[5].rightScore").value(35));
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
    void badmintonTeam_shouldAcceptLargeTeamBeyondOldLimit() throws Exception {
        // 原规则限制最多12人；现在取消上限，15人队伍应正常创建
        String body = badmintonTeamWithMembers(buildMemberArray(15, 0));
        String tournamentId = createAndGetId(body);
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getParticipantType());

        long memberCount = tournamentTeamMemberMapper.selectCount(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId));
        assertEquals(15 + 2, memberCount); // Team A: 15, Team B: 2
    }

    @Test
    void volleyballTeam_shouldAcceptLargeTeamBeyondOldLimit() throws Exception {
        // 原规则限制最多12人；现在取消上限，15人队伍应正常创建
        String body = """
                {
                  "sportType": 1,
                  "name": "Large volleyball team",
                  "tournamentType": 0,
                  "teams": [
                    {"name": "VA", "members": %s},
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
                """.formatted(buildVolleyballMemberArray(15, 1));
        String tournamentId = createAndGetId(body);
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getSportType());

        long memberCount = tournamentTeamMemberMapper.selectCount(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId));
        assertEquals(15 + 6, memberCount); // Team VA: 15, Team VB: 6
    }

    private String buildMemberArray(int count, int captainIndex) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("{\"name\": \"A").append(i + 1).append("\", \"captain\": ")
              .append(i == captainIndex).append("}");
        }
        sb.append("]");
        return sb.toString();
    }

    private String buildVolleyballMemberArray(int count, int startJersey) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(", ");
            sb.append("{\"name\": \"VA").append(i + 1).append("\", \"jerseyNumber\": ")
              .append(startJersey + i).append(", \"captain\": ").append(i == 0).append("}");
        }
        sb.append("]");
        return sb.toString();
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
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
                        .with(withMatchLock(matchRecordMapper, match.getId()))
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
    void badmintonTeamLineup_shouldRejectMemberChangeOnStartedItemButAllowReplay() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(match);

        String leftCaptain = memberByCaptain(tournamentId, match.getLeftPlayerId(), true).getId();
        String leftMember = memberByCaptain(tournamentId, match.getLeftPlayerId(), false).getId();
        String rightCaptain = memberByCaptain(tournamentId, match.getRightPlayerId(), true).getId();
        String rightMember = memberByCaptain(tournamentId, match.getRightPlayerId(), false).getId();
        String body = sudirmanLineupBody(leftCaptain, leftMember, rightCaptain, rightMember);

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // MS 已开始（status=1）
        TeamMatchItem savedMs = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", match.getId())
                .eq("item_code", "MS"));
        savedMs.setStatus(1);
        teamMatchItemMapper.updateById(savedMs);

        // 名单完全相同的重复提交仍放行（前端重放不误伤）
        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 换掉 MS 的出场成员：整批拒绝
        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sudirmanLineupBody(leftMember, leftCaptain, rightCaptain, rightMember)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("单项已开始或已结束，不能修改出场名单"));

        TeamMatchItem afterReject = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", match.getId())
                .eq("item_code", "MS"));
        assertEquals(savedMs.getLeftMemberIdsJson(), afterReject.getLeftMemberIdsJson());
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
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String startResponse = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatch.getId(), "MS")
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
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
                        .with(withMatchLock(matchRecordMapper, childMatchId))
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
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
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
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
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


    /**
     * 父子场锁序与子项锁定读修复回归：两个子场并发完赛（最后一项 + 倒数第二项）。
     * 修复前：子场完赛锁序为 子场→父场，与父场重开（父→子）形成 AB-BA 交叉；且子项统计
     * 走事务早期快照，RR 下两个并发完赛互相看不到对方提交的 winner_side → 父场漏结算。
     * 修复后统一 父场→子场 锁序 + countTeamMatchScore 锁定读：最后完赛的一方必须结算父场。
     * 注：H2 为 READ_COMMITTED，本用例在 CI 验证"不死锁 + 不漏结算 + 恰好一次"；
     * RR 快照语义由生产 MySQL 上的统一锁序与锁定读保证。
     */
    @Test
    void badmintonTeamChildMatches_concurrentLastTwoItems_shouldSettleParentExactlyOnce() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);

        TournamentTeamMember leftCaptainMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), true);
        TournamentTeamMember leftRegularMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), false);
        TournamentTeamMember rightCaptainMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), true);
        TournamentTeamMember rightRegularMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), false);
        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sudirmanLineupBody(
                                leftCaptainMember.getId(), leftRegularMember.getId(),
                                rightCaptainMember.getId(), rightRegularMember.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 前 3 项顺序完赛（left 2 : right 1），剩余 WD/XD 两项由两线程对齐起点并发完赛
        finishTeamItem(parentMatch.getId(), "MS", "left");
        finishTeamItem(parentMatch.getId(), "WS", "right");
        finishTeamItem(parentMatch.getId(), "MD", "left");

        runConcurrentFinish(parentMatch.getId(), "WD", "left", "XD", "left");

        MatchRecord parent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, parent.getStatus(), "并发完赛最后两项后父场必须自动结算");
        assertEquals(parentMatch.getLeftPlayerId(), parent.getWinnerId());
        assertEquals("4:1", parent.getScoreDisplay());
        assertEquals(4, parent.getLeftGameWins());
        assertEquals(1, parent.getRightGameWins());
    }

    /** 两线程同时对齐起点后各自完赛一个子项；任一线程失败都会聚合抛出到主线程 */
    private void runConcurrentFinish(String parentMatchId, String itemCodeA, String winnerA,
                                      String itemCodeB, String winnerB) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Void>> futures = List.of(
                    pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        finishTeamItem(parentMatchId, itemCodeA, winnerA);
                        return null;
                    }),
                    pool.submit(() -> {
                        ready.countDown();
                        start.await();
                        finishTeamItem(parentMatchId, itemCodeB, winnerB);
                        return null;
                    })
            );
            ready.await();
            start.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * 审查 §5.4-①：团体父场重开的级联清场必须尊重子场上的有效执裁锁——子场正被他人
     * 执裁时拒绝重开（防下游设备防抖窗口内的幽灵事件），锁过期后同一重开放行。
     * 覆盖 resetChildMatchForRestart 的 requireDownstreamUnlocked 分支（此前零测试）。
     */
    @Test
    void teamParentRestart_shouldRejectWhileChildMatchLocked_thenSucceedAfterExpiry() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);
        saveSudirmanLineup(tournamentId, parentMatch);

        // 开出 MS 子场，并在其上放置另一会话的有效锁
        String startResponse = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatch.getId(), "MS")
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        String childMatchId = objectMapper.readTree(startResponse).path("data").path("childMatchId").asText();
        assertFalse(childMatchId.isBlank());

        MatchRecord childLock = new MatchRecord();
        childLock.setId(childMatchId);
        childLock.setLockedByUserId("user-other");
        childLock.setLockToken("lock-other-session");
        childLock.setLockExpireTime(LocalDateTime.now().plusMinutes(10));
        matchRecordMapper.updateById(childLock);

        // 子场正被执裁：父场重开必须被拒且无副作用
        String rejected = mockMvc.perform(put("/api/v1/matches/{id}/restart", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        org.junit.jupiter.api.Assertions.assertTrue(rejected.contains("正在被执裁"),
                "拒绝理由应说明下游正被执裁: " + rejected);
        assertEquals(0, matchRecordMapper.selectById(parentMatch.getId()).getStatus(), "拒绝后父场状态不得变化");

        // 子场锁过期：同一重开放行，子项与子场全部重置
        MatchRecord expire = new MatchRecord();
        expire.setId(childMatchId);
        expire.setLockExpireTime(LocalDateTime.now().minusMinutes(1));
        matchRecordMapper.updateById(expire);

        mockMvc.perform(put("/api/v1/matches/{id}/restart", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        TeamMatchItem msItem = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", parentMatch.getId()).eq("item_code", "MS"));
        assertEquals(0, msItem.getStatus());
        assertNull(msItem.getWinnerSide());
        MatchRecord resetChild = matchRecordMapper.selectById(childMatchId);
        assertEquals(0, resetChild.getStatus());
        assertNull(resetChild.getWinnerId());
    }
    @Test
    void badmintonTeamParentRestart_shouldClearAllItemsAndChildrenThenSettleWithNewResult() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);
        saveSudirmanLineup(tournamentId, parentMatch);

        finishTeamItem(parentMatch.getId(), "MS", "left");
        finishTeamItem(parentMatch.getId(), "WS", "left");
        finishTeamItem(parentMatch.getId(), "MD", "left");
        TeamMatchItem msBeforeRestart = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", parentMatch.getId())
                .eq("item_code", "MS"));
        assertEquals(2, msBeforeRestart.getStatus());
        assertEquals("left", msBeforeRestart.getWinnerSide());
        String oldChildMatchId = msBeforeRestart.getChildMatchId();
        assertNotNull(oldChildMatchId);

        // 先结算父场：左队 3:0 提前夺冠，赛事完赛
        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        MatchRecord settledParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, settledParent.getStatus());
        assertEquals(parentMatch.getLeftPlayerId(), settledParent.getWinnerId());
        assertEquals("3:0", settledParent.getScoreDisplay());
        assertEquals(2, tournamentMapper.selectById(tournamentId).getStatus());

        // P1-6：重开团体父场 = 全部重来
        mockMvc.perform(put("/api/v1/matches/{id}/restart", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<TeamMatchItem> itemsAfterRestart = teamMatchItemMapper.selectList(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", parentMatch.getId()));
        assertEquals(5, itemsAfterRestart.size());
        for (TeamMatchItem item : itemsAfterRestart) {
            assertEquals(0, item.getStatus());
            assertNull(item.getWinnerSide());
        }
        TeamMatchItem msAfterRestart = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", parentMatch.getId())
                .eq("item_code", "MS"));
        // 设计选择：保留 child_match_id（组别场次统计靠它排除子场），但子场行本身已重置
        assertEquals(oldChildMatchId, msAfterRestart.getChildMatchId());
        MatchRecord restartedParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, restartedParent.getStatus());
        assertNull(restartedParent.getWinnerId());
        MatchRecord resetChild = matchRecordMapper.selectById(oldChildMatchId);
        assertEquals(0, resetChild.getStatus());
        assertNull(resetChild.getWinnerId());
        assertNull(resetChild.getScoreDisplay());
        assertNull(resetChild.getGameScores());

        // 重打：这次右队 3 项取胜，结算必须按新结果而不是旧冠军
        finishTeamItem(parentMatch.getId(), "MS", "right");
        finishTeamItem(parentMatch.getId(), "WS", "right");
        finishTeamItem(parentMatch.getId(), "MD", "right");
        TeamMatchItem msAfterReplay = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", parentMatch.getId())
                .eq("item_code", "MS"));
        assertEquals(2, msAfterReplay.getStatus());
        assertEquals("right", msAfterReplay.getWinnerSide());
        assertEquals(oldChildMatchId, msAfterReplay.getChildMatchId());

        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord reSettledParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, reSettledParent.getStatus());
        assertEquals(parentMatch.getRightPlayerId(), reSettledParent.getWinnerId());
        assertEquals("0:3", reSettledParent.getScoreDisplay());
        assertEquals(2, tournamentMapper.selectById(tournamentId).getStatus());
    }

    @Test
    void badmintonTeamRoundRobinRestart_shouldResetChildrenAndStillFinishDivision() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody().replace("\"tournamentType\": 0,", "\"tournamentType\": 2,"));
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);
        saveSudirmanLineup(tournamentId, parentMatch);

        finishTeamItem(parentMatch.getId(), "MS", "left");
        finishTeamItem(parentMatch.getId(), "WS", "left");
        finishTeamItem(parentMatch.getId(), "MD", "left");
        finishTeamItem(parentMatch.getId(), "WD", "left");
        finishTeamItem(parentMatch.getId(), "XD", "right");
        MatchRecord settledParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, settledParent.getStatus());
        assertEquals("4:1", settledParent.getScoreDisplay());
        assertEquals(2, tournamentMapper.selectById(tournamentId).getStatus());

        mockMvc.perform(put("/api/v1/matches/{id}/restart", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        MatchRecord restartedParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, restartedParent.getStatus());
        assertNull(restartedParent.getWinnerId());
        assertEquals(1, tournamentMapper.selectById(tournamentId).getStatus());

        // 全部重打：右队 3 项取胜；子场保留 child_match_id 才不会被算作组别未完成场次
        finishTeamItem(parentMatch.getId(), "MS", "right");
        finishTeamItem(parentMatch.getId(), "WS", "right");
        finishTeamItem(parentMatch.getId(), "MD", "right");
        finishTeamItem(parentMatch.getId(), "WD", "left");
        finishTeamItem(parentMatch.getId(), "XD", "left");

        MatchRecord reSettledParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, reSettledParent.getStatus());
        assertEquals(parentMatch.getRightPlayerId(), reSettledParent.getWinnerId());
        assertEquals("2:3", reSettledParent.getScoreDisplay());
        assertEquals(2, tournamentMapper.selectById(tournamentId).getStatus());
    }

    @Test
    void badmintonTeamRoundRobin_shouldRequireAllItemsBeforeSettlement() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody().replace("\"tournamentType\": 0,", "\"tournamentType\": 2,"));
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);

        TournamentTeamMember leftCaptainMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), true);
        TournamentTeamMember leftRegularMember = memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), false);
        TournamentTeamMember rightCaptainMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), true);
        TournamentTeamMember rightRegularMember = memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), false);

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sudirmanLineupBody(
                                leftCaptainMember.getId(), leftRegularMember.getId(),
                                rightCaptainMember.getId(), rightRegularMember.getId()
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        finishTeamItem(parentMatch.getId(), "MS", "left");
        finishTeamItem(parentMatch.getId(), "WS", "left");
        finishTeamItem(parentMatch.getId(), "MD", "left");

        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        MatchRecord parentAtThreeWins = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, parentAtThreeWins.getStatus());
        assertNull(parentAtThreeWins.getWinnerId());

        finishTeamItem(parentMatch.getId(), "WD", "left");
        finishTeamItem(parentMatch.getId(), "XD", "right");

        MatchRecord finishedParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, finishedParent.getStatus());
        assertEquals(parentMatch.getLeftPlayerId(), finishedParent.getWinnerId());
        assertEquals("4:1", finishedParent.getScoreDisplay());
    }

    @Test
    void badmintonTeamLineup_shouldRejectMissingItemAndWrongSideMember() throws Exception {
        String tournamentId = createAndGetId(badmintonTeamBody());
        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        String leftCaptain = memberByCaptain(tournamentId, match.getLeftPlayerId(), true).getId();
        String rightCaptain = memberByCaptain(tournamentId, match.getRightPlayerId(), true).getId();

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]}
                                ]}
                                """.formatted(leftCaptain, rightCaptain)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
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
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                                  {"itemCode": "MS", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]}
                                ]}
                                """.formatted(leftCaptain, rightCaptain, leftCaptain, rightCaptain)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }


    /** 保存标准苏杯 5 项布阵（队长/普通队员各一名） */
    private void saveSudirmanLineup(String tournamentId, MatchRecord parentMatch) throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(sudirmanLineupBody(
                                memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), true).getId(),
                                memberByCaptain(tournamentId, parentMatch.getLeftPlayerId(), false).getId(),
                                memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), true).getId(),
                                memberByCaptain(tournamentId, parentMatch.getRightPlayerId(), false).getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private void finishTeamItem(String parentMatchId, String itemCode, String winnerSide) throws Exception {
        String startResponse = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatchId, itemCode)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatchId)))
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
                        .with(withMatchLock(matchRecordMapper, childMatchId))
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
                .andExpect(status().isBadRequest())
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

    private void grantReferee(String tournamentId, String userId) {
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(tournamentId);
        grant.setUserId(userId);
        tournamentRefereeGrantMapper.insert(grant);
    }

    @Test
    void badmintonCustomTeam_shouldCreateWithCustomItemsAndReturnInVos() throws Exception {
        String body = """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Custom 3-item Team Match",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [
                      {"name": "A1", "captain": true},
                      {"name": "A2", "captain": false},
                      {"name": "A3", "captain": false}
                    ]},
                    {"name": "Team B", "members": [
                      {"name": "B1", "captain": true},
                      {"name": "B2", "captain": false},
                      {"name": "B3", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
        String tournamentId = createAndGetId(body);
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(3, tournament.getTeamMatchTemplate());

        // 验证 tournament_custom_items 表已持久化 3 项
        List<TournamentCustomItem> customItems = tournamentCustomItemMapper.selectList(
                new QueryWrapper<TournamentCustomItem>().eq("tournament_id", tournamentId).orderByAsc("display_order"));
        assertEquals(3, customItems.size());
        assertEquals("MS_1", customItems.get(0).getItemCode());
        assertEquals(1, customItems.get(0).getPlayerCount());
        assertEquals("WS_2", customItems.get(1).getItemCode());
        assertEquals(1, customItems.get(1).getPlayerCount());
        assertEquals("XD_3", customItems.get(2).getItemCode());
        assertEquals(2, customItems.get(2).getPlayerCount());

        // 验证详情、分组、对阵图、队伍接口均返回该 3 项 VO
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(3))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(3))
                .andExpect(jsonPath("$.data.teamMatchItems[0].code").value("MS_1"))
                .andExpect(jsonPath("$.data.teamMatchItems[0].playerCount").value(1))
                .andExpect(jsonPath("$.data.teamMatchItems[1].code").value("WS_2"))
                .andExpect(jsonPath("$.data.teamMatchItems[2].code").value("XD_3"))
                .andExpect(jsonPath("$.data.teamMatchItems[2].playerCount").value(2));

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(3))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(3));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamMatchTemplate").value(3))
                .andExpect(jsonPath("$.data.teamMatchItems.length()").value(3));
    }

    @Test
    void badmintonCustomTeam_lineupAndEarlyKnockoutSettlement() throws Exception {
        String body = """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Custom 3-item Knockout Early Settle",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team Alpha", "members": [
                      {"name": "Alpha 1", "captain": true},
                      {"name": "Alpha 2", "captain": false},
                      {"name": "Alpha 3", "captain": false}
                    ]},
                    {"name": "Team Beta", "members": [
                      {"name": "Beta 1", "captain": true},
                      {"name": "Beta 2", "captain": false},
                      {"name": "Beta 3", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
        String tournamentId = createAndGetId(body);
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertNotNull(parentMatch);

        List<TournamentTeamMember> leftMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getLeftPlayerId()));
        List<TournamentTeamMember> rightMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getRightPlayerId()));

        // 排阵 3 项
        String lineupBody = """
                {
                  "items": [
                    {"itemCode": "MS_1", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS_2", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "XD_3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), rightMembers.get(0).getId(),
                leftMembers.get(1).getId(), rightMembers.get(1).getId(),
                leftMembers.get(0).getId(), leftMembers.get(2).getId(),
                rightMembers.get(0).getId(), rightMembers.get(2).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.items.length()").value(3));

        // 启动第 1 项（MS_1）并完赛：Left 胜
        String start1Resp = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatch.getId(), "MS_1")
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemCode").value("MS_1"))
                .andReturn().getResponse().getContentAsString();
        String child1Id = objectMapper.readTree(start1Resp).path("data").path("childMatchId").asText();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", child1Id)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, child1Id))
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
                .andExpect(status().isOk());

        // 此时 1:0，还未达到 3项赛的 2胜门槛，尝试提前结算应失败
        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // 启动第 2 项（WS_2）并完赛：Left 胜，比分变为 2:0
        String start2Resp = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatch.getId(), "WS_2")
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.itemCode").value("WS_2"))
                .andReturn().getResponse().getContentAsString();
        String child2Id = objectMapper.readTree(start2Resp).path("data").path("childMatchId").asText();

        mockMvc.perform(put("/api/v1/matches/{id}/finish", child2Id)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, child2Id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "left",
                                  "leftScore": 0,
                                  "rightScore": 0,
                                  "leftGameWins": 2,
                                  "rightGameWins": 0,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": 21, "rightScore": 15, "winnerSide": "left"},
                                    {"gameNo": 2, "leftScore": 21, "rightScore": 18, "winnerSide": "left"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk());

        // 达到 2 胜（大于等于 ceil(3/2)=2），提前结算成功！
        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord settledParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, settledParent.getStatus()); // 2 = 已结束
        assertEquals(parentMatch.getLeftPlayerId(), settledParent.getWinnerId());
        assertEquals(2, settledParent.getLeftGameWins());
        assertEquals(0, settledParent.getRightGameWins());
    }

    @Test
    void badmintonCustomTeam_validationFailures() throws Exception {
        // 1. 项数不是 3/5/7（例如 4 项）
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Invalid items count",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "MD"},
                    {"displayOrder": 4, "itemType": "WD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);

        // 2. 自定义项为空
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Empty items",
                  "tournamentType": 0,
                  "customItems": [],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);

        // 3. 不支持的项目类型（例如 "INVALID"）
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Invalid item type",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "INVALID"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);

        // 4. 自定义多项团体赛队伍少于 3 人（例如仅 2 人）
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Too few members",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);

        // 5. 包含空/空白类型的子项（严禁静默丢弃）
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Blank item type",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "  "},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);

        // 6. 非自定义多项模板传 customItems 报错
        expectCreateFails("""
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 1,
                  "name": "Sudirman with customItems",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2}
                }
                """);
    }

    @Test
    void badmintonCustomTeam_7Items_shouldRequire4WinsAndDeduplicateNames() throws Exception {
        String body = """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Custom 7-item Team Match",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "MD"},
                    {"displayOrder": 4, "itemType": "WD"},
                    {"displayOrder": 5, "itemType": "XD"},
                    {"displayOrder": 6, "itemType": "MS"},
                    {"displayOrder": 7, "itemType": "WS"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [
                      {"name": "A1", "captain": true},
                      {"name": "A2", "captain": false},
                      {"name": "A3", "captain": false}
                    ]},
                    {"name": "Team B", "members": [
                      {"name": "B1", "captain": true},
                      {"name": "B2", "captain": false},
                      {"name": "B3", "captain": false}
                    ]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
        String tournamentId = createAndGetId(body);
        List<TournamentCustomItem> items = tournamentCustomItemMapper.selectList(
                new QueryWrapper<TournamentCustomItem>().eq("tournament_id", tournamentId).orderByAsc("display_order"));
        assertEquals(7, items.size());
        // 出现多次的类型附加序数，出现单次的类型为纯名称
        assertEquals("男单1", items.get(0).getItemName());
        assertEquals("女单1", items.get(1).getItemName());
        assertEquals("男双", items.get(2).getItemName());
        assertEquals("女双", items.get(3).getItemName());
        assertEquals("混双", items.get(4).getItemName());
        assertEquals("男单2", items.get(5).getItemName());
        assertEquals("女单2", items.get(6).getItemName());

        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        List<TournamentTeamMember> leftMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getLeftPlayerId()));
        List<TournamentTeamMember> rightMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getRightPlayerId()));

        String lineupBody = """
                {
                  "items": [
                    {"itemCode": "MS_1", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS_2", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "MD_3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "WD_4", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "XD_5", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]},
                    {"itemCode": "MS_6", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS_7", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), rightMembers.get(0).getId(),
                leftMembers.get(1).getId(), rightMembers.get(1).getId(),
                leftMembers.get(0).getId(), leftMembers.get(2).getId(),
                rightMembers.get(0).getId(), rightMembers.get(2).getId(),
                leftMembers.get(1).getId(), leftMembers.get(2).getId(),
                rightMembers.get(1).getId(), rightMembers.get(2).getId(),
                leftMembers.get(0).getId(), leftMembers.get(1).getId(),
                rightMembers.get(0).getId(), rightMembers.get(1).getId(),
                leftMembers.get(2).getId(), rightMembers.get(2).getId(),
                leftMembers.get(0).getId(), rightMembers.get(0).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk());

        // 连胜 3 项（3:0），7项赛门槛为 4 胜，此时提前结算应被拒绝（400）
        finishChildItem(parentMatch.getId(), "MS_1", "left");
        finishChildItem(parentMatch.getId(), "WS_2", "left");
        finishChildItem(parentMatch.getId(), "MD_3", "left");

        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));

        // 胜第 4 项（4:0），达到 (7 / 2) + 1 = 4 胜门槛，允许提前结算
        finishChildItem(parentMatch.getId(), "WD_4", "left");

        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isOk());

        MatchRecord finishedParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, finishedParent.getStatus());
        assertEquals(parentMatch.getLeftPlayerId(), finishedParent.getWinnerId());
        assertEquals(4, finishedParent.getLeftGameWins());
        assertEquals(0, finishedParent.getRightGameWins());
    }

    @Test
    void badmintonCustomTeam_shouldSupportPlayingAllItemsWithoutEarlySettlement() throws Exception {
        String body = """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Custom 3-item Play All Items",
                  "tournamentType": 0,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
        String tournamentId = createAndGetId(body);
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));

        List<TournamentTeamMember> leftMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getLeftPlayerId()));
        List<TournamentTeamMember> rightMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getRightPlayerId()));

        String lineupBody = """
                {
                  "items": [
                    {"itemCode": "MS_1", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS_2", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "XD_3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), rightMembers.get(0).getId(),
                leftMembers.get(1).getId(), rightMembers.get(1).getId(),
                leftMembers.get(0).getId(), leftMembers.get(2).getId(),
                rightMembers.get(0).getId(), rightMembers.get(2).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk());

        // 胜第 1 项（1:0）
        finishChildItem(parentMatch.getId(), "MS_1", "left");
        // 胜第 2 项（2:0）已达门槛，但不提前结算，继续打第 3 项
        finishChildItem(parentMatch.getId(), "WS_2", "left");

        MatchRecord midCheck = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(0, midCheck.getStatus()); // 父场未结束（0=未开始，仅全部子项完赛或提前结算才终结为2）

        // 打完第 3 项：Right 胜（最终 2:1）
        finishChildItem(parentMatch.getId(), "XD_3", "right");

        // 全部子项打完后，父场自动结算
        MatchRecord finalParent = matchRecordMapper.selectById(parentMatch.getId());
        assertEquals(2, finalParent.getStatus()); // 2 = 已结束
        assertEquals(parentMatch.getLeftPlayerId(), finalParent.getWinnerId());
        assertEquals(2, finalParent.getLeftGameWins());
        assertEquals(1, finalParent.getRightGameWins());
    }

    @Test
    void badmintonCustomTeam_roundRobin_shouldRejectEarlySettlementEvenIfWinningThresholdReached() throws Exception {
        String body = """
                {
                  "sportType": 0,
                  "participantType": 1,
                  "teamMatchTemplate": 3,
                  "name": "Custom 3-item Round Robin Must Play All",
                  "tournamentType": 2,
                  "roundRobinRounds": 1,
                  "customItems": [
                    {"displayOrder": 1, "itemType": "MS"},
                    {"displayOrder": 2, "itemType": "WS"},
                    {"displayOrder": 3, "itemType": "XD"}
                  ],
                  "teams": [
                    {"name": "Team A", "members": [{"name": "A1", "captain": true}, {"name": "A2", "captain": false}, {"name": "A3", "captain": false}]},
                    {"name": "Team B", "members": [{"name": "B1", "captain": true}, {"name": "B2", "captain": false}, {"name": "B3", "captain": false}]}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """;
        String tournamentId = createAndGetId(body);
        MatchRecord parentMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));

        List<TournamentTeamMember> leftMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getLeftPlayerId()));
        List<TournamentTeamMember> rightMembers = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId).eq("participant_id", parentMatch.getRightPlayerId()));

        String lineupBody = """
                {
                  "items": [
                    {"itemCode": "MS_1", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "WS_2", "leftMemberIds": ["%s"], "rightMemberIds": ["%s"]},
                    {"itemCode": "XD_3", "leftMemberIds": ["%s", "%s"], "rightMemberIds": ["%s", "%s"]}
                  ]
                }
                """.formatted(
                leftMembers.get(0).getId(), rightMembers.get(0).getId(),
                leftMembers.get(1).getId(), rightMembers.get(1).getId(),
                leftMembers.get(0).getId(), leftMembers.get(2).getId(),
                rightMembers.get(0).getId(), rightMembers.get(2).getId()
        );

        mockMvc.perform(put("/api/v1/matches/{id}/team-lineup", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(lineupBody))
                .andExpect(status().isOk());

        // 连胜 2 项达到 2:0
        finishChildItem(parentMatch.getId(), "MS_1", "left");
        finishChildItem(parentMatch.getId(), "WS_2", "left");

        // 循环赛必须打满所有项，不允许提前结算，调用 settle 接口应报 400
        mockMvc.perform(put("/api/v1/matches/{id}/team-match/settle", parentMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatch.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    private void finishChildItem(String parentMatchId, String itemCode, String winnerSide) throws Exception {
        String startResp = mockMvc.perform(put("/api/v1/matches/{id}/team-items/{itemCode}/start", parentMatchId, itemCode)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, parentMatchId)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String childMatchId = objectMapper.readTree(startResp).path("data").path("childMatchId").asText();

        int leftWins = "left".equals(winnerSide) ? 2 : 0;
        int rightWins = "left".equals(winnerSide) ? 0 : 2;
        int leftScore = "left".equals(winnerSide) ? 21 : 12;
        int rightScore = "left".equals(winnerSide) ? 12 : 21;

        mockMvc.perform(put("/api/v1/matches/{id}/finish", childMatchId)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, childMatchId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "%s",
                                  "leftScore": 0,
                                  "rightScore": 0,
                                  "leftGameWins": %d,
                                  "rightGameWins": %d,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": %d, "rightScore": %d, "winnerSide": "%s"},
                                    {"gameNo": 2, "leftScore": %d, "rightScore": %d, "winnerSide": "%s"}
                                  ]
                                }
                                """.formatted(winnerSide, leftWins, rightWins, leftScore, rightScore, winnerSide, leftScore, rightScore, winnerSide)))
                .andExpect(status().isOk());
    }
}

