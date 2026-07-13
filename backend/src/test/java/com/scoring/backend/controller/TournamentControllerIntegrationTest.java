package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:volleyball_create_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class TournamentControllerIntegrationTest {

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
    private UserMapper userMapper;

    @Autowired
    private TournamentRefereeConfigMapper tournamentRefereeConfigMapper;

    @Autowired
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentRefereeGrantMapper.delete(new QueryWrapper<>());
        tournamentRefereeConfigMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1", "openid-user-1", true));
    }

    @Test
    void createVolleyballTournament_shouldUseTeamsInsteadOfPlayers() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "排球测试赛",
                                  "location": "测试球馆",
                                  "tournamentType": 0,
                                  "players": [],
                                  "teams": [
                                    {
                                      "name": "A队",
                                      "members": [
                                        {"name": "张一", "jerseyNumber": 1, "libero": false, "captain": true},
                                        {"name": "张二", "jerseyNumber": 2, "libero": false, "captain": false},
                                        {"name": "张三", "jerseyNumber": 3, "libero": false, "captain": false},
                                        {"name": "张四", "jerseyNumber": 4, "libero": false, "captain": false},
                                        {"name": "张五", "jerseyNumber": 5, "libero": false, "captain": false},
                                        {"name": "张六", "jerseyNumber": 6, "libero": false, "captain": false},
                                        {"name": "张七", "jerseyNumber": 7, "libero": false, "captain": false},
                                        {"name": "张八", "jerseyNumber": 8, "libero": false, "captain": false},
                                        {"name": "张九", "jerseyNumber": 9, "libero": false, "captain": false},
                                        {"name": "张十", "jerseyNumber": 10, "libero": false, "captain": false},
                                        {"name": "张十一", "jerseyNumber": 11, "libero": false, "captain": false},
                                        {"name": "张十二", "jerseyNumber": 12, "libero": false, "captain": false}
                                      ]
                                    },
                                    {
                                      "name": "B队",
                                      "members": [
                                        {"name": "李一", "jerseyNumber": 1, "libero": false, "captain": true},
                                        {"name": "李二", "jerseyNumber": 2, "libero": false, "captain": false},
                                        {"name": "李三", "jerseyNumber": 3, "libero": false, "captain": false},
                                        {"name": "李四", "jerseyNumber": 4, "libero": false, "captain": false},
                                        {"name": "李五", "jerseyNumber": 5, "libero": false, "captain": false},
                                        {"name": "李六", "jerseyNumber": 6, "libero": false, "captain": false},
                                        {"name": "李七", "jerseyNumber": 7, "libero": false, "captain": false},
                                        {"name": "李八", "jerseyNumber": 8, "libero": false, "captain": false},
                                        {"name": "李九", "jerseyNumber": 9, "libero": false, "captain": false},
                                        {"name": "李十", "jerseyNumber": 10, "libero": false, "captain": false},
                                        {"name": "李十一", "jerseyNumber": 11, "libero": false, "captain": false},
                                        {"name": "李十二", "jerseyNumber": 12, "libero": false, "captain": false}
                                      ]
                                    }
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String tournamentId = root.path("data").path("tournamentId").asText();
        assertNotNull(tournamentId);

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getSportType());

        List<Player> participants = playerMapper.selectList(
                new QueryWrapper<Player>().eq("tournament_id", tournamentId)
        );
        assertEquals(2, participants.size());

        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId)
        );
        assertEquals(24, members.size());
    }

    @Test
    void createVolleyballGroupTournament_shouldGenerateGroupMatches() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "排球小组赛测试",
                                  "location": "测试球馆",
                                  "tournamentType": 1,
                                  "knockoutSlots": 4,
                                  "qualifiersPerGroup": 2,
                                  "players": [],
                                  "teams": [
                                    {"name": "A队", "members": [{"name": "A1", "jerseyNumber": 1, "captain": true}, {"name": "A2", "jerseyNumber": 2, "captain": false}, {"name": "A3", "jerseyNumber": 3, "captain": false}, {"name": "A4", "jerseyNumber": 4, "captain": false}, {"name": "A5", "jerseyNumber": 5, "captain": false}, {"name": "A6", "jerseyNumber": 6, "captain": false}]},
                                    {"name": "B队", "members": [{"name": "B1", "jerseyNumber": 1, "captain": true}, {"name": "B2", "jerseyNumber": 2, "captain": false}, {"name": "B3", "jerseyNumber": 3, "captain": false}, {"name": "B4", "jerseyNumber": 4, "captain": false}, {"name": "B5", "jerseyNumber": 5, "captain": false}, {"name": "B6", "jerseyNumber": 6, "captain": false}]},
                                    {"name": "C队", "members": [{"name": "C1", "jerseyNumber": 1, "captain": true}, {"name": "C2", "jerseyNumber": 2, "captain": false}, {"name": "C3", "jerseyNumber": 3, "captain": false}, {"name": "C4", "jerseyNumber": 4, "captain": false}, {"name": "C5", "jerseyNumber": 5, "captain": false}, {"name": "C6", "jerseyNumber": 6, "captain": false}]},
                                    {"name": "D队", "members": [{"name": "D1", "jerseyNumber": 1, "captain": true}, {"name": "D2", "jerseyNumber": 2, "captain": false}, {"name": "D3", "jerseyNumber": 3, "captain": false}, {"name": "D4", "jerseyNumber": 4, "captain": false}, {"name": "D5", "jerseyNumber": 5, "captain": false}, {"name": "D6", "jerseyNumber": 6, "captain": false}]},
                                    {"name": "E队", "members": [{"name": "E1", "jerseyNumber": 1, "captain": true}, {"name": "E2", "jerseyNumber": 2, "captain": false}, {"name": "E3", "jerseyNumber": 3, "captain": false}, {"name": "E4", "jerseyNumber": 4, "captain": false}, {"name": "E5", "jerseyNumber": 5, "captain": false}, {"name": "E6", "jerseyNumber": 6, "captain": false}]},
                                    {"name": "F队", "members": [{"name": "F1", "jerseyNumber": 1, "captain": true}, {"name": "F2", "jerseyNumber": 2, "captain": false}, {"name": "F3", "jerseyNumber": 3, "captain": false}, {"name": "F4", "jerseyNumber": 4, "captain": false}, {"name": "F5", "jerseyNumber": 5, "captain": false}, {"name": "F6", "jerseyNumber": 6, "captain": false}]}
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode root = objectMapper.readTree(response);
        String tournamentId = root.path("data").path("tournamentId").asText();
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertNotNull(tournament);
        assertEquals(1, tournament.getTournamentType());
        assertEquals(4, tournament.getKnockoutSlots());
        assertEquals(2, tournament.getQualifiersPerGroup());
        assertEquals(0, tournament.getCurrentStage());

        List<Player> participants = playerMapper.selectList(new QueryWrapper<Player>().eq("tournament_id", tournamentId));
        assertEquals(6, participants.size());
        long assignedGroups = participants.stream().filter(player -> player.getGroupNo() != null).count();
        assertEquals(6, assignedGroups);
    }

    @Test
    void getTeams_shouldReturnVolleyballTeamsWithCaptainFirstOrder() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "排球队伍查看测试",
                                  "location": "测试馆",
                                  "tournamentType": 0,
                                  "players": [],
                                  "teams": [
                                    {
                                      "name": "星火队",
                                      "members": [
                                        {"name": "二号", "jerseyNumber": 2, "captain": false},
                                        {"name": "十号", "jerseyNumber": 10, "captain": false},
                                        {"name": "一号队长", "jerseyNumber": 1, "captain": true},
                                        {"name": "八号", "jerseyNumber": 8, "captain": false},
                                        {"name": "六号", "jerseyNumber": 6, "captain": false},
                                        {"name": "四号", "jerseyNumber": 4, "captain": false}
                                      ]
                                    },
                                    {
                                      "name": "远航队",
                                      "members": [
                                        {"name": "队长", "jerseyNumber": 12, "captain": true},
                                        {"name": "三号", "jerseyNumber": 3, "captain": false},
                                        {"name": "九号", "jerseyNumber": 9, "captain": false},
                                        {"name": "六号", "jerseyNumber": 6, "captain": false},
                                        {"name": "一号", "jerseyNumber": 1, "captain": false},
                                        {"name": "五号", "jerseyNumber": 5, "captain": false}
                                      ]
                                    }
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.teams[0].name").value("星火队"))
                .andExpect(jsonPath("$.data.teams[0].captainName").value("一号队长"))
                .andExpect(jsonPath("$.data.teams[0].members[0].name").value("一号队长"))
                .andExpect(jsonPath("$.data.teams[0].members[1].jerseyNumber").value(2))
                .andExpect(jsonPath("$.data.teams[0].members[2].jerseyNumber").value(4))
                .andExpect(jsonPath("$.data.teams[1].members[0].name").value("队长"))
                .andExpect(jsonPath("$.data.teams[1].members[1].jerseyNumber").value(1))
                .andExpect(jsonPath("$.data.teams[1].members[2].jerseyNumber").value(3));
    }

    @Test
    void getTeams_shouldRejectBadmintonTournament() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "羽毛球测试赛",
                                  "location": "测试馆",
                                  "players": [
                                    {"name": "选手A", "seed": 1},
                                    {"name": "选手B", "seed": 2}
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2,
                                    "pointsToWin": 21,
                                    "enableDeuce": true,
                                    "capPoint": 30
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();

        mockMvc.perform(get("/api/v1/tournaments/" + tournamentId + "/teams"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅团体赛支持查看队伍"));
    }
    @Test
    void createTournament_shouldRejectIncompleteProfile() throws Exception {
        userMapper.insert(buildUser("user-2", "openid-user-2", false));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "未完善资料测试赛",
                                  "location": "测试馆",
                                  "players": [
                                    {"name": "选手A", "seed": 1},
                                    {"name": "选手B", "seed": 2}
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2,
                                    "pointsToWin": 21,
                                    "enableDeuce": true,
                                    "capPoint": 30
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先完善资料后再操作"));
    }

    @Test
    void favoriteTournament_shouldRejectIncompleteProfile() throws Exception {
        userMapper.insert(buildUser("user-2", "openid-user-2", false));

        Tournament tournament = new Tournament();
        tournament.setId("t-favorite-1");
        tournament.setName("收藏测试赛");
        tournament.setLocation("测试馆");
        tournament.setStatus(1);
        tournament.setSportType(0);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(21);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId("user-1");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments/t-favorite-1/favorite")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("请先完善资料后再操作"));
    }

    @Test
    void generateKnockout_whenSingleGroupTakesTwo_shouldFallbackToSameGroupPairing() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "single group",
                                  "location": "court",
                                  "tournamentType": 1,
                                  "knockoutSlots": 2,
                                  "qualifiersPerGroup": 2,
                                  "players": [
                                    {"name": "A", "seed": 1},
                                    {"name": "B", "seed": 2},
                                    {"name": "C", "seed": 3}
                                  ],
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2,
                                    "pointsToWin": 21,
                                    "enableDeuce": true,
                                    "capPoint": 30
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();
        List<MatchRecord> groupMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 0)
                        .orderByAsc("match_index")
        );
        assertEquals(3, groupMatches.size());

        for (MatchRecord match : groupMatches) {
            mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/api/v1/matches/{id}/score", match.getId())
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "winnerId": "%s",
                                      "scoreDisplay": "2:0"
                                    }
                                    """.formatted(match.getLeftPlayerId())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<MatchRecord> knockoutMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 1)
        );
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(1, knockoutMatches.size());
        assertEquals(1, tournament.getCurrentStage());
        assertEquals(Boolean.TRUE, tournament.getKnockoutGenerated());
    }

    @Test
    void groupStandings_whenTwoWayTieCrossesQualificationLine_shouldUseHeadToHead() throws Exception {
        String tournamentId = createBadmintonGroupTournament(8, 2, 1);
        List<Player> groupOne = loadGroupPlayers(tournamentId, 1);
        List<Player> groupTwo = loadGroupPlayers(tournamentId, 2);

        finishGroupWithTwoWayTopTie(tournamentId, 1, groupOne);
        finishGroupNormally(tournamentId, 2, groupTwo);

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[0].playerId").value(groupOne.get(0).getId()))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(true));

        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void groupStandings_whenNoGroupMatchFinished_shouldNotShowQualificationTags() throws Exception {
        String tournamentId = createBadmintonGroupTournament(6, 4, 2);

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("-"))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[0].tieUnresolved").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[1].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[1].tieUnresolved").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[2].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[2].tieUnresolved").value(false));
    }

    @Test
    void groupStandings_whenTieBlockCrossesQualificationLineAfterOneMatch_shouldOnlyShowCertainQualifier() throws Exception {
        String tournamentId = createBadmintonGroupTournament(8, 4, 2);
        List<Player> groupOne = loadGroupPlayers(tournamentId, 1);
        Player winner = groupOne.get(0);
        Player opponent = groupOne.get(1);

        finishOneGroupMatch(tournamentId, 1, winner.getId(), opponent.getId(), winner.getId());

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[0].playerId").value(winner.getId()))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[0].tieUnresolved").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[1].displayRankText").value("2"))
                .andExpect(jsonPath("$.data.groups[0].standings[1].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[1].tieUnresolved").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[2].displayRankText").value("2"))
                .andExpect(jsonPath("$.data.groups[0].standings[2].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[2].tieUnresolved").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[3].displayRankText").value("2"))
                .andExpect(jsonPath("$.data.groups[0].standings[3].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[3].tieUnresolved").value(true));
    }

    @Test
    void groupStandings_whenAllPlayersTieAcrossQualificationLineAfterMatches_shouldOnlyShowPending() throws Exception {
        String tournamentId = createBadmintonGroupTournament(6, 4, 2);
        List<Player> groupOne = loadGroupPlayers(tournamentId, 1);
        Player first = groupOne.get(0);
        Player second = groupOne.get(1);
        Player third = groupOne.get(2);

        finishOneGroupMatch(tournamentId, 1, first.getId(), second.getId(), first.getId());
        finishOneGroupMatch(tournamentId, 1, second.getId(), third.getId(), second.getId());
        finishOneGroupMatch(tournamentId, 1, third.getId(), first.getId(), third.getId());

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[0].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[0].tieUnresolved").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[1].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[1].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[1].tieUnresolved").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[2].displayRankText").value("1"))
                .andExpect(jsonPath("$.data.groups[0].standings[2].qualified").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[2].tieUnresolved").value(true));
    }

    @Test
    void groupStandings_whenThreeWayTieCrossesQualificationLine_shouldBlockKnockout() throws Exception {
        String tournamentId = createBadmintonGroupTournament(8, 2, 1);
        List<Player> groupOne = loadGroupPlayers(tournamentId, 1);
        List<Player> groupTwo = loadGroupPlayers(tournamentId, 2);

        finishGroupWithThreeWayTie(tournamentId, 1, groupOne);
        finishGroupNormally(tournamentId, 2, groupTwo);

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[0].tieUnresolved").value(true));

        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("group ranking has unresolved tie"));
    }

    @Test
    void groupStandings_whenTwoWayTieDoesNotCrossQualificationLine_shouldAllowKnockout() throws Exception {
        String tournamentId = createBadmintonGroupTournament(8, 4, 2);
        List<Player> groupOne = loadGroupPlayers(tournamentId, 1);
        List<Player> groupTwo = loadGroupPlayers(tournamentId, 2);

        finishGroupWithTwoWayTopTie(tournamentId, 1, groupOne);
        finishGroupNormally(tournamentId, 2, groupTwo);

        mockMvc.perform(get("/api/v1/tournaments/{id}/group-standings", tournamentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.hasUnresolvedTie").value(false))
                .andExpect(jsonPath("$.data.groups[0].standings[0].qualified").value(true))
                .andExpect(jsonPath("$.data.groups[0].standings[1].qualified").value(true));

        mockMvc.perform(post("/api/v1/tournaments/{id}/generate-knockout", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String createBadmintonGroupTournament(int playerCount, int knockoutSlots, int qualifiersPerGroup) throws Exception {
        List<Map<String, Object>> players = new ArrayList<>();
        for (int i = 1; i <= playerCount; i++) {
            Map<String, Object> player = new HashMap<>();
            player.put("name", "P" + i);
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
        request.put("name", "tie boundary");
        request.put("location", "court");
        request.put("tournamentType", 1);
        request.put("knockoutSlots", knockoutSlots);
        request.put("qualifiersPerGroup", qualifiersPerGroup);
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
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private List<Player> loadGroupPlayers(String tournamentId, int groupNo) {
        return playerMapper.selectList(
                        new QueryWrapper<Player>()
                                .eq("tournament_id", tournamentId)
                                .eq("group_no", groupNo)
                ).stream()
                .sorted(Comparator.comparing(Player::getGroupPosition))
                .toList();
    }

    private List<MatchRecord> loadGroupMatches(String tournamentId, int groupNo) {
        return matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .eq("stage_type", 0)
                        .eq("group_no", groupNo)
        );
    }

    private void finishGroupWithTwoWayTopTie(String tournamentId, int groupNo, List<Player> players) throws Exception {
        Player first = players.get(0);
        Player second = players.get(1);
        Player third = players.get(2);
        Player fourth = players.get(3);

        Map<String, String> winners = new HashMap<>();
        winners.put(pairKey(first.getId(), second.getId()), first.getId());
        winners.put(pairKey(third.getId(), first.getId()), third.getId());
        winners.put(pairKey(first.getId(), fourth.getId()), first.getId());
        winners.put(pairKey(second.getId(), third.getId()), second.getId());
        winners.put(pairKey(second.getId(), fourth.getId()), second.getId());
        winners.put(pairKey(fourth.getId(), third.getId()), fourth.getId());
        finishGroup(tournamentId, groupNo, winners);
    }

    private void finishGroupWithThreeWayTie(String tournamentId, int groupNo, List<Player> players) throws Exception {
        Player first = players.get(0);
        Player second = players.get(1);
        Player third = players.get(2);
        Player fourth = players.get(3);

        Map<String, String> winners = new HashMap<>();
        winners.put(pairKey(first.getId(), second.getId()), first.getId());
        winners.put(pairKey(second.getId(), third.getId()), second.getId());
        winners.put(pairKey(third.getId(), first.getId()), third.getId());
        winners.put(pairKey(first.getId(), fourth.getId()), first.getId());
        winners.put(pairKey(second.getId(), fourth.getId()), second.getId());
        winners.put(pairKey(third.getId(), fourth.getId()), third.getId());
        finishGroup(tournamentId, groupNo, winners);
    }

    private void finishGroupNormally(String tournamentId, int groupNo, List<Player> players) throws Exception {
        Map<String, String> winners = new HashMap<>();
        for (int i = 0; i < players.size(); i++) {
            for (int j = i + 1; j < players.size(); j++) {
                winners.put(pairKey(players.get(i).getId(), players.get(j).getId()), players.get(i).getId());
            }
        }
        finishGroup(tournamentId, groupNo, winners);
    }

    private void finishGroup(String tournamentId, int groupNo, Map<String, String> winners) throws Exception {
        for (MatchRecord match : loadGroupMatches(tournamentId, groupNo)) {
            String winnerId = winners.get(pairKey(match.getLeftPlayerId(), match.getRightPlayerId()));
            assertNotNull(winnerId);
            mockMvc.perform(put("/api/v1/matches/{id}/score", match.getId())
                            .header("Authorization", "Bearer test-token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "winnerId": "%s",
                                      "scoreDisplay": "2:0"
                                    }
                                    """.formatted(winnerId)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    private void finishOneGroupMatch(String tournamentId, int groupNo, String firstPlayerId, String secondPlayerId, String winnerId) throws Exception {
        String targetPairKey = pairKey(firstPlayerId, secondPlayerId);
        MatchRecord target = loadGroupMatches(tournamentId, groupNo).stream()
                .filter(match -> targetPairKey.equals(pairKey(match.getLeftPlayerId(), match.getRightPlayerId())))
                .findFirst()
                .orElseThrow();
        mockMvc.perform(put("/api/v1/matches/{id}/score", target.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "winnerId": "%s",
                                  "scoreDisplay": "2:0"
                                }
                                """.formatted(winnerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String pairKey(String left, String right) {
        return left.compareTo(right) < 0 ? left + ":" + right : right + ":" + left;
    }

    // ======================== 裁判密码流程测试 ========================

    @Test
    void createTournament_withRefereePassword_shouldPersistPassword() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "设置密码测试赛",
                                  "location": "测试馆",
                                  "players": [
                                    {"name": "选手A", "seed": 1},
                                    {"name": "选手B", "seed": 2}
                                  ],
                                  "refereePassword": "12345678",
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2,
                                    "pointsToWin": 21,
                                    "enableDeuce": true,
                                    "capPoint": 30
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String tournamentId = objectMapper.readTree(response).path("data").path("tournamentId").asText();
        assertNotNull(tournamentId);
    }

    @Test
    void createTournament_withInvalidPassword_shouldReject() throws Exception {
        // 非8位数字
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "name": "非法密码测试赛",
                                  "location": "测试馆",
                                  "players": [
                                    {"name": "选手A", "seed": 1},
                                    {"name": "选手B", "seed": 2}
                                  ],
                                  "refereePassword": "12345",
                                  "rule": {
                                    "bestOf": 3,
                                    "gamesToWin": 2,
                                    "pointsToWin": 21,
                                    "enableDeuce": true,
                                    "capPoint": 30
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("裁判密码必须为8位数字"));
    }

    @Test
    void refereeAuth_withCorrectPassword_shouldGrantAccess() throws Exception {
        // 创建者(user-1)创建赛事并设密码
        String tournamentId = createTournamentWithPassword("12345678");

        // 裁判(user-2)验证密码
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granted").value(true));
    }

    @Test
    void refereeAuth_withAllZeroPassword_shouldGrantAccess() throws Exception {
        String tournamentId = createTournamentWithPassword("00000000");

        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"00000000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granted").value(true));
    }

    @Test
    void refereeAuth_withWrongPassword_shouldReject() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"99999999\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("裁判密码错误"));
    }

    @Test
    void refereeAuth_withoutPasswordSet_shouldReject() throws Exception {
        // 创建赛事时不设密码
        String tournamentId = createVolleyballTournament();

        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("该赛事未设置裁判密码"));
    }

    @Test
    void listReferees_asCreator_shouldReturnList() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 裁判(user-2)先验证
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 创建者查看裁判列表
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        mockMvc.perform(get("/api/v1/tournaments/{id}/referees", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value("user-2"));
    }

    @Test
    void listReferees_asNonCreatorNonReferee_shouldReject() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        userMapper.insert(buildUser("user-3", "openid-stranger", true));
        when(authService.verifyToken(anyString())).thenReturn("user-3");

        mockMvc.perform(get("/api/v1/tournaments/{id}/referees", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("仅创建者或裁判可查看"));
    }

    @Test
    void removeReferee_asCreator_shouldSucceed() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 裁判(user-2)先验证
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 创建者移除裁判
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/tournaments/{id}/referees/user-2", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 验证列表为空
        mockMvc.perform(get("/api/v1/tournaments/{id}/referees", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void removeReferee_asReferee_shouldReject() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 两个裁判
        userMapper.insert(buildUser("user-2", "openid-referee-2", true));
        userMapper.insert(buildUser("user-3", "openid-referee-3", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));
        when(authService.verifyToken(anyString())).thenReturn("user-3");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // user-3(裁判)试图移除user-2(另一个裁判)
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/tournaments/{id}/referees/user-2", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有创建者可以移除裁判"));
    }

    @Test
    void updateRefereePassword_asCreator_shouldSucceed() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 修改密码
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-password", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"87654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 旧密码失效
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));

        // 新密码有效
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"87654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granted").value(true));
    }

    @Test
    void updateRefereePassword_asReferee_shouldReject() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 裁判不能改密码
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-password", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"11111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("只有创建者可以修改裁判密码"));
    }

    @Test
    void matchOperations_byReferee_shouldSucceed() throws Exception {
        // 创建排球赛事带密码
        String tournamentId = createTournamentWithPassword("12345678");

        // 裁判验证
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 裁判操作比赛: 获取match列表
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.canOperateMatches").value(true))
                .andExpect(jsonPath("$.data.canManageReferees").value(false))
                .andExpect(jsonPath("$.data.refereeGranted").value(true));

        // 创建者查看: canManageReferees=true
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.canOperateMatches").value(true))
                .andExpect(jsonPath("$.data.canManageReferees").value(true));
    }

    @Test
    void refereeAuth_duplicateGrant_shouldBeIdempotent() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        // 第一次验证
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 第二次验证(幂等)
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\": \"12345678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.granted").value(true));

        // 裁判列表只有1条
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        mockMvc.perform(get("/api/v1/tournaments/{id}/referees", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    @Test
    void tournamentDetail_shouldIncludeRefereeAccessFlags() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 未授权的陌生人查看详情
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.refereeGranted").value(false))
                .andExpect(jsonPath("$.data.canOperateMatches").value(false))
                .andExpect(jsonPath("$.data.canManageReferees").value(false));
    }

    @Test
    void refereesList_afterRemove_cannotOperateMatches() throws Exception {
        String tournamentId = createTournamentWithPassword("12345678");

        // 裁判验证
        userMapper.insert(buildUser("user-2", "openid-referee", true));
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/referee-auth", tournamentId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\": \"12345678\"}"));

        // 创建者移除裁判
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                .delete("/api/v1/tournaments/{id}/referees/user-2", tournamentId)
                .header("Authorization", "Bearer test-token"));

        // 被移除后canOperateMatches=false
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(get("/api/v1/tournaments/{id}/bracket", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.canOperateMatches").value(false))
                .andExpect(jsonPath("$.data.refereeGranted").value(false));
    }

    // ======================== 裁判测试辅助方法 ========================

    private String createTournamentWithPassword(String password) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "裁判测试赛",
                                  "location": "测试馆",
                                  "tournamentType": 0,
                                  "players": [],
                                  "teams": [
                                    {"name": "A队", "members": [
                                      {"name": "A1", "jerseyNumber": 1, "captain": true},
                                      {"name": "A2", "jerseyNumber": 2, "captain": false},
                                      {"name": "A3", "jerseyNumber": 3, "captain": false},
                                      {"name": "A4", "jerseyNumber": 4, "captain": false},
                                      {"name": "A5", "jerseyNumber": 5, "captain": false},
                                      {"name": "A6", "jerseyNumber": 6, "captain": false}
                                    ]},
                                    {"name": "B队", "members": [
                                      {"name": "B1", "jerseyNumber": 1, "captain": true},
                                      {"name": "B2", "jerseyNumber": 2, "captain": false},
                                      {"name": "B3", "jerseyNumber": 3, "captain": false},
                                      {"name": "B4", "jerseyNumber": 4, "captain": false},
                                      {"name": "B5", "jerseyNumber": 5, "captain": false},
                                      {"name": "B6", "jerseyNumber": 6, "captain": false}
                                    ]}
                                  ],
                                  "refereePassword": "%s",
                                  "rule": {"bestOf": 3, "gamesToWin": 2}
                                }
                                """.formatted(password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private String createVolleyballTournament() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "无密码测试赛",
                                  "location": "测试馆",
                                  "tournamentType": 0,
                                  "players": [],
                                  "teams": [
                                    {"name": "A队", "members": [
                                      {"name": "A1", "jerseyNumber": 1, "captain": true},
                                      {"name": "A2", "jerseyNumber": 2, "captain": false},
                                      {"name": "A3", "jerseyNumber": 3, "captain": false},
                                      {"name": "A4", "jerseyNumber": 4, "captain": false},
                                      {"name": "A5", "jerseyNumber": 5, "captain": false},
                                      {"name": "A6", "jerseyNumber": 6, "captain": false}
                                    ]},
                                    {"name": "B队", "members": [
                                      {"name": "B1", "jerseyNumber": 1, "captain": true},
                                      {"name": "B2", "jerseyNumber": 2, "captain": false},
                                      {"name": "B3", "jerseyNumber": 3, "captain": false},
                                      {"name": "B4", "jerseyNumber": 4, "captain": false},
                                      {"name": "B5", "jerseyNumber": 5, "captain": false},
                                      {"name": "B6", "jerseyNumber": 6, "captain": false}
                                    ]}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
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
