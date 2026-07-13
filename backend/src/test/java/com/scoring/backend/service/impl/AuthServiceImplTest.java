package com.scoring.backend.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.config.WechatProperties;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.AuthLoginVO;
import com.scoring.backend.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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

        service = new AuthServiceImpl(userMapper, authProperties, wechatProperties);
    }

    // ==================== loginWithCode ====================

    @Test
    void constructor_blankJwtSecret_shouldThrow() {
        AuthProperties blankAuthProperties = new AuthProperties();
        assertThrows(IllegalStateException.class,
                () -> new AuthServiceImpl(userMapper, blankAuthProperties, wechatProperties));
    }

    @Test
    void loginWithCode_blankCode_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.loginWithCode(""));
        assertThrows(IllegalArgumentException.class,
                () -> service.loginWithCode(null));
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
