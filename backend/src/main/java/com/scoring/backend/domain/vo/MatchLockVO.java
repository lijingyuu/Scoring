package com.scoring.backend.domain.vo;

public class MatchLockVO {

    private Boolean success;
    private Boolean editable;
    private Boolean sameSession;
    /** 本次加锁顶掉了同账号另一设备的仍有效旧锁（首次加锁/过期接管为 false），前端据此提示接管 */
    private Boolean kicked;
    private String lockedByUserId;
    private String lockExpireTime;

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Boolean getEditable() { return editable; }
    public void setEditable(Boolean editable) { this.editable = editable; }
    public Boolean getSameSession() { return sameSession; }
    public void setSameSession(Boolean sameSession) { this.sameSession = sameSession; }
    public Boolean getKicked() { return kicked; }
    public void setKicked(Boolean kicked) { this.kicked = kicked; }
    public String getLockedByUserId() { return lockedByUserId; }
    public void setLockedByUserId(String lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public String getLockExpireTime() { return lockExpireTime; }
    public void setLockExpireTime(String lockExpireTime) { this.lockExpireTime = lockExpireTime; }
}
