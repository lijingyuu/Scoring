package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tournament_qualification_override")
public class TournamentQualificationOverride {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tournament_id")
    private String tournamentId;

    @TableField("group_no")
    private Integer groupNo;

    @TableField("rank_slot")
    private Integer rankSlot;

    @TableField("player_id")
    private String playerId;

    @TableField("operator_user_id")
    private String operatorUserId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getGroupNo() { return groupNo; }
    public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }
    public Integer getRankSlot() { return rankSlot; }
    public void setRankSlot(Integer rankSlot) { this.rankSlot = rankSlot; }
    public String getPlayerId() { return playerId; }
    public void setPlayerId(String playerId) { this.playerId = playerId; }
    public String getOperatorUserId() { return operatorUserId; }
    public void setOperatorUserId(String operatorUserId) { this.operatorUserId = operatorUserId; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
