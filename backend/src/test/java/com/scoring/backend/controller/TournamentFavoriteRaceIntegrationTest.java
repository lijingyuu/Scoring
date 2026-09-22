package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentFavorite;
import com.scoring.backend.domain.entity.User;
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
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * favorite 的 select-then-insert 竞态语义（串行模拟）：
 * 预检读不到记录（另一请求尚未提交），随后 insert 撞唯一键 —— 必须按"已收藏成功"幂等返回 200，
 * 且不能重复累加 favorite_count。这里用 mock 的 mapper 串行复现读缺失 + 写冲突这一交错顺序。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:favorite_race_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class TournamentFavoriteRaceIntegrationTest {

    private static final String TOURNAMENT_ID = "t-fav-race-1";
    private static final String USER_ID = "user-race-1";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TournamentMapper tournamentMapper;

    @Autowired
    private UserMapper userMapper;

    @MockBean
    private TournamentFavoriteMapper tournamentFavoriteMapper;

    @MockBean
    private AuthService authService;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn(USER_ID);
        userMapper.delete(new LambdaQueryWrapper<>());
        tournamentMapper.delete(new LambdaQueryWrapper<>());

        User user = new User();
        user.setId(USER_ID);
        user.setOpenid("openid-" + USER_ID);
        user.setNickname(USER_ID);
        user.setProfileCompleted(true);
        userMapper.insert(user);

        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("favorite-race");
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

    @Test
    void favorite_duplicateKeyRace_shouldBeIdempotentInsteadOf500() throws Exception {
        // 读缺失（并发下另一请求尚未提交）
        when(tournamentFavoriteMapper.selectOne(any())).thenReturn(null);
        // 写撞唯一键（另一请求已提交），抛出与 H2/MySQL 一致的唯一键冲突异常
        doThrow(new DuplicateKeyException("dup favorite",
                new SQLException("Unique index violation", "23505", 23505)))
                .when(tournamentFavoriteMapper).insert(any(TournamentFavorite.class));

        mockMvc.perform(post("/api/v1/tournaments/{id}/favorite", TOURNAMENT_ID)
                        .header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        // 幂等：冲突不重复计数
        assertEquals(0, tournamentMapper.selectById(TOURNAMENT_ID).getFavoriteCount());
    }
}
