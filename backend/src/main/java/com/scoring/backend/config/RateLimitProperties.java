package com.scoring.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.rate-limit")
public class RateLimitProperties {

    private Boolean enabled = true;
    private Integer loginLimitPerMinute = 30;
    private Integer writeLimitPerMinute = 120;
    /** PC 扫码轮询 GET /auth/pc/status：单 ticket 每分钟上限（前端 1.5s 轮询 = 40 次/分，默认 60 留余量） */
    private Integer pcStatusTicketLimitPerMinute = 60;
    /** PC 扫码轮询 GET /auth/pc/status：单 IP 每分钟上限（默认 240 ≈ NAT 下 6 人同时扫码） */
    private Integer pcStatusIpLimitPerMinute = 240;
    private Boolean trustProxyHeaders = false;

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

    public Integer getPcStatusTicketLimitPerMinute() {
        return pcStatusTicketLimitPerMinute;
    }

    public void setPcStatusTicketLimitPerMinute(Integer pcStatusTicketLimitPerMinute) {
        this.pcStatusTicketLimitPerMinute = pcStatusTicketLimitPerMinute;
    }

    public Integer getPcStatusIpLimitPerMinute() {
        return pcStatusIpLimitPerMinute;
    }

    public void setPcStatusIpLimitPerMinute(Integer pcStatusIpLimitPerMinute) {
        this.pcStatusIpLimitPerMinute = pcStatusIpLimitPerMinute;
    }

    public Boolean getTrustProxyHeaders() {
        return trustProxyHeaders;
    }

    public void setTrustProxyHeaders(Boolean trustProxyHeaders) {
        this.trustProxyHeaders = trustProxyHeaders;
    }
}
