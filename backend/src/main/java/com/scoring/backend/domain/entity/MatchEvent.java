package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

@TableName("match_event")
public class MatchEvent {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("match_id")
    private String matchId;

    @TableField("event_seq")
    private Integer eventSeq;

    @TableField("event_type")
    private String eventType;

    @TableField("game_no")
    private Integer gameNo;

    @TableField("left_score")
    private Integer leftScore;

    @TableField("right_score")
    private Integer rightScore;

    @TableField("serve_side")
    private String serveSide;

    @TableField("payload_json")
    private String payloadJson;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public Integer getEventSeq() { return eventSeq; }
    public void setEventSeq(Integer eventSeq) { this.eventSeq = eventSeq; }
    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }
    public Integer getGameNo() { return gameNo; }
    public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
    public Integer getLeftScore() { return leftScore; }
    public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
    public Integer getRightScore() { return rightScore; }
    public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
    public String getServeSide() { return serveSide; }
    public void setServeSide(String serveSide) { this.serveSide = serveSide; }
    public String getPayloadJson() { return payloadJson; }
    public void setPayloadJson(String payloadJson) { this.payloadJson = payloadJson; }
}
