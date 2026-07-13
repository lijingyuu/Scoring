package com.scoring.backend.common;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.scoring.backend.config.RateLimitProperties;
import com.scoring.backend.security.ClientIpResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    private final RateLimitProperties rateLimitProperties;

    public RequestLoggingFilter(RateLimitProperties rateLimitProperties) {
        this.rateLimitProperties = rateLimitProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "/health".equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startAt = System.currentTimeMillis();
        try {
            filterChain.doFilter(request, response);
        } catch (Exception ex) {
            long cost = System.currentTimeMillis() - startAt;
            log.error("request_failed method={} path={} status={} costMs={} clientIp={} message={}",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    cost,
                    resolveClientIp(request),
                    ex.getMessage(),
                    ex);
            throw ex;
        }

        long cost = System.currentTimeMillis() - startAt;
        log.info("request_done method={} path={} status={} costMs={} clientIp={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                cost,
                resolveClientIp(request));
    }

    private String resolveClientIp(HttpServletRequest request) {
        return ClientIpResolver.resolve(request, Boolean.TRUE.equals(rateLimitProperties.getTrustProxyHeaders()));
    }
}
