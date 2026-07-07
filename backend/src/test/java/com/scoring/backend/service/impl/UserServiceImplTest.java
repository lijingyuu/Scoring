package com.scoring.backend.service.impl;

import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.dto.UpdateProfileReq;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.UserProfileVO;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    private UserServiceImpl service;

    private final String userId = IdUtil.simpleUUID();

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userMapper);
    }

    // ==================== getCurrentProfile ====================

    @Test
    void getCurrentProfile_existingUser_shouldReturnProfile() {
        User user = buildUser(userId, "openid-123", "小明", "https://img.example.com/avatar.png", true);
        when(userMapper.selectById(userId)).thenReturn(user);

        UserProfileVO result = service.getCurrentProfile(userId);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("小明", result.getNickname());
        assertEquals("https://img.example.com/avatar.png", result.getAvatarUrl());
        assertTrue(result.getProfileCompleted());
    }

    @Test
    void getCurrentProfile_userNotFound_shouldThrow() {
        when(userMapper.selectById(userId)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> service.getCurrentProfile(userId));
    }

    @Test
    void getCurrentProfile_blankUserId_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getCurrentProfile(""));
        assertThrows(IllegalArgumentException.class,
                () -> service.getCurrentProfile(null));
    }

    // ==================== updateProfile ====================

    @Test
    void updateProfile_shouldUpdateAndReturnProfile() {
        User user = buildUser(userId, "openid-456", null, null, false);
        when(userMapper.selectById(userId)).thenReturn(user);

        UpdateProfileReq req = new UpdateProfileReq();
        req.setNickname("小红");
        req.setAvatarUrl("https://img.example.com/new-avatar.png");

        UserProfileVO result = service.updateProfile(userId, req);

        assertNotNull(result);
        assertEquals(userId, result.getId());
        assertEquals("小红", result.getNickname());
        assertEquals("https://img.example.com/new-avatar.png", result.getAvatarUrl());
        assertTrue(result.getProfileCompleted());

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).updateById(captor.capture());
        User updated = captor.getValue();
        assertEquals("小红", updated.getNickname());
        assertTrue(updated.getProfileCompleted());
    }

    @Test
    void updateProfile_shouldTrimNickname() {
        User user = buildUser(userId, "openid-789", null, null, false);
        when(userMapper.selectById(userId)).thenReturn(user);

        UpdateProfileReq req = new UpdateProfileReq();
        req.setNickname("  张三  ");
        req.setAvatarUrl("https://img.example.com/avatar.png");

        UserProfileVO result = service.updateProfile(userId, req);
        assertEquals("张三", result.getNickname());
    }

    @Test
    void updateProfile_userNotFound_shouldThrow() {
        when(userMapper.selectById(userId)).thenReturn(null);

        UpdateProfileReq req = new UpdateProfileReq();
        req.setNickname("测试");
        req.setAvatarUrl("https://img.example.com/avatar.png");

        assertThrows(IllegalArgumentException.class,
                () -> service.updateProfile(userId, req));
    }

    private User buildUser(String id, String openid, String nickname, String avatarUrl, boolean profileCompleted) {
        User user = new User();
        user.setId(id);
        user.setOpenid(openid);
        user.setNickname(nickname);
        user.setAvatarUrl(avatarUrl);
        user.setProfileCompleted(profileCompleted);
        return user;
    }
}
