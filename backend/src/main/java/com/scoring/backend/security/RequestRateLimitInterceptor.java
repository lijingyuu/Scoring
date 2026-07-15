package com.scoring.backend.security;

import com.scoring.backend.common.RequestRateLimiter;
import com.scoring.backend.config.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

@Component
public class RequestRateLimitInterceptor implements HandlerInterceptor {

    private final RequestRateLimiter requestRateLimiter;
    private final RateLimitProperties rateLimitProperties;

    public RequestRateLimitInterceptor(RequestRateLimiter requestRateLimiter,
                                       RateLimitProperties rateLimitProperties) {
        this.requestRateLimiter = requestRateLimiter;
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!Boolean.TRUE.equals(rateLimitProperties.getEnabled())) {
            return true;
        }

        String method = request.getMethod();
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        String path = request.getRequestURI();
        String clientIp = resolveClientIp(request);

        if (isLoginEndpoint(path) && "POST".equalsIgnoreCase(method)) {
            enforceLimit("login", clientIp, rateLimitProperties.getLoginLimitPerMinute(), "登录过于频繁，请稍后再试");
            return true;
        }

        if (isWriteMethod(method) && path.startsWith("/api/")) {
            enforceLimit("write", clientIp, rateLimitProperties.getWriteLimitPerMinute(), "操作过于频繁，请稍后再试");
        }

        return true;
    }

    private void enforceLimit(String bucket, String clientIp, Integer limit, String message) {
        boolean allowed = requestRateLimiter.allow(bucket + ":" + clientIp, limit == null ? 0 : limit, Duration.ofMinutes(1));
        if (!allowed) {
            throw new TooManyRequestsException(message);
        }
    }

    private boolean isWriteMethod(String method) {
        return "POST".equalsIgnoreCase(method)
                || "PUT".equalsIgnoreCase(method)
                || "DELETE".equalsIgnoreCase(method)
                || "PATCH".equalsIgnoreCase(method);
    }

    private boolean isLoginEndpoint(String path) {
        return "/api/v1/auth/wechat-login".equals(path)
                || "/api/v1/auth/register".equals(path)
                || "/api/v1/auth/password-login".equals(path);
    }

    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, Boolean.TRUE.equals(rateLimitProperties.getTrustProxyHeaders()));
    }
}
