package com.scoring.backend.domain.vo;

/**
 * PC 扫码登录：轮询状态。
 * status: CREATED 待扫码 / SCANNED 已扫码待确认 / CONFIRMED 已确认(本次返回 token) /
 * CONSUMED token 已被取走 / EXPIRED 票据过期。
 */
public class PcLoginStatusVO {

    /** 票据过期/无效（虚拟态，驱动 PC 端展示"点击刷新"） */
    public static final String STATUS_EXPIRED = "EXPIRED";

    private String status;

    /** SCANNED/CONFIRMED 时的用户信息，供 PC 端核对登录身份 */
    private String nickname;
    private String avatarUrl;

    /** 仅首次轮询到 CONFIRMED 时返回（同时票据转 CONSUMED，一次性下发防重放） */
    private String token;
    private Boolean profileCompleted;

    public PcLoginStatusVO() {
    }

    public PcLoginStatusVO(String status) {
        this.status = status;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public Boolean getProfileCompleted() { return profileCompleted; }
    public void setProfileCompleted(Boolean profileCompleted) { this.profileCompleted = profileCompleted; }
}
