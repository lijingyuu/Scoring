package com.scoring.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private Boolean enabled = true;
    private Integer loginLimitPerMinute = 30;
    private Integer writeLimitPerMinute = 120;

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public Integer getLoginLimitPerMinute() {
        return loginLimitPerMinute;
    }

    public void setLoginLimitPerMinute(Integer loginLimitPerMinute) {
        this.loginLimitPerMinute = loginLimitPerMinute;
    }

    public Integer getWriteLimitPerMinute() {
        return writeLimitPerMinute;
    }

    public void setWriteLimitPerMinute(Integer writeLimitPerMinute) {
        this.writeLimitPerMinute = writeLimitPerMinute;
    }
}
