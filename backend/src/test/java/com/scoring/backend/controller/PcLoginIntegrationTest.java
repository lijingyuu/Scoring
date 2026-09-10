package com.scoring.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.scoring.backend.ScoringBackendApplication;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.entity.WebLoginSession;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.mapper.WebLoginSessionMapper;
import com.scoring.backend.service.AuthService;
import com.scoring.backend.service.wechat.WechatQrCodeClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PC 网页扫码登录全链路集成测试。
 * 微信侧（WechatQrCodeClient）与 token 签发（AuthService）均 Mock，
 * 聚焦验证票据状态机：CREATED → SCANNED → CONFIRMED → CONSUMED。
 */
@SpringBootTest(classes = ScoringBackendApplication.class)
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:pc_login_test;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=always",
        "spring.sql.init.schema-locations=classpath:schema-h2.sql",
        "app.rate-limit.enabled=false",
        "app.auth.jwt-secret=test-secret"
})
class PcLoginIntegrationTest {

    private static final String USER_ID = "pc-user-1";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private WebLoginSessionMapper webLoginSessionMapper;

    @MockBean
    private AuthService authService;
    @MockBean
    private WechatQrCodeClient wechatQrCodeClient;

    @BeforeEach
    void setUp() {
        when(authService.verifyToken(anyString())).thenReturn(USER_ID);
        AuthLoginVO login = new AuthLoginVO();
        login.setToken("jwt-for-" + USER_ID);
        login.setProfileCompleted(true);
        when(authService.issueLogin(USER_ID)).thenReturn(login);
        // Mock 返回真实 PNG 头部字节即可（服务层只做 base64 拼接）
        when(wechatQrCodeClient.fetchLoginQrCode(anyString()))
                .thenReturn(new byte[]{(byte) 0x89, 'P', 'N', 'G'});

        webLoginSessionMapper.delete(new QueryWrapper<>());
        userMapper.delete(new QueryWrapper<>());
        User user = new User();
        user.setId(USER_ID);
        user.setOpenid("openid-pc-1");
        user.setNickname("创建者小明");
        user.setAvatarUrl("https://example.com/avatar.png");
        user.setProfileCompleted(true);
        userMapper.insert(user);
    }

    @Test
    void fullFlow_createScanConfirmConsume() throws Exception {
        String ticket = createTicket();

        // 初始状态：CREATED
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CREATED"));

        // 未登录上报扫码 → 401
        mockMvc.perform(post("/api/v1/auth/pc/scan")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isUnauthorized());

        // 小程序上报扫码 → SCANNED，返回扫码人昵称供 PC 核对
        mockMvc.perform(post("/api/v1/auth/pc/scan")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCANNED"))
                .andExpect(jsonPath("$.data.nickname").value("创建者小明"))
                .andExpect(jsonPath("$.data.avatarUrl").value("https://example.com/avatar.png"));

        // 未登录确认 → 401
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isUnauthorized());

        // 小程序确认授权 → CONFIRMED
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());

        // PC 首次轮询取走 token（一次性）
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.token").value("jwt-for-" + USER_ID))
                .andExpect(jsonPath("$.data.profileCompleted").value(true));

        // 再次轮询：CONSUMED，不再下发 token（防重放）
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONSUMED"))
                .andExpect(jsonPath("$.data.token").doesNotExist());
    }

    @Test
    void confirm_ticketExpired_returns400() throws Exception {
        String ticket = insertSession(LocalDateTime.now().minusSeconds(10));
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("二维码已过期，请在电脑上刷新后重新扫码"));
    }

    @Test
    void confirm_ticketNotFound_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody("0123456789abcdef0123456789abcdef")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("二维码无效"));
    }

    @Test
    void confirm_twice_secondFails() throws Exception {
        String ticket = createTicket();
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("二维码已被使用，请在电脑上重新发起登录"));
    }

    @Test
    void confirm_skipsScanDirectly_succeeds() throws Exception {
        // scan 上报完全丢失时，无人扫过的 CREATED 票据允许确认人直转（此时确认人即第一接触人）
        String ticket = createTicket();
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"));
    }

    @Test
    void confirm_byDifferentUserThanScanner_fails() throws Exception {
        // 共享屏幕场景：A 扫码后，B 从同一张码进入不能替确认，PC 不会错登 B 的账号
        when(authService.verifyToken("token-b")).thenReturn("pc-user-2");

        String ticket = createTicket();
        mockMvc.perform(post("/api/v1/auth/pc/scan")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("请使用扫码的微信号确认授权"));

        // 扫码人本人确认仍成功
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(ticketBody(ticket)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.token").value("jwt-for-" + USER_ID));
    }

    @Test
    void scan_idempotent_andSilentAfterConfirm() throws Exception {
        String ticket = createTicket();
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/v1/auth/pc/scan")
                            .header("Authorization", "Bearer token")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(ticketBody(ticket)))
                    .andExpect(status().isOk());
        }
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(jsonPath("$.data.status").value("SCANNED"));
    }

    @Test
    void poll_unknownOrInvalidTicket_returnsExpired() throws Exception {
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", "ffffffffffffffffffffffffffffffff"))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", "not-hex"))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    void poll_expiredTicket_returnsExpired() throws Exception {
        String ticket = insertSession(LocalDateTime.now().minusSeconds(10));
        mockMvc.perform(get("/api/v1/auth/pc/status").param("ticket", ticket))
                .andExpect(jsonPath("$.data.status").value("EXPIRED"));
    }

    @Test
    void qrCode_returnsBase64ImageWithTicket() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/pc/qr-code"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.ticket").isNotEmpty())
                .andExpect(jsonPath("$.data.expireSeconds").value(180))
                .andReturn();
        String body = result.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(body.contains("data:image/png;base64,"));
        assertNotNull(webLoginSessionMapper.selectOne(
                new QueryWrapper<WebLoginSession>().eq("ticket", extractTicket(body))));
    }

    @Test
    void confirm_invalidTicketFormat_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/pc/confirm")
                        .header("Authorization", "Bearer token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticket\":\"short\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 走正常出码接口拿一个真实 ticket */
    private String createTicket() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/pc/qr-code"))
                .andExpect(status().isOk())
                .andReturn();
        return extractTicket(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** 绕过接口直接落一个指定过期时间的票据 */
    private String insertSession(LocalDateTime expireTime) {
        String ticket = java.util.UUID.randomUUID().toString().replace("-", "");
        WebLoginSession session = new WebLoginSession();
        session.setTicket(ticket);
        session.setStatus(WebLoginSession.STATUS_CREATED);
        session.setExpireTime(expireTime);
        webLoginSessionMapper.insert(session);
        return ticket;
    }

    private static String ticketBody(String ticket) {
        return "{\"ticket\":\"" + ticket + "\"}";
    }

    private String extractTicket(String body) throws Exception {
        return objectMapper.readTree(body).path("data").path("ticket").asText();
    }
}
