package com.scoring.backend.common;

import com.scoring.backend.config.AuthProperties;
import com.scoring.backend.config.CorsProperties;
import com.scoring.backend.config.ProductionStartupChecker;
import com.scoring.backend.config.WechatProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProductionStartupCheckerTest {

    @Test
    void run_shortOrDefaultJwtSecret_shouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> buildChecker("change-me-jwt-secret").run(args()));
        assertThrows(IllegalStateException.class,
                () -> buildChecker("short-secret").run(args()));
    }

    @Test
    void run_exampleCorsOrigin_shouldThrow() {
        assertThrows(IllegalStateException.class,
                () -> buildChecker("0123456789abcdef0123456789abcdef",
                        "https://www.example.com").run(args()));
    }

    @Test
    void run_validProdConfig_shouldPass() {
        assertDoesNotThrow(() -> buildChecker("0123456789abcdef0123456789abcdef").run(args()));
    }

    private ProductionStartupChecker buildChecker(String jwtSecret) {
        return buildChecker(jwtSecret, "https://scoreboard.example.org");
    }

    private ProductionStartupChecker buildChecker(String jwtSecret, String allowedOrigin) {
        AuthProperties authProperties = new AuthProperties();
        authProperties.setJwtSecret(jwtSecret);

        WechatProperties wechatProperties = new WechatProperties();
        wechatProperties.setAppId("wx-app-id");
        wechatProperties.setAppSecret("wx-app-secret");

        CorsProperties corsProperties = new CorsProperties();
        corsProperties.setAllowedOrigins(java.util.List.of(allowedOrigin));

        return new ProductionStartupChecker(authProperties, wechatProperties, corsProperties);
    }

    private ApplicationArguments args() {
        return new org.springframework.boot.DefaultApplicationArguments();
    }
}
