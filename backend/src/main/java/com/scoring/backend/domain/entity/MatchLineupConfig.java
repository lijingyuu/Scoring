package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("match_lineup_config")
public class MatchLineupConfig {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("match_id")
    private String matchId;

    @TableField("game_no")
    private Integer gameNo;

    @TableField("left_court_json")
    private String leftCourtJson;

    @TableField("right_court_json")
    private String rightCourtJson;

    @TableField("left_middle_pair_indexes_json")
    private String leftMiddlePairIndexesJson;

    @TableField("right_middle_pair_indexes_json")
    private String rightMiddlePairIndexesJson;

    @TableField("left_libero1_id")
    private String leftLibero1Id;

    @TableField("left_libero2_id")
    private String leftLibero2Id;

    @TableField("right_libero1_id")
    private String rightLibero1Id;

    @TableField("right_libero2_id")
    private String rightLibero2Id;

    @TableField("serve_side")
    private String serveSide;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public Integer getGameNo() { return gameNo; }
    public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
    public String getLeftCourtJson() { return leftCourtJson; }
    public void setLeftCourtJson(String leftCourtJson) { this.leftCourtJson = leftCourtJson; }
    public String getRightCourtJson() { return rightCourtJson; }
    public void setRightCourtJson(String rightCourtJson) { this.rightCourtJson = rightCourtJson; }
    public String getLeftMiddlePairIndexesJson() { return leftMiddlePairIndexesJson; }
    public void setLeftMiddlePairIndexesJson(String leftMiddlePairIndexesJson) { this.leftMiddlePairIndexesJson = leftMiddlePairIndexesJson; }
    public String getRightMiddlePairIndexesJson() { return rightMiddlePairIndexesJson; }
    public void setRightMiddlePairIndexesJson(String rightMiddlePairIndexesJson) { this.rightMiddlePairIndexesJson = rightMiddlePairIndexesJson; }
    public String getLeftLibero1Id() { return leftLibero1Id; }
    public void setLeftLibero1Id(String leftLibero1Id) { this.leftLibero1Id = leftLibero1Id; }
    public String getLeftLibero2Id() { return leftLibero2Id; }
    public void setLeftLibero2Id(String leftLibero2Id) { this.leftLibero2Id = leftLibero2Id; }
    public String getRightLibero1Id() { return rightLibero1Id; }
    public void setRightLibero1Id(String rightLibero1Id) { this.rightLibero1Id = rightLibero1Id; }
    public String getRightLibero2Id() { return rightLibero2Id; }
    public void setRightLibero2Id(String rightLibero2Id) { this.rightLibero2Id = rightLibero2Id; }
    public String getServeSide() { return serveSide; }
    public void setServeSide(String serveSide) { this.serveSide = serveSide; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
