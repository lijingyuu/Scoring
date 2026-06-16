package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchThemeConfig;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchThemeConfigMapper;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:volleyball_theme_config_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class MatchThemeConfigIntegrationTest {

    private static final String TOURNAMENT_ID = "t-1";
    private static final String MATCH_ID = "m-1";
    private static final String LEFT_TEAM_ID = "p-left";
    private static final String RIGHT_TEAM_ID = "p-right";

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
    private MatchThemeConfigMapper matchThemeConfigMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        matchThemeConfigMapper.delete(new QueryWrapper<>());
        matchRecordMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        prepareMatch();
    }

    @Test
    void saveThemeConfig_shouldCreateAndAllowOverwrite() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "pad",
                                "dark",
                                "#194955",
                                "#143843",
                                "#F49227"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchThemeConfig created = matchThemeConfigMapper.selectOne(
                new QueryWrapper<MatchThemeConfig>().eq("match_id", MATCH_ID)
        );
        assertNotNull(created);
        Map<String, Object> createdThemeConfig = objectMapper.readValue(created.getThemeJson(), new TypeReference<>() {});
        Map<String, Object> createdPadConfig = objectMapper.convertValue(createdThemeConfig.get("pad"), new TypeReference<>() {});
        Map<String, String> createdPadTheme = objectMapper.convertValue(createdPadConfig.get("dark"), new TypeReference<>() {});
        assertEquals("#194955", createdPadTheme.get("themeBase"));

        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "pad",
                                "dark",
                                "#225F6E",
                                "#143843",
                                "#F4A53A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        MatchThemeConfig updated = matchThemeConfigMapper.selectOne(
                new QueryWrapper<MatchThemeConfig>().eq("match_id", MATCH_ID)
        );
        Map<String, Object> updatedThemeConfig = objectMapper.readValue(updated.getThemeJson(), new TypeReference<>() {});
        Map<String, Object> updatedPadConfig = objectMapper.convertValue(updatedThemeConfig.get("pad"), new TypeReference<>() {});
        Map<String, String> updatedPadTheme = objectMapper.convertValue(updatedPadConfig.get("dark"), new TypeReference<>() {});
        assertEquals("#225F6E", updatedPadTheme.get("themeBase"));
        assertEquals("#F4A53A", updatedPadTheme.get("themeAccent"));
        assertNull(updatedThemeConfig.get("phone"));
    }

    @Test
    void getThemeConfig_shouldReturnSavedThemeByDevice() throws Exception {
        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "phone",
                                "dark",
                                "#194955",
                                "#143843",
                                "#F49227"
                         ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "phone",
                                "light",
                                "#FFF8E8",
                                "#F3E8C8",
                                "#E39B27"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "pad",
                                "dark",
                                "#225F6E",
                                "#143843",
                                "#F4A53A"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "pad",
                                "light",
                                "#F7F4EA",
                                "#D9E6E8",
                                "#DE8C2F"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/v1/matches/{id}/theme-config", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.matchId").value(MATCH_ID))
                .andExpect(jsonPath("$.data.phoneTheme.themeBase").value("#194955"))
                .andExpect(jsonPath("$.data.phoneTheme.themeBaseDeep").value("#143843"))
                .andExpect(jsonPath("$.data.phoneTheme.themeAccent").value("#F49227"))
                .andExpect(jsonPath("$.data.phoneLightTheme.themeBase").value("#FFF8E8"))
                .andExpect(jsonPath("$.data.phoneLightTheme.themeAccent").value("#E39B27"))
                .andExpect(jsonPath("$.data.padTheme.themeBase").value("#225F6E"))
                .andExpect(jsonPath("$.data.padTheme.themeAccent").value("#F4A53A"))
                .andExpect(jsonPath("$.data.padLightTheme.themeBase").value("#F7F4EA"))
                .andExpect(jsonPath("$.data.padLightTheme.themeAccent").value("#DE8C2F"));
    }

    @Test
    void saveThemeConfig_shouldRejectNonCreator() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn("user-2");

        mockMvc.perform(put("/api/v1/matches/{id}/theme-config", MATCH_ID)
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(buildThemePayload(
                                "pad",
                                "dark",
                                "#194955",
                                "#143843",
                                "#F49227"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("only creator can modify this match"));
    }

    @Test
    void getThemeConfig_shouldBeCompatibleWithLegacyThemeShape() throws Exception {
        MatchThemeConfig legacyConfig = new MatchThemeConfig();
        legacyConfig.setMatchId(MATCH_ID);
        legacyConfig.setThemeJson(objectMapper.writeValueAsString(buildLegacyThemeMap(
                "#194955",
                "#143843",
                "#F49227"
        )));
        matchThemeConfigMapper.insert(legacyConfig);

        mockMvc.perform(get("/api/v1/matches/{id}/theme-config", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.theme.themeBase").value("#194955"))
                .andExpect(jsonPath("$.data.phoneTheme").doesNotExist())
                .andExpect(jsonPath("$.data.phoneLightTheme").doesNotExist())
                .andExpect(jsonPath("$.data.padTheme").doesNotExist())
                .andExpect(jsonPath("$.data.padLightTheme").doesNotExist());
    }

    @Test
    void getThemeConfig_shouldTreatFlatDeviceThemeAsDarkTheme() throws Exception {
        Map<String, Object> flatConfig = new LinkedHashMap<>();
        flatConfig.put("phone", buildLegacyThemeMap("#194955", "#143843", "#F49227"));
        flatConfig.put("pad", buildLegacyThemeMap("#225F6E", "#143843", "#F4A53A"));

        MatchThemeConfig config = new MatchThemeConfig();
        config.setMatchId(MATCH_ID);
        config.setThemeJson(objectMapper.writeValueAsString(flatConfig));
        matchThemeConfigMapper.insert(config);

        mockMvc.perform(get("/api/v1/matches/{id}/theme-config", MATCH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.phoneTheme.themeBase").value("#194955"))
                .andExpect(jsonPath("$.data.phoneLightTheme").doesNotExist())
                .andExpect(jsonPath("$.data.padTheme.themeBase").value("#225F6E"))
                .andExpect(jsonPath("$.data.padLightTheme").doesNotExist());
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
        leftTeam.setId(LEFT_TEAM_ID);
        leftTeam.setTournamentId(TOURNAMENT_ID);
        leftTeam.setName("Left Team");
        playerMapper.insert(leftTeam);

        Player rightTeam = new Player();
        rightTeam.setId(RIGHT_TEAM_ID);
        rightTeam.setTournamentId(TOURNAMENT_ID);
        rightTeam.setName("Right Team");
        playerMapper.insert(rightTeam);

        MatchRecord match = new MatchRecord();
        match.setId(MATCH_ID);
        match.setTournamentId(TOURNAMENT_ID);
        match.setRoundNum(1);
        match.setMatchIndex(1);
        match.setStageType(1);
        match.setLeftPlayerId(LEFT_TEAM_ID);
        match.setRightPlayerId(RIGHT_TEAM_ID);
        match.setStatus(1);
        matchRecordMapper.insert(match);
    }

    private Map<String, Object> buildThemePayload(String device,
                                                  String mode,
                                                  String themeBase,
                                                  String themeBaseDeep,
                                                  String themeAccent) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("device", device);
        payload.put("mode", mode);
        payload.put("theme", buildLegacyThemeMap(themeBase, themeBaseDeep, themeAccent));
        return payload;
    }

    private Map<String, String> buildLegacyThemeMap(String themeBase,
                                                    String themeBaseDeep,
                                                    String themeAccent) {
        Map<String, String> theme = new LinkedHashMap<>();
        theme.put("themeBase", themeBase);
        theme.put("themeBaseDeep", themeBaseDeep);
        theme.put("themeAccent", themeAccent);
        theme.put("themeAccentInk", "#194955");
        theme.put("captain", "#739C69");
        theme.put("courtSurface", "#1E4F2B");
        theme.put("rightScoreAccent", "#52C41A");
        theme.put("dangerAccent", "#FF7A45");
        theme.put("textStrong", "#FFFFFF");
        theme.put("surfaceGlass", "#FFFFFF");
        theme.put("shadowColor", "#000000");
        theme.put("overlayMask", "#07121C");
        theme.put("courtSlotAccent", "#008F8D");
        return theme;
    }
}
