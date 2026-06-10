package com.scoring.backend.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public class SaveMatchEventsReq {

    @Valid
    @NotEmpty(message = "events不能为空")
    private List<EventItem> events;

    public List<EventItem> getEvents() { return events; }
    public void setEvents(List<EventItem> events) { this.events = events; }

    public static class EventItem {

        @NotNull(message = "eventSeq不能为空")
        @Positive(message = "eventSeq必须大于0")
        private Integer eventSeq;

        @NotBlank(message = "eventType不能为空")
        private String eventType;

        @NotNull(message = "gameNo不能为空")
        @Positive(message = "gameNo必须大于0")
        private Integer gameNo;

        @NotNull(message = "leftScore不能为空")
        private Integer leftScore;

        @NotNull(message = "rightScore不能为空")
        private Integer rightScore;

        @NotBlank(message = "serveSide不能为空")
        private String serveSide;

        @NotBlank(message = "payloadJson不能为空")
        private String payloadJson;

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
}
