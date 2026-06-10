package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentMapper;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
    private MatchRecordMapper matchRecordMapper;

    @Autowired
    private MatchEventMapper matchEventMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchEventMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
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
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
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
    void getMatchRecord_shouldReturnAggregatedRecord() throws Exception {
        Map<String, Object> req = new LinkedHashMap<>();
        req.put("events", List.of(
                buildEvent(1, "roster_snapshot", 1, 0, 0, "left", "{\"leftMembers\":[{\"id\":\"l1\",\"name\":\"甲一\",\"jerseyNumber\":1,\"captain\":true,\"libero\":false}],\"rightMembers\":[{\"id\":\"r1\",\"name\":\"乙一\",\"jerseyNumber\":2,\"captain\":false,\"libero\":true}]}"),
                buildEvent(2, "timeout", 1, 8, 7, "left", "{\"side\":\"left\"}")
        ));

        mockMvc.perform(put("/api/v1/matches/{id}/events", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
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
                .andExpect(jsonPath("$.data.rosterSnapshot.leftMembers[0].name").value("甲一"))
                .andExpect(jsonPath("$.data.events[1].eventType").value("timeout"))
                .andExpect(jsonPath("$.data.events[1].summary").value("Left Team 叫暂停"));
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
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(99);
        tournament.setCreatorUserId("user-1");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);

        Player leftTeam = new Player();
        leftTeam.setId("p-left");
        leftTeam.setTournamentId(TOURNAMENT_ID);
        leftTeam.setName("Left Team");
        playerMapper.insert(leftTeam);

        Player rightTeam = new Player();
        rightTeam.setId("p-right");
        rightTeam.setTournamentId(TOURNAMENT_ID);
        rightTeam.setName("Right Team");
        playerMapper.insert(rightTeam);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(1);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setLeftPlayerId("p-left");
        match.setRightPlayerId("p-right");
        match.setStatus(1);
        matchRecordMapper.insert(match);
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
}
