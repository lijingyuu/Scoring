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
 
         if (isPcStatusEndpoint(path) && "GET".equalsIgnoreCase(method)) {
             // PC 扫码轮询：免登录 GET 且每次轮询都查库，必须限流防打满 DB（P2-5）。
             // 双维度：每 ticket（前端 1.5s 轮询 = 40 次/分，留 50% 余量）
             //       + 每 IP（容忍校园网 NAT 下多人同时扫码登录）
             String ticket = request.getParameter("ticket");
             if (ticket != null && !ticket.isBlank()) {
                 enforceLimit("pcstatus-ticket", ticket.trim(), rateLimitProperties.getPcStatusTicketLimitPerMinute(), "轮询过于频繁，请稍后再试");
             }
             enforceLimit("pcstatus-ip", clientIp, rateLimitProperties.getPcStatusIpLimitPerMinute(), "操作过于频繁，请稍后再试");
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
                || "/api/v1/auth/password-login".equals(path)
                || "/api/v1/auth/pc/qr-code".equals(path);
    }
 
     private boolean isPcStatusEndpoint(String path) {
         return "/api/v1/auth/pc/status".equals(path);
     }

    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, Boolean.TRUE.equals(rateLimitProperties.getTrustProxyHeaders()));
    }
}
