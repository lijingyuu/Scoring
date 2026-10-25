package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 羽毛球个人赛双打参赛单位（V31 entry_player_count）验收测试：
 * 1. 扁平 payload（顶层 playerCount=2）：组合名落库、成员明细落 tournament_team_member、
 *    record 详情带 playerCountPerParticipant=2 与双方成员；
 * 2. 多组别单/双打混合：组别间 entry_player_count 与成员互不串扰；
 * 3. 非法输入：成员 3 人 / 两人同名 → 400；单打带 members 被忽略（零回归）；
 * 4. 单打（不传新字段）：entry_player_count=1、无成员行、record 取 1。
 */
@SpringBootTest(classes = com.scoring.backend.ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:individual_doubles_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class IndividualDoublesCreationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private TournamentDivisionMapper tournamentDivisionMapper;

    @Autowired
    private PlayerMapper playerMapper;

    @Autowired
    private MatchRecordMapper matchRecordMapper;

    @Autowired
    private com.scoring.backend.mapper.TournamentTeamMemberMapper memberMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @Autowired
    private MatchLineupConfigMapper matchLineupConfigMapper;

    @Autowired
    private MatchReportMetaMapper matchReportMetaMapper;

    @Autowired
    private TeamMatchItemMapper teamMatchItemMapper;

    @Autowired
    private TournamentRankingConfigMapper tournamentRankingConfigMapper;

    @Autowired
    private TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;

    @Autowired
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-doubles");
        matchEventMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        matchReportMetaMapper.delete(new QueryWrapper<>());
        teamMatchItemMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentRoundRuleMapper.delete(new QueryWrapper<>());
        tournamentRankingConfigMapper.delete(new QueryWrapper<>());
        tournamentQualificationOverrideMapper.delete(new QueryWrapper<>());
        memberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        User user = new User();
        user.setId("user-doubles");
        user.setOpenid("openid-doubles");
        user.setNickname("doubles");
        user.setProfileCompleted(true);
        userMapper.insert(user);
    }

    private String createAndGetId(String body) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.tournamentId").isNotEmpty())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("data").path("tournamentId").asText();
    }

    private String createExpectError(String body, String expectedFragment) throws Exception {
        String response = mockMvc.perform(MockMvcRequestBuilders.post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().is4xxClientError())
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String message = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(response).path("message").asText();
        assertTrue(message.contains(expectedFragment), "拒绝理由应包含「" + expectedFragment + "」，实际: " + message);
        return message;
    }

    private static final String FLAT_DOUBLES_BODY = """
            {
              "name": "教工双打赛",
              "sportType": 0,
              "participantType": 0,
              "playerCount": 2,
              "tournamentType": 0,
              "refereePassword": "1234567890",
              "players": [
                {"seed": 1, "members": ["张三", "李四"]},
                {"seed": 2, "members": ["王五", "赵六"]},
                {"name": "临时组合", "members": ["甲", "乙"]},
                {"members": ["丙", "丁"]}
              ],
              "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
            }
            """;

    @Test
    void flatDoublesCreate_shouldPersistPairsAndMembers() throws Exception {
        String tournamentId = createAndGetId(FLAT_DOUBLES_BODY);

        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId));
        assertEquals(1, divisions.size());
        assertEquals(Integer.valueOf(2), divisions.get(0).getEntryPlayerCount());

        List<Player> players = playerMapper.selectList(
                new QueryWrapper<Player>().eq("division_id", divisions.get(0).getId()));
        assertEquals(4, players.size());
        // name 缺省时自动拼接组合显示名（seed 为 null 的行在 H2 默认 NULLS FIRST，按下标断言不可靠，按名定位）
        java.util.function.Function<String, Player> byName = (expected) -> players.stream()
                .filter(player -> expected.equals(player.getName()))
                .findFirst().orElseThrow(() -> new IllegalStateException("缺少组合: " + expected));
        Player pair1 = byName.apply("张三/李四");
        byName.apply("王五/赵六");
        byName.apply("临时组合");
        byName.apply("丙/丁");

        // 每个参赛方恰好 2 行成员，display_order 1/2
        List<TournamentTeamMember> members = memberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId)
                        .orderByAsc("participant_id", "display_order"));
        assertEquals(8, members.size());
        List<TournamentTeamMember> pair1Members = members.stream()
                .filter(member -> pair1.getId().equals(member.getParticipantId()))
                .toList();
        assertEquals(2, pair1Members.size());
        assertEquals("张三", pair1Members.get(0).getName());
        assertEquals("李四", pair1Members.get(1).getName());
        assertEquals(Integer.valueOf(1), pair1Members.get(0).getDisplayOrder());

        // 个人赛双打不得被 safeParticipantType 误判为团体赛
        //（tournament_team_member 有组合成员行，历史上该表有行即推断为团体赛，V31 起需排除）
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/tournaments/{id}", tournamentId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.participantType").value(0));

        // record 详情：playerCountPerParticipant=2 且双方成员名单正确
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        assertTrue(matches.size() > 0);
        MatchRecord sample = matches.stream()
                .filter(match -> match.getLeftPlayerId() != null && match.getRightPlayerId() != null)
                .findFirst().orElseThrow();
        String leftName = players.stream()
                .filter(player -> player.getId().equals(sample.getLeftPlayerId()))
                .findFirst().map(Player::getName).orElse("");
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/matches/{id}/record", sample.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playerCountPerParticipant").value(2))
                .andExpect(jsonPath("$.data.left.name").value(leftName))
                .andExpect(jsonPath("$.data.left.members.length()").value(2))
                .andExpect(jsonPath("$.data.right.members.length()").value(2));
    }

    @Test
    void mixedDivisions_shouldIsolateEntryPlayerCount() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "新生杯混编",
                  "sportType": 0,
                  "participantType": 0,
                  "refereePassword": "1234567890",
                  "divisions": [
                    {"name": "单打组", "tournamentType": 0, "playerCount": 1,
                     "players": [{"name": "单甲"}, {"name": "单乙"}, {"name": "单丙"}, {"name": "单丁"}]},
                    {"name": "双打组", "tournamentType": 0, "playerCount": 2,
                     "players": [{"members": ["大A", "小a"]}, {"members": ["大B", "小b"]},
                                 {"members": ["大C", "小c"]}, {"members": ["大D", "小d"]}]}
                  ]
                }
                """);

        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId).orderByAsc("sort_order"));
        assertEquals(2, divisions.size());
        assertEquals(Integer.valueOf(1), divisions.get(0).getEntryPlayerCount());
        assertEquals(Integer.valueOf(2), divisions.get(1).getEntryPlayerCount());

        List<Player> singles = playerMapper.selectList(
                new QueryWrapper<Player>().eq("division_id", divisions.get(0).getId()));
        List<Player> doubles = playerMapper.selectList(
                new QueryWrapper<Player>().eq("division_id", divisions.get(1).getId()));
        assertEquals(4, singles.size());
        assertEquals(4, doubles.size());
        assertEquals("大A/小a", doubles.get(0).getName());

        List<TournamentTeamMember> members = memberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>().eq("tournament_id", tournamentId));
        // 成员只属于双打组的 4 个参赛方
        assertEquals(8, members.size());
        assertTrue(members.stream().allMatch(member ->
                doubles.stream().anyMatch(player -> player.getId().equals(member.getParticipantId()))));
    }

    @Test
    void invalidDoublesInputs_shouldBeRejected() throws Exception {
        // 成员 3 人
        createExpectError("""
                {"name": "违规1", "sportType": 0, "participantType": 0, "playerCount": 2,
                 "players": [{"members": ["甲", "乙", "丙"]}, {"members": ["丁", "戊"]}], "refereePassword": "1234567890"}
                """, "必须恰好2名成员");
        // 两人同名
        createExpectError("""
                {"name": "违规2", "sportType": 0, "participantType": 0, "playerCount": 2,
                 "players": [{"members": ["甲", "甲"]}, {"members": ["丁", "戊"]}], "refereePassword": "1234567890"}
                """, "不能同名");
        // 同一名选手出现在两个组合
        createExpectError("""
                {"name": "违规4", "sportType": 0, "participantType": 0, "playerCount": 2,
                 "players": [{"members": ["甲", "乙"]}, {"members": ["甲", "丙"]}], "refereePassword": "1234567890"}
                """, "不能出现在多个双打组合");
        // 成员经空白过滤后只剩 1 人
        createExpectError("""
                {"name": "违规3", "sportType": 0, "participantType": 0, "playerCount": 2,
                 "players": [{"members": ["甲", "  "]}, {"members": ["丁", "戊"]}], "refereePassword": "1234567890"}
                """, "必须恰好2名成员");
    }

    @Test
    void singlesWithoutNewFields_shouldBehaveExactlyAsBefore() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "普通单打赛",
                  "sportType": 0,
                  "participantType": 0,
                  "tournamentType": 0,
                  "refereePassword": "1234567890",
                  "players": [
                    {"name": "甲", "members": ["将被忽略"]},
                    {"name": "乙"},
                    {"name": "丙"},
                    {"name": "丁"}
                  ]
                }
                """);

        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId));
        assertEquals(Integer.valueOf(1), divisions.get(0).getEntryPlayerCount());

        // 单打携带 members 被静默忽略：不落任何成员行
        assertEquals(0L, memberMapper.selectCount(new QueryWrapper<TournamentTeamMember>()
                .eq("tournament_id", tournamentId)).longValue());

        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId));
        MatchRecord sample = matches.stream()
                .filter(match -> match.getLeftPlayerId() != null && match.getRightPlayerId() != null)
                .findFirst().orElseThrow();
        mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/matches/{id}/record", sample.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.playerCountPerParticipant").value(1));
    }
}
