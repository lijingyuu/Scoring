package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tournament_referee_grant")
public class TournamentRefereeGrant {

    /** 授权角色：赛务编排管理员（可出线覆盖/排签/重开已完赛比赛，含执裁能力） */
    public static final String GRANT_ROLE_ADMIN = "ADMIN";
    /** 授权角色：现场执裁裁判（仅记分/阵容/战报等单场操作） */
    public static final String GRANT_ROLE_SCORER = "SCORER";

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tournament_id")
    private String tournamentId;

    @TableField("user_id")
    private String userId;

    /** 授权角色（ADMIN/SCORER），历史数据迁移后默认 SCORER */
    @TableField("grant_role")
    private String grantRole;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public String getGrantRole() { return grantRole; }
    public void setGrantRole(String grantRole) { this.grantRole = grantRole; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
