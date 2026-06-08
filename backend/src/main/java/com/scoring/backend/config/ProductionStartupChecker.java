package com.scoring.backend.config;

import cn.hutool.core.util.StrUtil;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("prod")
public class ProductionStartupChecker implements ApplicationRunner {

    private final AuthProperties authProperties;
    private final WechatProperties wechatProperties;

    public ProductionStartupChecker(AuthProperties authProperties, WechatProperties wechatProperties) {
        this.authProperties = authProperties;
        this.wechatProperties = wechatProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (StrUtil.isBlank(authProperties.getJwtSecret())) {
            throw new IllegalStateException("JWT_SECRET must be configured in prod");
        }
        if (StrUtil.isBlank(wechatProperties.getAppId()) || StrUtil.isBlank(wechatProperties.getAppSecret())) {
            throw new IllegalStateException("WECHAT_APP_ID and WECHAT_APP_SECRET must be configured in prod");
        }
    }
}
