package com.scoring.backend.domain.vo;

/**
 * 赛事读取类 VO 的权限态契约（详情 / 小组赛 / 签表共用）。
 */
public interface TournamentMatchAccessVO {

    Boolean getRefereeGranted();

    void setRefereeGranted(Boolean refereeGranted);

    Boolean getCanOperateMatches();

    void setCanOperateMatches(Boolean canOperateMatches);

    Boolean getCanManageReferees();

    void setCanManageReferees(Boolean canManageReferees);

    /** 当前用户裁判角色：null（未认证）/ "ADMIN"（赛务编排管理员）/ "SCORER"（现场执裁裁判） */
    String getRefereeRole();

    void setRefereeRole(String refereeRole);

    /** 是否可执行赛务编排操作（出线覆盖 / 淘汰赛预览与生成）：创建者或 ADMIN 且未归档 */
    Boolean getCanManageKnockout();

    void setCanManageKnockout(Boolean canManageKnockout);
}
