package com.scoring.backend.domain.vo;

public class TournamentRefereeVO {

    private String userId;
    private String nickname;
    private String avatarUrl;
    private String grantedAt;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getNickname() { return nickname; }
    public void setNickname(String nickname) { this.nickname = nickname; }
    public String getAvatarUrl() { return avatarUrl; }
    public void setAvatarUrl(String avatarUrl) { this.avatarUrl = avatarUrl; }
    public String getGrantedAt() { return grantedAt; }
    public void setGrantedAt(String grantedAt) { this.grantedAt = grantedAt; }
}
