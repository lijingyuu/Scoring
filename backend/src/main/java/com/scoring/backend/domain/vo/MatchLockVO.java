package com.scoring.backend.domain.vo;

public class MatchLockVO {

    private Boolean success;
    private Boolean editable;
    private String lockedByUserId;
    private String lockExpireTime;

    public Boolean getSuccess() { return success; }
    public void setSuccess(Boolean success) { this.success = success; }
    public Boolean getEditable() { return editable; }
    public void setEditable(Boolean editable) { this.editable = editable; }
    public String getLockedByUserId() { return lockedByUserId; }
    public void setLockedByUserId(String lockedByUserId) { this.lockedByUserId = lockedByUserId; }
    public String getLockExpireTime() { return lockExpireTime; }
    public void setLockExpireTime(String lockExpireTime) { this.lockExpireTime = lockExpireTime; }
}
