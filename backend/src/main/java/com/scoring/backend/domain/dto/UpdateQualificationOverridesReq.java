package com.scoring.backend.domain.dto;

import java.util.List;

public class UpdateQualificationOverridesReq {

    private List<Item> overrides;

    public List<Item> getOverrides() {
        return overrides;
    }

    public void setOverrides(List<Item> overrides) {
        this.overrides = overrides;
    }

    public static class Item {
        private Integer groupNo;
        private Integer rankSlot;
        private String playerId;

        public Integer getGroupNo() { return groupNo; }
        public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }
        public Integer getRankSlot() { return rankSlot; }
        public void setRankSlot(Integer rankSlot) { this.rankSlot = rankSlot; }
        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
    }
}
