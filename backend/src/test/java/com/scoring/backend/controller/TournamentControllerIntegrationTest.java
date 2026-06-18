package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
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

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
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
                .andExpect(jsonPath("$.message").value("仅排球赛事支持查看队伍"));
    }
}
