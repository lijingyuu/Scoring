package com.scoring.backend.security;

import cn.hutool.core.util.StrUtil;
import com.scoring.backend.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    private final AuthService authService;

    public AuthInterceptor(AuthService authService) {
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String authorization = request.getHeader("Authorization");
        String path = request.getRequestURI();
        if (isPublicAuthEndpoint(path)) {
            return true;
        }
        if (StrUtil.isBlank(authorization) || !authorization.startsWith("Bearer ")) {
            return true;
        }
        String token = authorization.substring("Bearer ".length()).trim();
        if (StrUtil.isBlank(token)) {
            return true;
        }
        AuthContext.setUserId(authService.verifyToken(token));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private boolean isPublicAuthEndpoint(String path) {
        return "/api/v1/auth/wechat-login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/password-login".equals(path);
    }
}
