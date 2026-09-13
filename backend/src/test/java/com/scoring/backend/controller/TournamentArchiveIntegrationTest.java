package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentFavoriteMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:archive_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class TournamentArchiveIntegrationTest {

    @Autowired
    private com.scoring.backend.mapper.TournamentDivisionMapper tournamentDivisionMapper;

    private static final String FINISHED_ID = "t-archive-finished";
    private static final String RUNNING_ID = "t-archive-running";
    private static final String MATCH_ID = "m-archive-finished";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TournamentMapper tournamentMapper;
    @Autowired
    private TournamentFavoriteMapper tournamentFavoriteMapper;
    @Autowired
    private MatchRecordMapper matchRecordMapper;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("creator");
        tournamentFavoriteMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        tournamentDivisionMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("creator"));
        userMapper.insert(buildUser("other"));
        userMapper.insert(buildUser("fan"));
        Tournament finishedTournament = buildTournament(FINISHED_ID, "finished archive target", 2, "creator");
        tournamentMapper.insert(finishedTournament);
        insertDefaultDivision(finishedTournament);
        Tournament runningTournament = buildTournament(RUNNING_ID, "running archive target", 1, "creator");
        tournamentMapper.insert(runningTournament);
        insertDefaultDivision(runningTournament);
        matchRecordMapper.insert(buildMatch());
    }

    @Test
    void archive_shouldHideFromNormalListsAndKeepInArchivedList() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn("fan");
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", FINISHED_ID)
                        .header("Authorization", "Bearer fan-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        when(authService.verifyToken(anyString())).thenReturn("creator");
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", FINISHED_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/tournaments?keyword=finished"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(get("/api/v1/tournaments/mine/created")
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(RUNNING_ID));

        mockMvc.perform(get("/api/v1/tournaments/mine/archived")
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(FINISHED_ID));

        when(authService.verifyToken(anyString())).thenReturn("fan");
        mockMvc.perform(get("/api/v1/tournaments/mine/favorites")
                        .header("Authorization", "Bearer fan-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void unarchive_shouldRestoreCreatedAndFavoriteLists() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn("fan");
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", FINISHED_ID)
                .header("Authorization", "Bearer fan-token"));

        when(authService.verifyToken(anyString())).thenReturn("creator");
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", FINISHED_ID)
                .header("Authorization", "Bearer creator-token"));
        mockMvc.perform(put("/api/v1/tournaments/{id}/unarchive", FINISHED_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/tournaments/mine/created")
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        when(authService.verifyToken(anyString())).thenReturn("fan");
        mockMvc.perform(get("/api/v1/tournaments/mine/favorites")
                        .header("Authorization", "Bearer fan-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(FINISHED_ID));
    }

    @Test
    void archive_shouldRejectRunningTournamentAndNonCreator() throws Exception {
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", RUNNING_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));

        when(authService.verifyToken(anyString())).thenReturn("other");
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", FINISHED_ID)
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void archivedTournament_shouldOnlyBeVisibleToCreator() throws Exception {
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", FINISHED_ID)
                .header("Authorization", "Bearer creator-token"));

        mockMvc.perform(get("/api/v1/tournaments/{id}", FINISHED_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.archived").value(true));

        when(authService.verifyToken(anyString())).thenReturn("other");
        mockMvc.perform(get("/api/v1/tournaments/{id}", FINISHED_ID)
                        .header("Authorization", "Bearer other-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void archivedTournament_shouldRejectMatchWrites() throws Exception {
        mockMvc.perform(put("/api/v1/tournaments/{id}/archive", FINISHED_ID)
                .header("Authorization", "Bearer creator-token"));

        mockMvc.perform(put("/api/v1/matches/{id}/restart", MATCH_ID)
                        .header("Authorization", "Bearer creator-token"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value(500));
    }

    private User buildUser(String id) {
        User user = new User();
        user.setId(id);
        user.setOpenid(id + "-openid");
        user.setNickname(id);
        user.setProfileCompleted(true);
        return user;
    }

    private Tournament buildTournament(String id, String name, int status, String creatorUserId) {
        Tournament tournament = new Tournament();
        tournament.setId(id);
        tournament.setName(name);
        tournament.setLocation("court");
        tournament.setStatus(status);
        tournament.setSportType(0);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(21);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId(creatorUserId);
        tournament.setFavoriteCount(0);
        return tournament;
    }

    private MatchRecord buildMatch() {
        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(FINISHED_ID);
        match.setDivisionId(FINISHED_ID + "D01");
        match.setStageType(1);
        match.setRoundNum(1);
        match.setMatchIndex(0);
        match.setLeftPlayerId("p-left");
        match.setRightPlayerId("p-right");
        match.setWinnerId("p-left");
        match.setStatus(2);
        return match;
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
