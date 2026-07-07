package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.User;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:favorite_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class TournamentFavoriteIntegrationTest {

    private static final String TOURNAMENT_ID = "t-fav-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private TournamentMapper tournamentMapper;
    @Autowired
    private TournamentFavoriteMapper tournamentFavoriteMapper;
    @Autowired
    private PlayerMapper playerMapper;
    @Autowired
    private UserMapper userMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn("user-1");
        tournamentFavoriteMapper.delete(new QueryWrapper<>());
        playerMapper.delete(new QueryWrapper<>());
        tournamentMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        userMapper.insert(buildUser("user-1", true));
        userMapper.insert(buildUser("user-2", true));
        prepareTournament();
    }

    @Test
    void favorite_shouldCreateAndIncreaseCount() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Verify DB record
        TournamentFavorite fav = tournamentFavoriteMapper.selectOne(
                new QueryWrapper<TournamentFavorite>()
                        .eq("user_id", "user-1")
                        .eq("tournament_id", TOURNAMENT_ID)
        );
        assertEquals("user-1", fav.getUserId());
        assertEquals(TOURNAMENT_ID, fav.getTournamentId());

        // Verify count incremented
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(1, tournament.getFavoriteCount());
    }

    @Test
    void favorite_duplicate_shouldBeIdempotent() throws Exception {
        // First favorite
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Second favorite (same user)
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Should still only have 1 record
        long count = tournamentFavoriteMapper.selectCount(
                new QueryWrapper<TournamentFavorite>()
                        .eq("user_id", "user-1")
                        .eq("tournament_id", TOURNAMENT_ID)
        );
        assertEquals(1, count);

        // Count should not be double-incremented
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(1, tournament.getFavoriteCount());
    }

    @Test
    void unfavorite_shouldRemoveAndDecreaseCount() throws Exception {
        // First favorite
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                .header("Authorization", "Bearer token"));

        // Then unfavorite
        mockMvc.perform(delete("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // Should have no records
        long count = tournamentFavoriteMapper.selectCount(
                new QueryWrapper<TournamentFavorite>()
                        .eq("user_id", "user-1")
                        .eq("tournament_id", TOURNAMENT_ID)
        );
        assertEquals(0, count);

        // Count should be back to 0
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(0, tournament.getFavoriteCount());
    }

    @Test
    void listMyFavorites_shouldReturnFavoriteTournaments() throws Exception {
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                .header("Authorization", "Bearer token"));

        mockMvc.perform(get("/api/v1/tournaments/mine/favorites")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(TOURNAMENT_ID))
                .andExpect(jsonPath("$.data[0].favorite").value(true));
    }

    @Test
    void listMyFavorites_withoutFavorites_shouldReturnEmptyList() throws Exception {
        mockMvc.perform(get("/api/v1/tournaments/mine/favorites")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(0));
    }

    @Test
    void listMyCreated_shouldReturnCreatedTournaments() throws Exception {
        when(authService.verifyToken(anyString())).thenReturn("user-creator");

        mockMvc.perform(get("/api/v1/tournaments/mine/created")
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(TOURNAMENT_ID))
                .andExpect(jsonPath("$.data[0].creator").value(true));
    }

    @Test
    void differentUsers_shouldHaveIndependentFavorites() throws Exception {
        // user-1 favorites
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                .header("Authorization", "Bearer token"));

        // user-2 favorites
        when(authService.verifyToken(anyString())).thenReturn("user-2");
        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                .header("Authorization", "Bearer token"));

        // Both should have separate records
        long count = tournamentFavoriteMapper.selectCount(
                new QueryWrapper<TournamentFavorite>()
                        .eq("tournament_id", TOURNAMENT_ID)
        );
        assertEquals(2, count);

        // Count should be 2
        Tournament tournament = tournamentMapper.selectById(TOURNAMENT_ID);
        assertEquals(2, tournament.getFavoriteCount());
    }

    private void prepareTournament() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("test-tournament");
        tournament.setLocation("court");
        tournament.setStatus(1);
        tournament.setSportType(0);
        tournament.setTournamentType(0);
        tournament.setCurrentStage(1);
        tournament.setKnockoutGenerated(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(21);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId("user-creator");
        tournament.setFavoriteCount(0);
        tournamentMapper.insert(tournament);
    }

    private User buildUser(String id, boolean profileCompleted) {
        User user = new User();
        user.setId(id);
        user.setOpenid("openid-" + id);
        user.setNickname(id);
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(profileCompleted);
        return user;
    }
}
