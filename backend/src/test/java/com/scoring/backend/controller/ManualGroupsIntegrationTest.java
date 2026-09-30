package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.MatchThemeConfigMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentRankingConfigMapper;
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

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 手写分组（小组赛+淘汰赛）验收测试：
 * 1. 创建：manual-groups 按入参 groups 物化 group_no/group_position（允许组间不均）+
 *    小组赛赛程（stage_type=0）+ draw_mode=2 落库；
 * 2. 校验拦截：组数不符 / 某组小于下限 / 下标重复 / 漏人 / 模式与赛制交叉；
 * 3. group-assignments：首场小组赛开赛前整体重分组（全删全建赛程、幂等）；
 *    已开赛 / auto 赛事 / 非创建者 / 淘汰赛已生成拒绝；
 * 4. draw-slots（type1）：淘汰赛生成后、首场淘汰赛开赛前整体重排（原地更新、幂等）；
 *    未生成 / 轮空 / 非出线者 / 已开赛拒绝。
 */
@SpringBootTest(classes = com.scoring.backend.ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual_groups_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class ManualGroupsIntegrationTest {

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
    private TournamentDivisionMapper tournamentDivisionMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @Autowired
    private MatchLineupConfigMapper matchLineupConfigMapper;

    @Autowired
    private MatchReportMetaMapper matchReportMetaMapper;

    @Autowired
    private MatchThemeConfigMapper matchThemeConfigMapper;

    @Autowired
    private TeamMatchItemMapper teamMatchItemMapper;

    @Autowired
    private TournamentRankingConfigMapper tournamentRankingConfigMapper;

    @Autowired
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Autowired
    private TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    private static final String CREATOR = "user-groups";

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn(CREATOR);
        matchEventMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        matchReportMetaMapper.delete(new QueryWrapper<>());
        matchThemeConfigMapper.delete(new QueryWrapper<>());
        teamMatchItemMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentRoundRuleMapper.delete(new QueryWrapper<>());
        tournamentRankingConfigMapper.delete(new QueryWrapper<>());
        tournamentQualificationOverrideMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser(CREATOR, "openid-groups"));
    }

    /** 10 人 4 组（不均 2/3/2/3）：knockoutSlots=8、qualifiersPerGroup=2。 */
    private static final String MANUAL_GROUPS_BODY_10 = """
            {
              "name": "手写分组测试赛",
              "location": "Gym",
              "sportType": 0,
              "participantType": 0,
              "tournamentType": 1,
              "drawMode": "manual-groups",
              "knockoutSlots": 8,
              "qualifiersPerGroup": 2,
              "groups": [[0, 4], [1, 2, 3], [5, 6], [7, 8, 9]],
              "players": [
                {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"}, {"name": "p4"},
                {"name": "p5"}, {"name": "p6"}, {"name": "p7"}, {"name": "p8"}, {"name": "p9"}
              ],
              "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
            }
            """;

    /** 4 人 2 组（每组 2 人、每组出线 1 人）：用于走完小组赛 → 生成淘汰赛 → 重排签位链路。 */
    private static final String MANUAL_GROUPS_BODY_4 = """
            {
              "name": "手写分组淘汰赛链路赛",
              "sportType": 0,
              "participantType": 0,
              "tournamentType": 1,
              "drawMode": "manual-groups",
              "knockoutSlots": 2,
              "qualifiersPerGroup": 1,
              "groups": [[0, 1], [2, 3]],
              "players": [
                {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"}
              ],
              "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
            }
            """;

    // ============ 创建 ============

    @Test
    void manualGroupsCreate_unevenGroups_shouldAssignAndGenerateSchedule() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_10);
        TournamentDivision division = soleDivision(tournamentId);

        assertEquals(2, division.getDrawMode());
        assertEquals(8, division.getKnockoutSlots());
        assertEquals(2, division.getQualifiersPerGroup());
        assertEquals(3, division.getGroupSize());      // ceil(10/4)，展示口径
        assertEquals(0, division.getCurrentStage());
        assertEquals(false, division.getKnockoutGenerated());

        List<Player> roster = roster(division.getId());
        assertEquals(10, roster.size());
        assertAssignment(roster.get(0), 1, 1);
        assertAssignment(roster.get(4), 1, 2);
        assertAssignment(roster.get(1), 2, 1);
        assertAssignment(roster.get(2), 2, 2);
        assertAssignment(roster.get(3), 2, 3);
        assertAssignment(roster.get(5), 3, 1);
        assertAssignment(roster.get(6), 3, 2);
        assertAssignment(roster.get(7), 4, 1);
        assertAssignment(roster.get(8), 4, 2);
        assertAssignment(roster.get(9), 4, 3);

        // 小组赛场次按组人数生成：C(2,2)=1 + C(3,2)=3 + 1 + 3 = 8 场
        List<MatchRecord> groupStage = stageMatches(division.getId(), 0);
        assertEquals(8, groupStage.size());
        Map<Integer, Long> byGroup = groupStage.stream()
                .collect(Collectors.groupingBy(MatchRecord::getGroupNo, Collectors.counting()));
        assertEquals(1L, byGroup.get(1));
        assertEquals(3L, byGroup.get(2));
        assertEquals(1L, byGroup.get(3));
        assertEquals(3L, byGroup.get(4));

        // 1 号组（p0,p1 座次）唯一一场：p0 vs p4
        MatchRecord group1 = match(division.getId(), 1, 0, 0, 1);
        assertEquals(roster.get(0).getId(), group1.getLeftPlayerId());
        assertEquals(roster.get(4).getId(), group1.getRightPlayerId());
        assertEquals(0, group1.getStatus());
        assertNull(group1.getWinnerId());

        // bracket VO 暴露 drawMode=2，淘汰赛阶段为空
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions/{did}/bracket", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drawMode").value(2))
                .andExpect(jsonPath("$.data.matches.length()").value(0));
    }

    @Test
    void manualGroupsCreate_wrongGroupCount_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "组数不符",
                                  "sportType": 0,
                                  "tournamentType": 1,
                                  "drawMode": "manual-groups",
                                  "knockoutSlots": 8,
                                  "qualifiersPerGroup": 2,
                                  "groups": [[0, 4], [1, 2], [5, 6], [7, 8]],
                                  "players": [
                                    {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"}, {"name": "p4"},
                                    {"name": "p5"}, {"name": "p6"}, {"name": "p7"}, {"name": "p8"}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGroupsCreate_groupTooSmall_shouldReject() throws Exception {
        // qualifiers=2 → 每组至少 2 人，第 4 组只有 1 人（允许不均，但不允许低于下限）
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "组过小",
                                  "sportType": 0,
                                  "tournamentType": 1,
                                  "drawMode": "manual-groups",
                                  "knockoutSlots": 8,
                                  "qualifiersPerGroup": 2,
                                  "groups": [[0, 4], [1, 2, 3], [5, 6], [7]],
                                  "players": [
                                    {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"}, {"name": "p4"},
                                    {"name": "p5"}, {"name": "p6"}, {"name": "p7"}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGroupsCreate_duplicateIndex_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "重复下标",
                                  "sportType": 0,
                                  "tournamentType": 1,
                                  "drawMode": "manual-groups",
                                  "knockoutSlots": 8,
                                  "qualifiersPerGroup": 2,
                                  "groups": [[0, 0], [1, 2], [3, 4], [5, 6]],
                                  "players": [
                                    {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"},
                                    {"name": "p4"}, {"name": "p5"}, {"name": "p6"}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGroupsCreate_missingCoverage_shouldReject() throws Exception {
        // 8 人只放 7 人
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "漏人",
                                  "sportType": 0,
                                  "tournamentType": 1,
                                  "drawMode": "manual-groups",
                                  "knockoutSlots": 8,
                                  "qualifiersPerGroup": 2,
                                  "groups": [[0, 4], [1, 2], [3, 6], [5, 7]],
                                  "players": [
                                    {"name": "p0"}, {"name": "p1"}, {"name": "p2"}, {"name": "p3"},
                                    {"name": "p4"}, {"name": "p5"}, {"name": "p6"}, {"name": "p7"}, {"name": "p8"}
                                  ],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGroupsCreate_typeMismatch_shouldReject() throws Exception {
        // type0 + manual-groups
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "模式赛制交叉1",
                                  "sportType": 0,
                                  "tournamentType": 0,
                                  "drawMode": "manual-groups",
                                  "knockoutSlotOrder": [0, 1],
                                  "players": [{"name": "a"}, {"name": "b"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
        // type1 + manual（type0 的手写签表模式）
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "模式赛制交叉2",
                                  "sportType": 0,
                                  "tournamentType": 1,
                                  "drawMode": "manual",
                                  "knockoutSlots": 4,
                                  "qualifiersPerGroup": 2,
                                  "knockoutSlotOrder": [0, 1, 2, 3],
                                  "players": [{"name": "a"}, {"name": "b"}, {"name": "c"}, {"name": "d"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualGroupsCreate_divisionSpecPath_shouldWork() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "组别路径手写分组",
                  "sportType": 0,
                  "participantType": 0,
                  "divisions": [
                    {
                      "name": "甲组",
                      "tournamentType": 1,
                      "drawMode": "manual-groups",
                      "knockoutSlots": 4,
                      "qualifiersPerGroup": 2,
                      "groups": [[1, 0], [2, 3]],
                      "players": [{"name": "a"}, {"name": "b"}, {"name": "c"}, {"name": "d"}],
                      "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                    }
                  ]
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        assertEquals(2, division.getDrawMode());

        List<Player> roster = roster(division.getId());
        assertAssignment(roster.get(1), 1, 1);
        assertAssignment(roster.get(0), 1, 2);
        assertAssignment(roster.get(2), 2, 1);
        assertAssignment(roster.get(3), 2, 2);
        assertEquals(2, stageMatches(division.getId(), 0).size());
    }

    @Test
    void autoGroupsCreate_regression_shouldStaySnake() throws Exception {
        // 不带 drawMode 的 type1 创建回归：蛇形路径不变，人人有组
        String tournamentId = createAndGetId("""
                {
                  "name": "自动分组回归",
                  "sportType": 0,
                  "tournamentType": 1,
                  "knockoutSlots": 8,
                  "qualifiersPerGroup": 2,
                  "players": [
                    {"name": "a"}, {"name": "b"}, {"name": "c"}, {"name": "d"},
                    {"name": "e"}, {"name": "f"}, {"name": "g"}, {"name": "h"}
                  ],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        assertEquals(0, division.getDrawMode());
        List<Player> roster = roster(division.getId());
        assertEquals(8, roster.size());
        for (Player player : roster) {
            assertNotNull(player.getGroupNo(), "auto 分组后每人应有组号");
            assertNotNull(player.getGroupPosition());
        }
    }

    // ============ group-assignments（重新分组） ============

    @Test
    void updateGroupAssignments_beforeStart_shouldRewriteAndIdempotent() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_10);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());
        String p0 = roster.get(0).getId();
        String p4 = roster.get(4).getId();
        String p9 = roster.get(9).getId();

        List<String> originalIds = stageMatches(division.getId(), 0).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());

        // 新分组：组内顺序也调换（p4 打头），组间人数仍 2/3/2/3
        String body = groupsBody(new String[][]{
                {p4, p0},
                {roster.get(3).getId(), roster.get(2).getId(), roster.get(1).getId()},
                {roster.get(6).getId(), roster.get(5).getId()},
                {p9, roster.get(8).getId(), roster.get(7).getId()}
        });
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<Player> updated = roster(division.getId());
        assertAssignment(updated.get(4), 1, 1);
        assertAssignment(updated.get(0), 1, 2);
        assertAssignment(updated.get(3), 2, 1);
        assertAssignment(updated.get(9), 4, 1);

        // 赛程全删全建：match id 全部更换，场次数不变
        List<String> rebuiltIds = stageMatches(division.getId(), 0).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());
        assertEquals(originalIds.size(), rebuiltIds.size());
        assertNotEquals(originalIds, rebuiltIds);
        MatchRecord group1First = match(division.getId(), 1, 0, 0, 1);
        assertEquals(p4, group1First.getLeftPlayerId());
        assertEquals(p0, group1First.getRightPlayerId());

        // 幂等：同内容重复提交，match id 集合稳定（不重建）
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        List<String> idempotentIds = stageMatches(division.getId(), 0).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());
        assertEquals(rebuiltIds, idempotentIds);
    }

    @Test
    void updateGroupAssignments_afterEvent_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_10);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        MatchRecord anyGroupMatch = stageMatches(division.getId(), 0).get(0);
        MatchEvent event = new MatchEvent();
        event.setMatchId(anyGroupMatch.getId());
        event.setEventSeq(1);
        event.setEventType("SCORE");
        event.setServeSide("left");
        event.setPayloadJson("{}");
        event.setGameNo(1);
        event.setLeftScore(1);
        event.setRightScore(0);
        matchEventMapper.insert(event);

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupsBody(new String[][]{
                                {roster.get(0).getId(), roster.get(1).getId()},
                                {roster.get(2).getId(), roster.get(3).getId()},
                                {roster.get(4).getId(), roster.get(5).getId()},
                                {roster.get(6).getId(), roster.get(7).getId()}
                        })))
                .andExpect(status().isBadRequest());

        // 分组与赛程未被破坏（该 body 本身组数合法但漏了 p8/p9，且被开赛守卫先拦截）
        assertAssignment(roster(division.getId()).get(0), 1, 1);
        assertEquals(8, stageMatches(division.getId(), 0).size());
    }

    @Test
    void updateGroupAssignments_autoTournament_shouldReject() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "auto 不可重分组",
                  "sportType": 0,
                  "tournamentType": 1,
                  "knockoutSlots": 4,
                  "qualifiersPerGroup": 2,
                  "players": [{"name": "a"}, {"name": "b"}, {"name": "c"}, {"name": "d"}],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupsBody(new String[][]{
                                {roster.get(0).getId(), roster.get(1).getId()},
                                {roster.get(2).getId(), roster.get(3).getId()}
                        })))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGroupAssignments_nonCreator_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        when(authService.verifyToken("other-token")).thenReturn("user-other");

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupsBody(new String[][]{
                                {roster.get(1).getId(), roster.get(0).getId()},
                                {roster.get(3).getId(), roster.get(2).getId()}
                        })))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateGroupAssignments_afterKnockoutGenerated_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        finishAllGroupMatches(division.getId());
        generateKnockout(tournamentId, division.getId());

        List<Player> roster = roster(division.getId());
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/group-assignments", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupsBody(new String[][]{
                                {roster.get(1).getId(), roster.get(0).getId()},
                                {roster.get(3).getId(), roster.get(2).getId()}
                        })))
                .andExpect(status().isBadRequest());
        assertAssignment(roster(division.getId()).get(0), 1, 1);
    }

    // ============ draw-slots（type1 淘汰赛签位重排） ============

    @Test
    void updateDrawSlots_type1_afterKnockoutGenerated_shouldRewriteInPlace() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        finishAllGroupMatches(division.getId());
        generateKnockout(tournamentId, division.getId());

        List<Player> roster = roster(division.getId());
        String p0 = roster.get(0).getId();
        String p2 = roster.get(2).getId();

        // 生成默认签位：各小组第 1 按 groupNo 升序 → [p0, p2]
        MatchRecord finalMatch = match(division.getId(), 1, 0, 1, null);
        assertEquals(p0, finalMatch.getLeftPlayerId());
        assertEquals(p2, finalMatch.getRightPlayerId());
        List<String> beforeIds = stageMatches(division.getId(), 1).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());

        // 整体重排：[p2, p0]
        String body = slotsBody(p2, p0);
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchRecord rewritten = match(division.getId(), 1, 0, 1, null);
        assertEquals(p2, rewritten.getLeftPlayerId());
        assertEquals(p0, rewritten.getRightPlayerId());
        assertNull(rewritten.getWinnerId());
        assertEquals(0, rewritten.getStatus());

        // 原地更新：match id 不变；幂等
        List<String> afterIds = stageMatches(division.getId(), 1).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());
        assertEquals(beforeIds, afterIds);
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(p2, match(division.getId(), 1, 0, 1, null).getLeftPlayerId());
    }

    @Test
    void updateDrawSlots_type1_beforeKnockoutGenerated_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotsBody(roster.get(0).getId(), roster.get(2).getId())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDrawSlots_type1_nullOrNonQualifier_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        finishAllGroupMatches(division.getId());
        generateKnockout(tournamentId, division.getId());

        List<Player> roster = roster(division.getId());
        String p0 = roster.get(0).getId();
        String p1 = roster.get(1).getId();
        String p2 = roster.get(2).getId();

        // 轮空：type1 淘汰赛签位不存在轮空
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotsBody(null, p0)))
                .andExpect(status().isBadRequest());
        // 非出线者：p1 小组赛已出局
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotsBody(p0, p1)))
                .andExpect(status().isBadRequest());

        assertEquals(p0, match(division.getId(), 1, 0, 1, null).getLeftPlayerId());
    }

    @Test
    void updateDrawSlots_type1_afterKnockoutStarted_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_GROUPS_BODY_4);
        TournamentDivision division = soleDivision(tournamentId);
        finishAllGroupMatches(division.getId());
        generateKnockout(tournamentId, division.getId());

        List<Player> roster = roster(division.getId());
        MatchRecord knockoutMatch = match(division.getId(), 1, 0, 1, null);
        MatchEvent event = new MatchEvent();
        event.setMatchId(knockoutMatch.getId());
        event.setEventSeq(1);
        event.setEventType("SCORE");
        event.setServeSide("left");
        event.setPayloadJson("{}");
        event.setGameNo(1);
        event.setLeftScore(1);
        event.setRightScore(0);
        matchEventMapper.insert(event);

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(slotsBody(roster.get(2).getId(), roster.get(0).getId())))
                .andExpect(status().isBadRequest());

        // 原签位未被破坏
        assertEquals(roster.get(0).getId(), knockoutMatch.getLeftPlayerId());
    }

    // ---------- helpers ----------

    private void assertAssignment(Player player, int groupNo, int groupPosition) {
        assertEquals(groupNo, player.getGroupNo(), player.getName() + " groupNo");
        assertEquals(groupPosition, player.getGroupPosition(), player.getName() + " groupPosition");
    }

    private String groupsBody(String[][] groups) throws Exception {
        String[][] trimmed = groups;
        return "{\"groups\":" + objectMapper.writeValueAsString(trimmed) + "}";
    }

    private String slotsBody(String... slotIds) throws Exception {
        return "{\"knockoutSlotOrder\":" + objectMapper.writeValueAsString(java.util.Arrays.asList(slotIds)) + "}";
    }

    /** 走完该组别全部小组赛（每场左侧胜）：每组第 1 名唯一、无跨线平局。 */
    private void finishAllGroupMatches(String divisionId) throws Exception {
        List<MatchRecord> groupStage = stageMatches(divisionId, 0);
        for (MatchRecord matchRecord : groupStage) {
            String winnerId = matchRecord.getLeftPlayerId();
            String winnerSide = "left";
            String body = """
                    {
                      "winnerSide": "%s",
                      "leftScore": 2,
                      "rightScore": 0,
                      "leftGameWins": 2,
                      "rightGameWins": 0,
                      "gameScores": [
                        {"gameNo":1,"leftScore":21,"rightScore":15,"winnerSide":"left"},
                        {"gameNo":2,"leftScore":21,"rightScore":15,"winnerSide":"left"}
                      ]
                    }
                    """.formatted(winnerSide);
            mockMvc.perform(put("/api/v1/matches/{id}/finish", matchRecord.getId())
                            .header("Authorization", "Bearer test-token")
                            .with(MatchLockTestSupport.withMatchLock(matchRecordMapper, matchRecord.getId(), CREATOR))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    private void generateKnockout(String tournamentId, String divisionId) throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/{id}/divisions/{did}/generate-knockout", tournamentId, divisionId)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    private String createAndGetId(String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tournamentId").asText();
    }

    private TournamentDivision soleDivision(String tournamentId) {
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId));
        assertEquals(1, divisions.size());
        return divisions.get(0);
    }

    private List<Player> roster(String divisionId) {
        return playerMapper.selectList(new QueryWrapper<Player>()
                .eq("division_id", divisionId)
                .orderByAsc("create_time", "id"));
    }

    private List<MatchRecord> stageMatches(String divisionId, int stageType) {
        return matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .eq("stage_type", stageType)
                .orderByAsc("round_num", "match_index"));
    }

    /** 组别内单场定位（stage_type 区分小组赛/淘汰赛；小组赛需带 groupNo——match_index 每轮从 0 起，跨组会重号）。 */
    private MatchRecord match(String divisionId, int roundNum, int matchIndex, int stageType, Integer groupNo) {
        QueryWrapper<MatchRecord> wrapper = new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .eq("stage_type", stageType)
                .eq("round_num", roundNum)
                .eq("match_index", matchIndex)
                .eq("match_role", 0);
        if (groupNo != null) {
            wrapper.eq("group_no", groupNo);
        }
        MatchRecord record = matchRecordMapper.selectOne(wrapper);
        assertNotNull(record, "match not found: stage=" + stageType + " group=" + groupNo
                + " round=" + roundNum + " index=" + matchIndex);
        return record;
    }
    private User buildUser(String id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname("manual-groups");
        user.setProfileCompleted(true);
        return user;
    }
}
