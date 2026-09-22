package com.scoring.backend.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.scoring.backend.controller.MatchLockTestSupport.withMatchLock;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:match_event_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchEventIntegrationTest {

    @Autowired
    private com.scoring.backend.mapper.TournamentDivisionMapper tournamentDivisionMapper;

    private static final String TOURNAMENT_ID = "t-1";
    private static final String MATCH_ID = "m-1";

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
    private MatchLineupConfigMapper matchLineupConfigMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @Autowired
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchEventMapper.delete(new QueryWrapper<>());
        matchLineupConfigMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        tournamentTeamMemberMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentRefereeGrantMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1", "openid-user-1"));
        prepareMatch();
    }

    @Test
    void saveMatchEvents_shouldBeIdempotentByMatchAndSeq() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "roster_snapshot", 1, 0, 0, "left", "{\"leftMembers\":[\"l1\"],\"rightMembers\":[\"r1\"]}"),
                buildEvent(2, "captain_change", 1, 0, 0, "left", "{\"side\":\"left\",\"captainMemberId\":\"l1\",\"source\":\"auto\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        List<MatchEvent> events = matchEventMapper.selectList(
                new QueryWrapper<MatchEvent>()
                        .eq("match_id", MATCH_ID)
                        .orderByAsc("event_seq")
        );
        assertEquals(2, events.size());
        assertEquals("roster_snapshot", events.get(0).getEventType());
        assertEquals("captain_change", events.get(1).getEventType());
    }

    @Test
    void saveMatchEvents_shouldAcceptScoreSnapshotAndRenderIt() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "score_snapshot", 1, 4, 2, "right", "{\"side\":\"left\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events[0].eventType").value("score_snapshot"))
                .andExpect(jsonPath("$.data.events[0].eventTypeLabel").value("比分更新"))
                .andExpect(jsonPath("$.data.events[0].summary").value("第 1 局比分更新"))
                .andExpect(jsonPath("$.data.events[0].detailLines[0]").value("比分 4:2"));
    }

    @Test
    void getMatchRecord_shouldReturnAggregatedRecord() throws Exception {
        grantReferee("user-1");
        mockMvc.perform(put("/api/v1/matches/{id}/report-meta", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "matchTypeLabel", "混排小组赛",
                                "matchTimeText", "2026-06-12 19:30",
                                "initialCoinTossServeTeam", "A",
                                "initialCoinTossChooseSideTeam", "B",
                                "decidingSetCoinTossEnabled", true,
                                "decidingSetCoinTossServeTeam", "B",
                                "decidingSetCoinTossChooseSideTeam", "A",
                                "chiefRefereeName", "主裁甲",
                                "assistantRefereeName", "副裁乙"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchLineupConfig lineupConfig = new MatchLineupConfig();
        lineupConfig.setId("lineup-1");
        lineupConfig.setMatchId(MATCH_ID);
        lineupConfig.setGameNo(1);
        lineupConfig.setLeftCourtJson("[\"l1\",\"l2\",\"l3\",\"l4\",\"l5\",\"l6\"]");
        lineupConfig.setRightCourtJson("[\"r1\",\"r2\",\"r3\",\"r4\",\"r5\",\"r6\"]");
        lineupConfig.setLeftMiddlePairIndexesJson("[1,4]");
        lineupConfig.setRightMiddlePairIndexesJson("[]");
        lineupConfig.setLeftLibero1Id("l7");
        lineupConfig.setLeftLibero2Id("");
        lineupConfig.setRightLibero1Id("");
        lineupConfig.setRightLibero2Id("");
        lineupConfig.setServeSide("left");
        matchLineupConfigMapper.insert(lineupConfig);

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "roster_snapshot", 1, 0, 0, "left", "{\"leftMembers\":[{\"id\":\"l1\",\"name\":\"甲一\",\"jerseyNumber\":1,\"captain\":true,\"libero\":false}],\"rightMembers\":[{\"id\":\"r1\",\"name\":\"乙一\",\"jerseyNumber\":2,\"captain\":false,\"libero\":true}]}"),
                buildEvent(2, "lineup_snapshot", 1, 0, 0, "left", "{\"left\":{\"court\":[\"l1\",\"l2\",\"l3\",\"l4\",\"l5\",\"l6\"],\"middlePairIndexes\":[1,4],\"libero1Id\":\"l7\",\"libero2Id\":\"\"},\"right\":{\"court\":[\"r1\",\"r2\",\"r3\",\"r4\",\"r5\",\"r6\"],\"middlePairIndexes\":[],\"libero1Id\":\"\",\"libero2Id\":\"\"},\"serveSide\":\"left\"}"),
                buildEvent(3, "substitution", 1, 4, 3, "right", "{\"side\":\"left\",\"outMemberId\":\"l1\",\"inMemberId\":\"l8\"}"),
                buildEvent(4, "timeout", 1, 8, 7, "right", "{\"side\":\"left\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchId").value(MATCH_ID))
                .andExpect(jsonPath("$.data.tournamentName").value("test"))
                .andExpect(jsonPath("$.data.left.name").value("Left Team"))
                .andExpect(jsonPath("$.data.right.name").value("Right Team"))
                .andExpect(jsonPath("$.data.reportMeta.matchTypeLabel").value("混排小组赛"))
                .andExpect(jsonPath("$.data.reportRender.header.matchTimeText").value("2026-06-12 19:30"))
                .andExpect(jsonPath("$.data.reportRender.header.teamSummaryText").value("A队：Left Team / B队：Right Team"))
                .andExpect(jsonPath("$.data.reportRender.header.scoreWinnerText").value("胜方待确认"))
                .andExpect(jsonPath("$.data.reportRender.header.scoreSummaryText").value("A队 0:0 B队，胜方待确认"))
                .andExpect(jsonPath("$.data.reportRender.signatures.chiefRefereeName").value("主裁甲"))
                .andExpect(jsonPath("$.data.reportRender.signatures.assistantRefereeName").value("副裁乙"))
                .andExpect(jsonPath("$.data.reportRender.coinTossBlocks[0].text").value("猜边结果：A队发球，B队选边"))
                .andExpect(jsonPath("$.data.reportRender.games[0].leftRotationGrid[0].primaryJerseyNumber").value(1))
                .andExpect(jsonPath("$.data.reportRender.games[0].leftRotationGrid[0].secondaryJerseyNumber").value(8))
                .andExpect(jsonPath("$.data.reportRender.games[0].leftRotationGrid[1].secondaryJerseyNumber").value(7))
                .andExpect(jsonPath("$.data.reportRender.games[0].timeoutLines[0]").value("A队暂停 8:7 B队发球"))
                .andExpect(jsonPath("$.data.events[3].eventType").value("timeout"))
                .andExpect(jsonPath("$.data.events[3].payloadJson").value("{\"side\":\"left\"}"));
    }

    @Test
    void saveMatchEvents_sameSeqDifferentPayload_shouldReturn409AndRejectWholeBatch() throws Exception {
        Map<String, Object> first = new LinkedHashMap<>();
        first.put("events", List.of(
                buildEvent(1, "timeout", 1, 1, 0, "left", "{\"side\":\"left\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 双设备执裁/清缓存后新设备 eventSeq 从 1 重来：撞号且内容不同
        Map<String, Object> conflict = new LinkedHashMap<>();
        conflict.put("events", List.of(
                buildEvent(1, "timeout", 1, 9, 8, "left", "{\"side\":\"right\"}"),
                buildEvent(2, "substitution", 1, 9, 8, "left", "{\"side\":\"left\",\"outMemberId\":\"l1\",\"inMemberId\":\"l8\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflict)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409))
                .andExpect(jsonPath("$.message").value(containsString("事件序号与已有记录冲突")))
                .andExpect(jsonPath("$.message").value(containsString("服务端最大序号 1")));

        // 整批拒绝：冲突事件与同批新增的 seq 2 都不落库
        assertEquals(1, matchEventMapper.selectCount(
                new QueryWrapper<MatchEvent>().eq("match_id", MATCH_ID)
        ));
    }

    @Test
    void getMatchRecord_shouldNotRenderEventsRevertedByUndo() throws Exception {
        tournamentTeamMemberMapper.insert(buildMember("l9", "p-left", "L9", 9, false, false));

        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "lineup_snapshot", 1, 0, 0, "left", buildLineupPayload()),
                // 误点换人（l1 → l8），已被 undo 撤销
                buildEvent(2, "substitution", 1, 3, 2, "left", "{\"side\":\"left\",\"outMemberId\":\"l1\",\"inMemberId\":\"l8\"}"),
                // undo 快照：撤销生效后应保留到 seq 1（换人是 seq 2，落在水位之上）
                buildEvent(3, "score_snapshot", 1, 3, 2, "left", "{\"reason\":\"undo\",\"revertToSeq\":1,\"leftScore\":3,\"rightScore\":2}"),
                // undo 之后的新换人（l3 → l9）必须照常渲染
                buildEvent(4, "substitution", 1, 4, 3, "left", "{\"side\":\"left\",\"outMemberId\":\"l3\",\"inMemberId\":\"l9\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        String body = mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID))
                .andExpect(status().isOk())
                // 读模型事件流里不再有被撤销的换人：只剩 lineup(1) / undo(3) / 新换人(4)
                .andExpect(jsonPath("$.data.events.length()").value(3))
                .andExpect(jsonPath("$.data.events[1].eventSeq").value(3))
                .andExpect(jsonPath("$.data.events[2].eventSeq").value(4))
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode grid = objectMapper.readTree(body)
                .path("data").path("reportRender").path("games").path(0).path("leftRotationGrid");
        // 被撤销的换人不再进轮次表：1 号位没有替补副号（否则会显示 8 号）
        JsonNode ghostSlotSecondary = grid.path(0).path("secondaryJerseyNumber");
        assertTrue(ghostSlotSecondary.isMissingNode() || ghostSlotSecondary.isNull(),
                "被 undo 撤销的换人仍渲染进了轮次表：" + ghostSlotSecondary);
        // 自由人副号照常渲染
        assertEquals(7, grid.path(1).path("secondaryJerseyNumber").asInt());
        // undo 之后的新换人正常渲染
        assertEquals(9, grid.path(2).path("secondaryJerseyNumber").asInt());
    }

    @Test
    void getMatchRecord_undoWithoutRevertToSeq_shouldKeepLegacyRendering() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "lineup_snapshot", 1, 0, 0, "left", buildLineupPayload()),
                buildEvent(2, "substitution", 1, 3, 2, "left", "{\"side\":\"left\",\"outMemberId\":\"l1\",\"inMemberId\":\"l8\"}"),
                // 旧版本客户端：undo 快照不带 revertToSeq → 不做补偿，维持原有渲染
                buildEvent(3, "score_snapshot", 1, 3, 2, "left", "{\"reason\":\"undo\",\"leftScore\":3,\"rightScore\":2}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .with(withMatchLock(matchRecordMapper, MATCH_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/matches/{id}/record", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.events.length()").value(3))
                .andExpect(jsonPath("$.data.reportRender.games[0].leftRotationGrid[0].secondaryJerseyNumber").value(8));
    }

    private String buildLineupPayload() {
        return "{\"left\":{\"court\":[\"l1\",\"l2\",\"l3\",\"l4\",\"l5\",\"l6\"],\"middlePairIndexes\":[1,4],\"libero1Id\":\"l7\",\"libero2Id\":\"\"},"
                + "\"right\":{\"court\":[\"r1\",\"r2\",\"r3\",\"r4\",\"r5\",\"r6\"],\"middlePairIndexes\":[],\"libero1Id\":\"\",\"libero2Id\":\"\"},"
                + "\"serveSide\":\"left\"}";
    }

    private void grantReferee(String userId) {
        TournamentRefereeGrant grant = new TournamentRefereeGrant();
        grant.setTournamentId(TOURNAMENT_ID);
        grant.setUserId(userId);
        tournamentRefereeGrantMapper.insert(grant);
    }

    private void prepareMatch() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("test");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(99);
        tournament.setCreatorUserId("user-1");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);
        insertDefaultDivision(tournament);

        Player leftTeam = new Player();
        leftTeam.setId("p-left");
        leftTeam.setTournamentId(TOURNAMENT_ID);
        leftTeam.setDivisionId(TOURNAMENT_ID + "D01");
        leftTeam.setName("Left Team");
        playerMapper.insert(leftTeam);

        Player rightTeam = new Player();
        rightTeam.setId("p-right");
        rightTeam.setTournamentId(TOURNAMENT_ID);
        rightTeam.setDivisionId(TOURNAMENT_ID + "D01");
        rightTeam.setName("Right Team");
        playerMapper.insert(rightTeam);

        for (int i = 1; i <= 8; i++) {
            tournamentTeamMemberMapper.insert(buildMember("l" + i, "p-left", "L" + i, i, i == 7, i == 1));
            tournamentTeamMemberMapper.insert(buildMember("r" + i, "p-right", "R" + i, i, false, i == 1));
        }

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setDivisionId(TOURNAMENT_ID + "D01");
        match.setRoundNum(1);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setLeftPlayerId("p-left");
        match.setRightPlayerId("p-right");
        match.setStatus(1);
        matchRecordMapper.insert(match);
    }

    private TournamentTeamMember buildMember(String id,
                                             String participantId,
                                             String name,
                                             int jerseyNumber,
                                             boolean libero,
                                             boolean captain) {
        TournamentTeamMember member = new TournamentTeamMember();
        member.setId(id);
        member.setTournamentId(TOURNAMENT_ID);
        member.setParticipantId(participantId);
        member.setName(name);
        member.setJerseyNumber(jerseyNumber);
        member.setLibero(libero);
        member.setCaptain(captain);
        member.setDisplayOrder(jerseyNumber);
        return member;
    }

    private User buildUser(String id, String openid) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        return user;
    }

    private Map<String, Object> buildEvent(int eventSeq,
                                           String eventType,
                                           int gameNo,
                                           int leftScore,
                                           int rightScore,
                                           String serveSide,
                                           String payloadJson) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("eventSeq", eventSeq);
        item.put("eventType", eventType);
        item.put("gameNo", gameNo);
        item.put("leftScore", leftScore);
        item.put("rightScore", rightScore);
        item.put("serveSide", serveSide);
        item.put("payloadJson", payloadJson);
        return item;
    }
    private String insertDefaultDivision(com.scoring.backend.domain.entity.Tournament tournament) {
        com.scoring.backend.domain.entity.TournamentDivision division = new com.scoring.backend.domain.entity.TournamentDivision();
        division.setId(tournament.getId() + "D01");
        division.setTournamentId(tournament.getId());
        division.setName("默认组别");
        division.setSortOrder(0);
        division.setStatus(tournament.getStatus() == null ? 0 : tournament.getStatus());
        division.setParticipantType(0);
        division.setTournamentType(tournament.getTournamentType() == null ? 0 : tournament.getTournamentType());
        division.setGroupSize(tournament.getGroupSize());
        division.setKnockoutSlots(tournament.getKnockoutSlots());
        division.setKnockoutRounds(tournament.getKnockoutRounds());
        division.setQualifiersPerGroup(tournament.getQualifiersPerGroup());
        division.setRoundRobinRounds(tournament.getRoundRobinRounds());
        division.setCurrentStage(tournament.getCurrentStage() == null ? 1 : tournament.getCurrentStage());
        division.setKnockoutGenerated(tournament.getKnockoutGenerated() == null ? Boolean.TRUE : tournament.getKnockoutGenerated());
        division.setBestOf(tournament.getBestOf() == null ? 3 : tournament.getBestOf());
        division.setGamesToWin(tournament.getGamesToWin() == null ? 2 : tournament.getGamesToWin());
        division.setPointsToWin(tournament.getPointsToWin() == null ? 21 : tournament.getPointsToWin());
        division.setDecidingPointsToWin(tournament.getDecidingPointsToWin());
        division.setEnableDeuce(tournament.getEnableDeuce() == null ? Boolean.TRUE : tournament.getEnableDeuce());
        division.setCapPoint(tournament.getCapPoint() == null ? 30 : tournament.getCapPoint());
        division.setRoundRuleEnabled(tournament.getRoundRuleEnabled());
        division.setThirdPlaceEnabled(tournament.getThirdPlaceEnabled() == null ? Boolean.FALSE : tournament.getThirdPlaceEnabled());
        division.setThirdPlaceBestOf(tournament.getThirdPlaceBestOf());
        division.setThirdPlaceGamesToWin(tournament.getThirdPlaceGamesToWin());
        division.setThirdPlacePointsToWin(tournament.getThirdPlacePointsToWin());
        division.setThirdPlaceDecidingPointsToWin(tournament.getThirdPlaceDecidingPointsToWin());
        division.setThirdPlaceEnableDeuce(tournament.getThirdPlaceEnableDeuce());
        division.setThirdPlaceCapPoint(tournament.getThirdPlaceCapPoint());
        tournamentDivisionMapper.insert(division);
        return division.getId();
    }

}
