package com.scoring.backend.service.impl;

import cn.hutool.core.util.StrUtil;
import com.scoring.backend.domain.dto.UpdateProfileReq;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.UserProfileVO;
import com.scoring.backend.mapper.UserMapper;
import com.scoring.backend.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @Override
    public UserProfileVO getCurrentProfile(String userId) {
        return toVO(requireUser(userId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserProfileVO updateProfile(String userId, UpdateProfileReq req) {
        User user = requireUser(userId);
        user.setNickname(StrUtil.trim(req.getNickname()));
        user.setAvatarUrl(StrUtil.trim(req.getAvatarUrl()));
        user.setProfileCompleted(true);
        userMapper.updateById(user);
        return toVO(user);
    }

    private User requireUser(String userId) {
        if (StrUtil.isBlank(userId)) {
            throw new IllegalArgumentException("用户不存在");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        return user;
    }

    private UserProfileVO toVO(User user) {
        UserProfileVO vo = new UserProfileVO();
        vo.setId(user.getId());
        vo.setNickname(user.getNickname());
        vo.setAvatarUrl(user.getAvatarUrl());
        vo.setProfileCompleted(Boolean.TRUE.equals(user.getProfileCompleted()));
        return vo;
    }
}
