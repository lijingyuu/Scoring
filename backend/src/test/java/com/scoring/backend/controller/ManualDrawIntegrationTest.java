package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
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
 * 手写签表（线下抽签录入）验收测试（docs/手写签表改造方案.md §11）：
 * 1. 创建：manual 模式按签位物化 + 轮空坍缩 + 晋级链传播；draw_mode 落库
 * 2. 校验拦截：双轮空对位 / 下标重复 / 未覆盖 / 长度不匹配 / 非纯淘汰赛
 * 3. 开赛前编辑：全量重提交、match id 不变、幂等；自动抽签赛事与已开赛赛事拒绝
 */
@SpringBootTest(classes = com.scoring.backend.ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:manual_draw_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class ManualDrawIntegrationTest {

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

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-draw");
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
        userMapper.insert(buildUser("user-draw", "openid-draw"));
    }

    /** 5 人手写签表：8 槽 [0,null,3,2,1,null,4,null]（验收用例 1） */
    private static final String MANUAL_BODY = """
            {
              "name": "手写签表测试赛",
              "location": "Gym",
              "sportType": 0,
              "participantType": 0,
              "tournamentType": 0,
              "drawMode": "manual",
              "knockoutSlotOrder": [0, null, 3, 2, 1, null, 4, null],
              "players": [
                {"name": "选手甲"}, {"name": "选手乙"}, {"name": "选手丙"}, {"name": "选手丁"}, {"name": "选手戊"}
              ],
              "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
            }
            """;

    @Test
    void manualCreate_withByes_shouldCollapseAndLink() throws Exception {
        String tournamentId = createAndGetId(MANUAL_BODY);

        TournamentDivision division = soleDivision(tournamentId);
        assertEquals(1, division.getDrawMode());
        assertEquals(3, division.getKnockoutRounds());

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        assertEquals(1, tournament.getStatus());

        List<Player> roster = roster(division.getId());
        assertEquals(5, roster.size());
        String p0 = roster.get(0).getId();
        String p1 = roster.get(1).getId();
        String p2 = roster.get(2).getId();
        String p3 = roster.get(3).getId();
        String p4 = roster.get(4).getId();

        // 首轮 4 场 + 第二轮 2 场 + 决赛 1 场 = 7
        List<MatchRecord> matches = divisionMatches(division.getId());
        assertEquals(7, matches.size());

        MatchRecord m10 = match(division.getId(), 1, 0);
        assertEquals(p0, m10.getLeftPlayerId());
        assertNull(m10.getRightPlayerId());
        assertEquals(p0, m10.getWinnerId());
        assertEquals(2, m10.getStatus());

        MatchRecord m11 = match(division.getId(), 1, 1);
        assertEquals(p3, m11.getLeftPlayerId());
        assertEquals(p2, m11.getRightPlayerId());
        assertNull(m11.getWinnerId());
        assertEquals(0, m11.getStatus());

        MatchRecord m12 = match(division.getId(), 1, 2);
        assertEquals(p1, m12.getLeftPlayerId());
        assertNull(m12.getRightPlayerId());
        assertEquals(p1, m12.getWinnerId());

        MatchRecord m13 = match(division.getId(), 1, 3);
        assertEquals(p4, m13.getLeftPlayerId());
        assertNull(m13.getRightPlayerId());
        assertEquals(p4, m13.getWinnerId());

        // 轮空坍缩传播到第二轮
        MatchRecord m20 = match(division.getId(), 2, 0);
        assertEquals(p0, m20.getLeftPlayerId());
        assertNull(m20.getRightPlayerId());
        assertEquals(0, m20.getStatus());
        MatchRecord m21 = match(division.getId(), 2, 1);
        assertEquals(p1, m21.getLeftPlayerId());
        assertEquals(p4, m21.getRightPlayerId());

        MatchRecord finalMatch = match(division.getId(), 3, 0);
        assertNull(finalMatch.getLeftPlayerId());
        assertNull(finalMatch.getRightPlayerId());
        assertNull(finalMatch.getWinnerId());
        assertNull(finalMatch.getNextMatchId());

        // 晋级链：m10 → m20.left；m11 → m20.right
        assertEquals(m20.getId(), m10.getNextMatchId());
        assertEquals("left", m10.getNextMatchSlot());
        assertEquals(m20.getId(), m11.getNextMatchId());
        assertEquals("right", m11.getNextMatchSlot());

        // bracket VO 暴露 drawMode，小程序零改动可渲染
        mockMvc.perform(get("/api/v1/tournaments/{id}/divisions/{did}/bracket", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.drawMode").value(1))
                .andExpect(jsonPath("$.data.matches.length()").value(7));
    }

    @Test
    void manualCreate_doubleByePair_shouldReject() throws Exception {
        // 6 人 8 槽，全部放置但签位 3、4 相邻双轮空
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "双轮空测试",
                                  "sportType": 0,
                                  "tournamentType": 0,
                                  "drawMode": "manual",
                                  "knockoutSlotOrder": [0, 1, null, null, 2, 3, 4, 5],
                                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"},{"name":"e"},{"name":"f"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCreate_duplicateIndex_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "重复下标测试",
                                  "sportType": 0,
                                  "tournamentType": 0,
                                  "drawMode": "manual",
                                  "knockoutSlotOrder": [0, 0, 1, 2, 3, null, null, null],
                                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"},{"name":"e"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCreate_missingCoverage_shouldReject() throws Exception {
        // 5 人只放置 4 人
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "漏人测试",
                                  "sportType": 0,
                                  "tournamentType": 0,
                                  "drawMode": "manual",
                                  "knockoutSlotOrder": [0, 1, 2, 3, null, null, null, null],
                                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"},{"name":"e"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCreate_wrongLength_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "长度测试",
                                  "sportType": 0,
                                  "tournamentType": 0,
                                  "drawMode": "manual",
                                  "knockoutSlotOrder": [0, 1, 2, 3],
                                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"},{"name":"e"}],
                                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCreate_nonKnockoutType_shouldReject() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "赛制限制测试",
                                  "sportType": 0,
                                  "tournamentType": 2,
                                  "roundRobinRounds": 1,
                                  "drawMode": "manual",
                                  "players": [{"name":"a"},{"name":"b"},{"name":"c"}],
                                  "rule": {"bestOf": 1, "gamesToWin": 1, "pointsToWin": 11, "enableDeuce": true, "capPoint": 15}
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void autoCreate_regression_shouldStayAuto() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "自动抽签回归",
                  "sportType": 0,
                  "tournamentType": 0,
                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"}],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        assertEquals(0, division.getDrawMode());
        assertEquals(3, divisionMatches(division.getId()).size());
    }

    @Test
    void updateDrawSlots_beforeStart_shouldRewriteInPlaceAndIdempotent() throws Exception {
        String tournamentId = createAndGetId(MANUAL_BODY);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());
        String p0 = roster.get(0).getId();
        String p1 = roster.get(1).getId();
        String p2 = roster.get(2).getId();
        String p3 = roster.get(3).getId();
        String p4 = roster.get(4).getId();

        List<String> beforeIds = divisionMatches(division.getId()).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());

        // 新签位：[null,4, 2,0, 3,null, 1,null] —— 轮空从 2/6/8 号位挪到 1/6/8 号位
        String body = "{\"knockoutSlotOrder\":" + objectMapper.writeValueAsString(java.util.Arrays.asList(null, p4, p2, p0, p3, null, p1, null)) + "}";
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // match id 集合不变（原地更新）
        List<String> afterIds = divisionMatches(division.getId()).stream()
                .map(MatchRecord::getId).sorted().collect(Collectors.toList());
        assertEquals(beforeIds, afterIds);

        MatchRecord m10 = match(division.getId(), 1, 0);
        assertNull(m10.getLeftPlayerId());
        assertEquals(p4, m10.getRightPlayerId());
        assertEquals(p4, m10.getWinnerId());
        assertEquals(2, m10.getStatus());

        MatchRecord m11 = match(division.getId(), 1, 1);
        assertEquals(p2, m11.getLeftPlayerId());
        assertEquals(p0, m11.getRightPlayerId());
        assertNull(m11.getWinnerId());
        assertEquals(0, m11.getStatus());

        // 旧传播结果被清理并按新签位重放：m20.left 从 p0 变为 p4；m21.left 从 p1 变为 p3
        MatchRecord m20 = match(division.getId(), 2, 0);
        assertEquals(p4, m20.getLeftPlayerId());
        assertNull(m20.getRightPlayerId());
        MatchRecord m21 = match(division.getId(), 2, 1);
        assertEquals(p3, m21.getLeftPlayerId());
        assertEquals(p1, m21.getRightPlayerId());

        MatchRecord finalMatch = match(division.getId(), 3, 0);
        assertNull(finalMatch.getLeftPlayerId());
        assertNull(finalMatch.getRightPlayerId());

        // 幂等：同内容重复提交成功且无副作用
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(p4, match(division.getId(), 2, 0).getLeftPlayerId());
        assertNotEquals(p0, match(division.getId(), 1, 0).getWinnerId());
    }

    @Test
    void updateDrawSlots_afterEvent_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_BODY);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        MatchEvent event = new MatchEvent();
        event.setMatchId(match(division.getId(), 1, 1).getId());
        event.setEventSeq(1);
        event.setEventType("SCORE");
        event.setServeSide("left");
        event.setPayloadJson("{}");
        event.setGameNo(1);
        event.setLeftScore(1);
        event.setRightScore(0);
        matchEventMapper.insert(event);

        String body = validOrderBody(roster);
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        // 原签表未被破坏
        assertNull(match(division.getId(), 1, 0).getRightPlayerId());
    }

    @Test
    void updateDrawSlots_autoDrawTournament_shouldReject() throws Exception {
        String tournamentId = createAndGetId("""
                {
                  "name": "自动签表不可编辑",
                  "sportType": 0,
                  "tournamentType": 0,
                  "players": [{"name":"a"},{"name":"b"},{"name":"c"},{"name":"d"}],
                  "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());
        String body = "{\"knockoutSlotOrder\":" + objectMapper.writeValueAsString(
                roster.stream().map(Player::getId).collect(Collectors.toList())) + "}";
        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateDrawSlots_nonCreator_shouldReject() throws Exception {
        String tournamentId = createAndGetId(MANUAL_BODY);
        TournamentDivision division = soleDivision(tournamentId);
        List<Player> roster = roster(division.getId());

        when(authService.verifyToken("other-token")).thenReturn("user-other");

        mockMvc.perform(put("/api/v1/tournaments/{id}/divisions/{did}/draw-slots", tournamentId, division.getId())
                        .header("Authorization", "Bearer other-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderBody(roster)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manualCreate_divisionSpecPath_shouldWork() throws Exception {
        // divisions[] 显式组别路径（组别级 drawMode/knockoutSlotOrder 下标指向该组别 players）
        String tournamentId = createAndGetId("""
                {
                  "name": "组别路径手写签表",
                  "sportType": 0,
                  "participantType": 0,
                  "divisions": [
                    {
                      "name": "甲组",
                      "tournamentType": 0,
                      "drawMode": "manual",
                      "knockoutSlotOrder": [0, 1, 2, null],
                      "players": [{"name":"a"},{"name":"b"},{"name":"c"}],
                      "rule": {"bestOf": 3, "gamesToWin": 2, "pointsToWin": 21, "enableDeuce": true, "capPoint": 30}
                    }
                  ]
                }
                """);
        TournamentDivision division = soleDivision(tournamentId);
        assertEquals(1, division.getDrawMode());

        List<Player> roster = roster(division.getId());
        MatchRecord m11 = match(division.getId(), 1, 1);
        assertNull(m11.getRightPlayerId());
        assertEquals(roster.get(2).getId(), m11.getLeftPlayerId());
        assertEquals(roster.get(2).getId(), m11.getWinnerId());
        assertEquals(roster.get(2).getId(), match(division.getId(), 2, 0).getRightPlayerId());
    }

    // ---------- helpers ----------

    /** 5 人名册的合法签位（轮空位于 1/3/8 号位，无相邻双轮空）。 */
    private String validOrderBody(List<Player> roster) throws Exception {
        return "{\"knockoutSlotOrder\":" + objectMapper.writeValueAsString(
                java.util.Arrays.asList(null, roster.get(0).getId(), null, roster.get(1).getId(),
                        roster.get(2).getId(), roster.get(3).getId(), roster.get(4).getId(), null)) + "}";
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

    private List<MatchRecord> divisionMatches(String divisionId) {
        return matchRecordMapper.selectList(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .orderByAsc("round_num", "match_index"));
    }

    private MatchRecord match(String divisionId, int roundNum, int matchIndex) {
        MatchRecord record = matchRecordMapper.selectOne(new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .eq("round_num", roundNum)
                .eq("match_index", matchIndex)
                .eq("match_role", 0));
        assertNotNull(record, "match not found: round=" + roundNum + " index=" + matchIndex);
        return record;
    }

    private User buildUser(String id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname("manual-draw");
        user.setProfileCompleted(true);
        return user;
    }
}
