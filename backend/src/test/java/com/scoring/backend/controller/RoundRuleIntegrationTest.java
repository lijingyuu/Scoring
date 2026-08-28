package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
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

import static com.scoring.backend.controller.MatchLockTestSupport.withMatchLock;
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
        "spring.datasource.url=jdbc:h2:mem:round_rule_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class RoundRuleIntegrationTest {

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
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-round-rule");
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentRoundRuleMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser());
    }

    @Test
    void createKnockoutTournament_withRoundRules_shouldApplyRuleByKnockoutRound() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Round rule knockout",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": false, "capPoint": 11}},
                    {"stageType": 1, "roundNum": 2, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}}
                  ]
                }
                """);

        assertEquals(2, tournamentRoundRuleMapper.selectCount(new QueryWrapper<TournamentRoundRule>().eq("tournament_id", tournamentId)));

        MatchRecord firstRoundMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", 1)
                .eq("round_num", 1)
                .last("LIMIT 1"));
        assertNotNull(firstRoundMatch);

        mockMvc.perform(get("/api/v1/matches/{id}/record", firstRoundMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bestOf").value(1))
                .andExpect(jsonPath("$.data.gamesToWin").value(1))
                .andExpect(jsonPath("$.data.pointsToWin").value(11))
                .andExpect(jsonPath("$.data.enableDeuce").value(false))
                .andExpect(jsonPath("$.data.capPoint").value(11));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roundRuleEnabled").value(true))
                .andExpect(jsonPath("$.data.roundRules.length()").value(2));
    }

    @Test
    void createKnockoutTournament_withThirdPlace_shouldCreateThirdPlaceMatchAndUseThirdPlaceRule() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Third place knockout",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "thirdPlaceEnabled": true,
                  "thirdPlaceRule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": false, "capPoint": 11}
                }
                """);

        MatchRecord thirdPlaceMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("match_role", 1));
        assertNotNull(thirdPlaceMatch);
        assertEquals(2, thirdPlaceMatch.getRoundNum());

        assertEquals(2, matchRecordMapper.selectCount(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("loser_next_match_id", thirdPlaceMatch.getId())));

        mockMvc.perform(get("/api/v1/matches/{id}/record", thirdPlaceMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchRole").value(1))
                .andExpect(jsonPath("$.data.bestOf").value(1))
                .andExpect(jsonPath("$.data.gamesToWin").value(1))
                .andExpect(jsonPath("$.data.pointsToWin").value(11))
                .andExpect(jsonPath("$.data.enableDeuce").value(false))
                .andExpect(jsonPath("$.data.capPoint").value(11));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.thirdPlaceEnabled").value(true))
                .andExpect(jsonPath("$.data.thirdPlaceBestOf").value(1));
    }

    @Test
    void createKnockoutTournament_withExplicitKnockoutRounds_shouldPersistRoundsAndScopes() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Explicit knockout rounds",
                  "tournamentType": 0,
                  "knockoutRounds": 3,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4},
                    {"name": "P5", "seed": 5}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": false, "capPoint": 11}},
                    {"stageType": 1, "roundNum": 2, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 15, "enableDeuce": false, "capPoint": 15}},
                    {"stageType": 1, "roundNum": 3, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}}
                  ]
                }
                """);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(3, tournament.getKnockoutRounds());
        assertEquals(3, tournamentRoundRuleMapper.selectCount(new QueryWrapper<TournamentRoundRule>().eq("tournament_id", tournamentId)));

        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.knockoutRounds").value(3))
                .andExpect(jsonPath("$.data.roundRules.length()").value(3));
    }

    @Test
    void createKnockoutTournament_withMismatchedKnockoutRounds_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "Invalid knockout rounds",
                                  "tournamentType": 0,
                                  "knockoutRounds": 3,
                                  "players": [
                                    {"name": "P1", "seed": 1},
                                    {"name": "P2", "seed": 2},
                                    {"name": "P3", "seed": 3},
                                    {"name": "P4", "seed": 4}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("player count does not match knockoutRounds"));
    }

    @Test
    void finishMatch_withManualLowGameScore_shouldNotBeRejectedByTargetPoints() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Manual low score",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}}
                  ]
                }
                """);

        MatchRecord match = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", 1)
                .eq("round_num", 1)
                .last("LIMIT 1"));
        assertNotNull(match);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", match.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, match.getId(), "user-round-rule"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "left",
                                  "leftScore": 1,
                                  "rightScore": 0,
                                  "leftGameWins": 1,
                                  "rightGameWins": 0,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": 11, "rightScore": 9, "winnerSide": "left"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void finishMatch_shouldUseRoundRuleGamesToWinInsteadOfTournamentDefault() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Round rule finish",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": false, "capPoint": 0}},
                    {"stageType": 1, "roundNum": 2, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}}
                  ]
                }
                """);

        MatchRecord firstRoundMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", 1)
                .eq("round_num", 1)
                .last("LIMIT 1"));
        assertNotNull(firstRoundMatch);

        mockMvc.perform(put("/api/v1/matches/{id}/finish", firstRoundMatch.getId())
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, firstRoundMatch.getId(), "user-round-rule"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerSide": "left",
                                  "leftScore": 1,
                                  "rightScore": 0,
                                  "leftGameWins": 1,
                                  "rightGameWins": 0,
                                  "gameScores": [
                                    {"gameNo": 1, "leftScore": 11, "rightScore": 9, "winnerSide": "left"}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void createTournament_withoutDeuce_shouldAllowCapPointBelowPointsToWin() throws Exception {
        createAndGetId("""
                {
                  "sportType": 0,
                  "name": "No deuce high target",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 50, "enableDeuce": false, "capPoint": 30}
                }
                """);
        createAndGetId("""
                {
                  "sportType": 0,
                  "name": "No deuce zero cap",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 50, "enableDeuce": false, "capPoint": 0}
                }
                """);
        createAndGetId("""
                {
                  "sportType": 0,
                  "name": "No deuce high cap",
                  "tournamentType": 0,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2}
                  ],
                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 50, "enableDeuce": false, "capPoint": 150}
                }
                """);
    }

    @Test
    void createGroupTournament_withRoundRules_shouldPersistGroupAndKnockoutScopes() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "sportType": 0,
                  "name": "Round rule group",
                  "tournamentType": 1,
                  "knockoutSlots": 2,
                  "qualifiersPerGroup": 1,
                  "players": [
                    {"name": "P1", "seed": 1},
                    {"name": "P2", "seed": 2},
                    {"name": "P3", "seed": 3},
                    {"name": "P4", "seed": 4}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30},
                  "roundRuleEnabled": true,
                  "roundRules": [
                    {"stageType": 0, "roundNum": 0, "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": false, "capPoint": 11}},
                    {"stageType": 1, "roundNum": 1, "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}}
                  ]
                }
                """);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getKnockoutRounds());
        assertEquals(2, tournamentRoundRuleMapper.selectCount(new QueryWrapper<TournamentRoundRule>().eq("tournament_id", tournamentId)));

        MatchRecord groupMatch = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", 0)
                .last("LIMIT 1"));
        assertNotNull(groupMatch);

        mockMvc.perform(get("/api/v1/matches/{id}/record", groupMatch.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.bestOf").value(1))
                .andExpect(jsonPath("$.data.pointsToWin").value(11));

        mockMvc.perform(get("/api/v1/tournaments/{id}/groups", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.roundRuleEnabled").value(true))
                .andExpect(jsonPath("$.data.roundRules.length()").value(2));
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
        user.setId("user-round-rule");
        user.setOpenid("openid-round-rule");
        user.setNickname("round-rule");
        user.setProfileCompleted(true);
        return user;
    }
}
