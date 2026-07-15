package com.scoring.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    private String jwtSecret;
    private Long jwtExpireSeconds = 2592000L;
    private Boolean registrationEnabled = true;

    public String getJwtSecret() {
        return jwtSecret;
    }

    public void setJwtSecret(String jwtSecret) {
        this.jwtSecret = jwtSecret;
    }

    public Long getJwtExpireSeconds() {
        return jwtExpireSeconds;
    }

    public void setJwtExpireSeconds(Long jwtExpireSeconds) {
        this.jwtExpireSeconds = jwtExpireSeconds;
    }

    public Boolean getRegistrationEnabled() {
        return registrationEnabled;
    }

    public void setRegistrationEnabled(Boolean registrationEnabled) {
        this.registrationEnabled = registrationEnabled;
    }
}
