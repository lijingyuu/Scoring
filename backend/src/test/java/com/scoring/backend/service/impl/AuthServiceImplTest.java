package com.scoring.backend.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.config.WechatProperties;
import com.scoring.backend.domain.dto.PasswordLoginReq;
import com.scoring.backend.domain.dto.RegisterReq;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.StandardEnvironment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private StandardEnvironment environment;
    private AuthProperties authProperties;
    private WechatProperties wechatProperties;
    private AuthServiceImpl service;

    @BeforeEach
    void setUp() {
        authProperties = new AuthProperties();
        authProperties.setJwtSecret("test-jwt-secret-for-unit-testing");
        authProperties.setJwtExpireSeconds(3600L);

        wechatProperties = new WechatProperties();
        wechatProperties.setAppId("");
        wechatProperties.setAppSecret("");

        environment = new StandardEnvironment();
        environment.setActiveProfiles("dev");

        service = new AuthServiceImpl(userMapper, authProperties, wechatProperties, environment);
    }

    // ==================== loginWithCode ====================

    @Test
    void constructor_blankJwtSecret_shouldThrow() {
        AuthProperties blankAuthProperties = new AuthProperties();
        assertThrows(IllegalStateException.class,
                () -> new AuthServiceImpl(userMapper, blankAuthProperties, wechatProperties, environment));
    }

    @Test
    void loginWithCode_blankCode_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.loginWithCode(""));
        assertThrows(IllegalArgumentException.class,
                () -> service.loginWithCode(null));
    }

    @Test
    void loginWithCode_missingWechatConfigNonDev_shouldThrow() {
        StandardEnvironment nonDevEnvironment = new StandardEnvironment();
        AuthServiceImpl nonDevService = new AuthServiceImpl(userMapper, authProperties, wechatProperties, nonDevEnvironment);

        assertThrows(IllegalStateException.class,
                () -> nonDevService.loginWithCode("test-code-nondev"));
    }

    @Test
    void loginWithCode_newUser_shouldCreateAndReturnToken() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        AuthLoginVO result = service.loginWithCode("test-code-123");

        assertNotNull(result);
        assertNotNull(result.getToken());
        assertFalse(result.getProfileCompleted());

        // Verify new user was inserted
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("mock_test-code-123", inserted.getOpenid());
        assertFalse(inserted.getProfileCompleted());
    }

    @Test
    void loginWithCode_existingUser_shouldReturnToken() {
        User existingUser = new User();
        existingUser.setId(IdUtil.simpleUUID());
        existingUser.setOpenid("mock_test-code-456");
        existingUser.setNickname("小明");
        existingUser.setProfileCompleted(true);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        AuthLoginVO result = service.loginWithCode("test-code-456");

        assertNotNull(result);
        assertNotNull(result.getToken());
        assertTrue(result.getProfileCompleted());
    }

    @Test
    void loginWithCode_uncompletedProfile_shouldReturnFalse() {
        User existingUser = new User();
        existingUser.setId(IdUtil.simpleUUID());
        existingUser.setOpenid("mock_test-code-789");
        existingUser.setNickname(null);
        existingUser.setProfileCompleted(false);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        AuthLoginVO result = service.loginWithCode("test-code-789");

        assertNotNull(result.getToken());
        assertFalse(result.getProfileCompleted());
    }

    @Test
    void register_newUser_shouldHashPasswordAndReturnToken() {
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        RegisterReq req = new RegisterReq();
        req.setUsername("Admin_01");
        req.setPassword("secret123");
        req.setNickname("管理员");

        AuthLoginVO result = service.register(req);

        assertNotNull(result.getToken());
        assertTrue(result.getProfileCompleted());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User inserted = captor.getValue();
        assertEquals("admin_01", inserted.getUsername());
        assertEquals("管理员", inserted.getNickname());
        assertTrue(inserted.getProfileCompleted());
        assertNotNull(inserted.getPasswordHash());
        assertTrue(cn.hutool.crypto.digest.BCrypt.checkpw("secret123", inserted.getPasswordHash()));
    }

    @Test
    void register_duplicateUsername_shouldThrow() {
        User existingUser = new User();
        existingUser.setId("user-duplicate");
        existingUser.setUsername("admin");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        RegisterReq req = new RegisterReq();
        req.setUsername("Admin");
        req.setPassword("secret123");
        req.setNickname("管理员");

        assertThrows(IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    void register_disabled_shouldThrow() {
        authProperties.setRegistrationEnabled(false);

        RegisterReq req = new RegisterReq();
        req.setUsername("admin");
        req.setPassword("secret123");
        req.setNickname("管理员");

        assertThrows(IllegalArgumentException.class, () -> service.register(req));
    }

    @Test
    void loginWithPassword_validPassword_shouldReturnToken() {
        User existingUser = new User();
        existingUser.setId("user-password");
        existingUser.setUsername("admin");
        existingUser.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("secret123", cn.hutool.crypto.digest.BCrypt.gensalt()));
        existingUser.setProfileCompleted(true);
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        PasswordLoginReq req = new PasswordLoginReq();
        req.setUsername("Admin");
        req.setPassword("secret123");

        AuthLoginVO result = service.loginWithPassword(req);

        assertNotNull(result.getToken());
        assertTrue(result.getProfileCompleted());
    }

    @Test
    void loginWithPassword_wrongPassword_shouldThrow() {
        User existingUser = new User();
        existingUser.setId("user-password");
        existingUser.setUsername("admin");
        existingUser.setPasswordHash(cn.hutool.crypto.digest.BCrypt.hashpw("secret123", cn.hutool.crypto.digest.BCrypt.gensalt()));
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        PasswordLoginReq req = new PasswordLoginReq();
        req.setUsername("admin");
        req.setPassword("bad-password");

        assertThrows(IllegalArgumentException.class, () -> service.loginWithPassword(req));
    }

    // ==================== verifyToken ====================

    @Test
    void verifyToken_validToken_shouldReturnUserId() {
        // First login to get a token
        User existingUser = new User();
        existingUser.setId("test-user-id-001");
        existingUser.setOpenid("mock_test-code-token");
        existingUser.setProfileCompleted(true);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        AuthLoginVO loginResult = service.loginWithCode("test-code-token");
        String token = loginResult.getToken();

        // Then verify it
        String userId = service.verifyToken(token);
        assertEquals("test-user-id-001", userId);
    }

    @Test
    void verifyToken_invalidToken_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyToken("invalid-token-string"));
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyToken(""));
        assertThrows(IllegalArgumentException.class,
                () -> service.verifyToken(null));
    }

    @Test
    void verifyToken_tamperedToken_shouldThrow() {
        User existingUser = new User();
        existingUser.setId("test-user-id-002");
        existingUser.setOpenid("mock_test-code-tamper");
        existingUser.setProfileCompleted(true);

        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(existingUser);

        AuthLoginVO loginResult = service.loginWithCode("test-code-tamper");
        String token = loginResult.getToken();

        // Tamper with the token
        String tampered = token.substring(0, token.length() - 5) + "xxxxx";

        assertThrows(IllegalArgumentException.class,
                () -> service.verifyToken(tampered));
    }
}
