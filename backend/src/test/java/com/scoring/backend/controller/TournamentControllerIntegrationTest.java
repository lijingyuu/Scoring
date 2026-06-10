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
}
