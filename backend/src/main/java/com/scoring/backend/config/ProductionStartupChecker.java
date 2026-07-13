package com.scoring.backend.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("prod")
public class ProductionStartupChecker implements ApplicationRunner {

    private static final int MIN_JWT_SECRET_LENGTH = 32;
    private static final String DEFAULT_JWT_SECRET = "change-me-jwt-secret";

    private final AuthProperties authProperties;
    private final WechatProperties wechatProperties;
    private final CorsProperties corsProperties;

    public ProductionStartupChecker(AuthProperties authProperties,
                                    WechatProperties wechatProperties,
                                    CorsProperties corsProperties) {
        this.authProperties = authProperties;
        this.wechatProperties = wechatProperties;
        this.corsProperties = corsProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        String jwtSecret = authProperties.getJwtSecret();
        if (StrUtil.isBlank(jwtSecret)
                || DEFAULT_JWT_SECRET.equals(jwtSecret)
                || jwtSecret.length() < MIN_JWT_SECRET_LENGTH) {
            throw new IllegalStateException("JWT_SECRET must be configured in prod with at least 32 characters");
        }
        if (StrUtil.isBlank(wechatProperties.getAppId()) || StrUtil.isBlank(wechatProperties.getAppSecret())) {
            throw new IllegalStateException("WECHAT_APP_ID and WECHAT_APP_SECRET must be configured in prod");
        }
        validateCorsAllowedOrigins();
    }

    private void validateCorsAllowedOrigins() {
        List<String> allowedOrigins = corsProperties.getAllowedOrigins() == null
                ? List.of()
                : corsProperties.getAllowedOrigins().stream()
                .filter(StrUtil::isNotBlank)
                .toList();
        boolean invalid = allowedOrigins.isEmpty()
                || allowedOrigins.stream().anyMatch(origin -> "*".equals(origin)
                || origin.contains("example.com"));
        if (invalid) {
            throw new IllegalStateException("CORS allowed origins must be configured with real domains in prod");
        }
    }
}
