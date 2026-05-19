package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("match_record")
public class MatchRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tournament_id")
    private String tournamentId;

    @TableField("round_num")
    private Integer roundNum;

    @TableField("left_player_id")
    private String leftPlayerId;

    @TableField("right_player_id")
    private String rightPlayerId;

    @TableField("score_display")
    private String scoreDisplay;

    @TableField("winner_id")
    private String winnerId;

    private Integer status;

    @TableField("next_match_id")
    private String nextMatchId;

    @TableField("next_match_slot")
    private String nextMatchSlot;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getRoundNum() { return roundNum; }
    public void setRoundNum(Integer roundNum) { this.roundNum = roundNum; }
    public String getLeftPlayerId() { return leftPlayerId; }
    public void setLeftPlayerId(String leftPlayerId) { this.leftPlayerId = leftPlayerId; }
    public String getRightPlayerId() { return rightPlayerId; }
    public void setRightPlayerId(String rightPlayerId) { this.rightPlayerId = rightPlayerId; }
    public String getScoreDisplay() { return scoreDisplay; }
    public void setScoreDisplay(String scoreDisplay) { this.scoreDisplay = scoreDisplay; }
    public String getWinnerId() { return winnerId; }
    public void setWinnerId(String winnerId) { this.winnerId = winnerId; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public String getNextMatchId() { return nextMatchId; }
    public void setNextMatchId(String nextMatchId) { this.nextMatchId = nextMatchId; }
    public String getNextMatchSlot() { return nextMatchSlot; }
    public void setNextMatchSlot(String nextMatchSlot) { this.nextMatchSlot = nextMatchSlot; }
}
