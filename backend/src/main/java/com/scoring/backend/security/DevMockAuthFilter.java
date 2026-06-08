package com.scoring.backend.security;

import cn.hutool.core.util.StrUtil;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Date;

/**
 * 仅在 dev profile 生效，对所有 /api/** 请求自动注入一个模拟用户的 JWT token。
 * 用于网页端调试（浏览器无法调用 uni.login 微信登录）。
 * <p>
 * 模拟用户 profileCompleted=true，因此不会触发资料补全弹窗。
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DevMockAuthFilter extends OncePerRequestFilter {

    private static final String MOCK_OPENID = "dev_mock_web_debug";

    private final UserMapper userMapper;
    private final AuthProperties authProperties;

    private Algorithm algorithm;
    private volatile boolean initialized;
    private String mockUserId;
    private String mockToken;

    public DevMockAuthFilter(UserMapper userMapper, AuthProperties authProperties) {
        this.userMapper = userMapper;
        this.authProperties = authProperties;
    }

    @PostConstruct
    public void init() {
        this.algorithm = Algorithm.HMAC256(
                StrUtil.blankToDefault(authProperties.getJwtSecret(), "change-me-jwt-secret"));
    }

    private synchronized void ensureMockUser() {
        if (initialized) return;

        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getOpenid, MOCK_OPENID));

        if (user == null) {
            user = new User();
            user.setOpenid(MOCK_OPENID);
            user.setNickname("Dev调试");
            user.setAvatarUrl("https://mmbiz.qpic.cn/mmbiz/icTdbqWNOwNRna42FI242Lcia07jQodd2FJGIYQfG0LAJGFxM4FbnQP6yfMxBgJ0F3YRqJCJ1aPAK2dQagdusBZg/0");
            user.setProfileCompleted(true);
            userMapper.insert(user);
        } else if (!Boolean.TRUE.equals(user.getProfileCompleted())) {
            user.setNickname(StrUtil.blankToDefault(user.getNickname(), "Dev调试"));
            user.setProfileCompleted(true);
            userMapper.updateById(user);
        }

        this.mockUserId = user.getId();
        this.mockToken = generateToken(mockUserId);
        this.initialized = true;
    }

    private String generateToken(String userId) {
        long expireSeconds = authProperties.getJwtExpireSeconds() == null
                ? 2592000L : authProperties.getJwtExpireSeconds();
        Date expireAt = new Date(System.currentTimeMillis() + expireSeconds * 1000);
        return JWT.create()
                .withClaim("userId", userId)
                .withExpiresAt(expireAt)
                .sign(algorithm);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/api/")) {
            chain.doFilter(request, response);
            return;
        }

        ensureMockUser();

        HttpServletRequestWrapper wrapper = new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                if ("Authorization".equalsIgnoreCase(name)) {
                    return "Bearer " + mockToken;
                }
                return super.getHeader(name);
            }
        };

        chain.doFilter(wrapper, response);
    }
}
