package com.scoring.backend.security;

import cn.hutool.core.util.StrUtil;
import org.springframework.stereotype.Component;

@Component
public class AuthGuard {

    public String requireUserId() {
        String userId = AuthContext.getUserId();
        if (StrUtil.isBlank(userId)) {
            throw new UnauthorizedException("请先登录");
        }
        return userId;
    }
}
