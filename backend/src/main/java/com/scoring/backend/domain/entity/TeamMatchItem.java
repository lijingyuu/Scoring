package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("team_match_item")
public class TeamMatchItem {
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("match_id")
    private String matchId;

    @TableField("tournament_id")
    private String tournamentId;

    @TableField("display_order")
    private Integer displayOrder;

    @TableField("item_code")
    private String itemCode;

    @TableField("item_name")
    private String itemName;

    @TableField("player_count")
    private Integer playerCount;

    @TableField("left_member_ids_json")
    private String leftMemberIdsJson;

    @TableField("right_member_ids_json")
    private String rightMemberIdsJson;

    @TableField("child_match_id")
    private String childMatchId;

    @TableField("winner_side")
    private String winnerSide;

    private Integer status;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
    public String getLeftMemberIdsJson() { return leftMemberIdsJson; }
    public void setLeftMemberIdsJson(String leftMemberIdsJson) { this.leftMemberIdsJson = leftMemberIdsJson; }
    public String getRightMemberIdsJson() { return rightMemberIdsJson; }
    public void setRightMemberIdsJson(String rightMemberIdsJson) { this.rightMemberIdsJson = rightMemberIdsJson; }
    public String getChildMatchId() { return childMatchId; }
    public void setChildMatchId(String childMatchId) { this.childMatchId = childMatchId; }
    public String getWinnerSide() { return winnerSide; }
    public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
