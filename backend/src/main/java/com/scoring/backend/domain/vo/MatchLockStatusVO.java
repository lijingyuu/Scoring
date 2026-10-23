package com.scoring.backend.domain.vo;

/**
 * 执裁会话锁只读探测结果（不触发抢锁）。
 *
 * 用途：前端在遮罩+顶替确认弹窗里判断「锁现在被谁持有」，据此决定是否提示接管与原因文案。
 * 刻意不下发持锁人 userId，只告知「是否同账号」，避免跨账号信息泄露。
 */
public class MatchLockStatusVO {

    /** 锁当前有效（lockedByUserId 非空且未过期），判活口径与 MatchLockService#requireActiveMatchLock 一致 */
    private boolean locked;
    /** locked 且持锁人就是当前登录用户 */
    private boolean sameAccount;
    /** 锁过期时间（yyyy-MM-dd HH:mm:ss，与 MatchLockVO.lockExpireTime 同格式；locked=false 时为 null） */
    private String lockExpireTime;

    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public boolean isSameAccount() { return sameAccount; }
    public void setSameAccount(boolean sameAccount) { this.sameAccount = sameAccount; }
    public String getLockExpireTime() { return lockExpireTime; }
    public void setLockExpireTime(String lockExpireTime) { this.lockExpireTime = lockExpireTime; }
}
