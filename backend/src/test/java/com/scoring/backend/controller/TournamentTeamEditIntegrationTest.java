package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 创建者编辑队伍（改队名/追加队员）集成测试。
 * 有意不覆盖：增删队伍、删除队员（需求收窄，接口不支持）。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:team_edit_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class TournamentTeamEditIntegrationTest {

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
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1"));
    }

    @Test
    void teamsVO_exposesCreatorFlagForEditEntry() throws Exception {
        String tournamentId = createVolleyballTournament();

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creator").value(true));

        when(authService.verifyToken(eq("other-token"))).thenReturn("user-2");
        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer other-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creator").value(false));
    }

    @Test
    void creator_canRenameTeamAndAppendVolleyballMembers() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "雷暴更名",
                                  "addMembers": [
                                    {"name": "新队员七", "jerseyNumber": 7, "libero": false},
                                    {"name": "自由人八", "jerseyNumber": 8, "libero": true}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Player team = playerMapper.selectById(teamId);
        assertNotNull(team);
        assertEquals("雷暴更名", team.getName());

        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("participant_id", teamId)
                        .orderByAsc("display_order"));
        assertEquals(8, members.size());
        TournamentTeamMember appended = members.get(6);
        assertEquals("新队员七", appended.getName());
        assertEquals(7, appended.getJerseyNumber());
        assertEquals(false, appended.getLibero());
        assertEquals(7, appended.getDisplayOrder());
        TournamentTeamMember libero = members.get(7);
        assertEquals("自由人八", libero.getName());
        assertEquals(8, libero.getJerseyNumber());
        assertEquals(true, libero.getLibero());

        mockMvc.perform(get("/api/v1/tournaments/{id}/teams", tournamentId).header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams[0].name").value("雷暴更名"))
                .andExpect(jsonPath("$.data.teams[0].memberCount").value(8));
    }

    @Test
    void volleyball_appendMember_withDuplicateJerseyRejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addMembers": [{"name": "号码冲突", "jerseyNumber": 1}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("雷暴 球衣号码 1 已被使用"));

        assertEquals(6, tournamentTeamMemberMapper.selectCount(
                new QueryWrapper<TournamentTeamMember>().eq("participant_id", teamId)));
    }

    @Test
    void volleyball_appendMember_withoutJerseyRejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addMembers": [{"name": "无号码"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("雷暴 新队员需要有效球衣号码"));
    }

    @Test
    void appendSecondCaptain_rejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"addMembers": [{"name": "第二个队长", "jerseyNumber": 9, "captain": true}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("雷暴 全队必须有且仅有1名队长"));
    }

    @Test
    void badmintonTeam_renameSucceedsAndJerseyIgnored() throws Exception {
        String tournamentId = createBadmintonTeamTournament();
        String teamId = firstTeamId(tournamentId, "鹰队");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "新队名", "addMembers": [{"name": "新成员", "jerseyNumber": 99}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("participant_id", teamId));
        assertEquals(3, members.size());
        TournamentTeamMember appended = members.stream()
                .filter(member -> "新成员".equals(member.getName()))
                .findFirst().orElseThrow();
        assertEquals(null, appended.getJerseyNumber());
        assertEquals(false, appended.getLibero());
    }

    @Test
    void nonCreator_rejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");
        when(authService.verifyToken(eq("other-token"))).thenReturn("user-2");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "越权改名"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("only creator can operate this tournament"));

        assertEquals("雷暴", playerMapper.selectById(teamId).getName());
    }

    @Test
    void archivedTournament_rejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        tournament.setArchived(true);
        tournamentMapper.updateById(tournament);

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "归档后改名"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    @Test
    void emptyUpdate_rejected() throws Exception {
        String tournamentId = createVolleyballTournament();
        String teamId = firstTeamId(tournamentId, "雷暴");

        mockMvc.perform(put("/api/v1/tournaments/{id}/teams/{participantId}", tournamentId, teamId)
                .header("Authorization", "Bearer test-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("没有需要保存的修改"));
    }

    private String createVolleyballTournament() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 1,
                                  "name": "排球团体赛",
                                  "location": "体育馆",
                                  "tournamentType": 2,
                                  "roundRobinRounds": 1,
                                  "players": [],
                                  "teams": [
                                    {"name": "雷暴", "members": [
                                      {"name": "一号", "jerseyNumber": 1, "captain": true},
                                      {"name": "二号", "jerseyNumber": 2},
                                      {"name": "三号", "jerseyNumber": 3},
                                      {"name": "四号", "jerseyNumber": 4},
                                      {"name": "五号", "jerseyNumber": 5},
                                      {"name": "六号", "jerseyNumber": 6}
                                    ]},
                                    {"name": "闪电", "members": [
                                      {"name": "甲一", "jerseyNumber": 1, "captain": true},
                                      {"name": "甲二", "jerseyNumber": 2},
                                      {"name": "甲三", "jerseyNumber": 3},
                                      {"name": "甲四", "jerseyNumber": 4},
                                      {"name": "甲五", "jerseyNumber": 5},
                                      {"name": "甲六", "jerseyNumber": 6}
                                    ]}
                                  ],
                                  "rule": {"bestOf": 5, "gamesToWin": 3}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private String createBadmintonTeamTournament() throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sportType": 0,
                                  "participantType": 1,
                                  "teamMatchTemplate": 1,
                                  "name": "羽毛球团体赛",
                                  "tournamentType": 0,
                                  "teams": [
                                    {"name": "鹰队", "members": [
                                      {"name": "队长甲", "captain": true},
                                      {"name": "队员乙"}
                                    ]},
                                    {"name": "隼队", "members": [
                                      {"name": "队长丙", "captain": true},
                                      {"name": "队员丁"}
                                    ]}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private String firstTeamId(String tournamentId, String teamName) {
        Player team = playerMapper.selectOne(new QueryWrapper<Player>()
                .eq("tournament_id", tournamentId)
                .eq("name", teamName));
        assertNotNull(team);
        assertTrue(tournamentTeamMemberMapper.selectCount(
                new QueryWrapper<TournamentTeamMember>().eq("participant_id", team.getId())) > 0);
        return team.getId();
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
