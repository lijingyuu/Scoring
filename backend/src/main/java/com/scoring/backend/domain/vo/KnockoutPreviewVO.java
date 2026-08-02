package com.scoring.backend.domain.vo;

import java.util.List;

public class KnockoutPreviewVO {

    private String id;
    private Integer knockoutSlots;
    private Integer qualifiersPerGroup;
    private Boolean allGroupMatchesFinished;
    private Boolean hasUnresolvedTie;
    private List<MatchVO> matches;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
    public Boolean getAllGroupMatchesFinished() { return allGroupMatchesFinished; }
    public void setAllGroupMatchesFinished(Boolean allGroupMatchesFinished) { this.allGroupMatchesFinished = allGroupMatchesFinished; }
    public Boolean getHasUnresolvedTie() { return hasUnresolvedTie; }
    public void setHasUnresolvedTie(Boolean hasUnresolvedTie) { this.hasUnresolvedTie = hasUnresolvedTie; }
    public List<MatchVO> getMatches() { return matches; }
    public void setMatches(List<MatchVO> matches) { this.matches = matches; }

    public static class MatchVO {
        private Integer slotIndex;
        private ParticipantVO leftPlayer;
        private ParticipantVO rightPlayer;

        public Integer getSlotIndex() { return slotIndex; }
        public void setSlotIndex(Integer slotIndex) { this.slotIndex = slotIndex; }
        public ParticipantVO getLeftPlayer() { return leftPlayer; }
        public void setLeftPlayer(ParticipantVO leftPlayer) { this.leftPlayer = leftPlayer; }
        public ParticipantVO getRightPlayer() { return rightPlayer; }
        public void setRightPlayer(ParticipantVO rightPlayer) { this.rightPlayer = rightPlayer; }
    }

    public static class ParticipantVO {
        private String playerId;
        private String playerName;
        private Integer groupNo;
        private Integer groupRank;
        private Integer seedRank;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Integer getGroupNo() { return groupNo; }
        public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }
        public Integer getGroupRank() { return groupRank; }
        public void setGroupRank(Integer groupRank) { this.groupRank = groupRank; }
        public Integer getSeedRank() { return seedRank; }
        public void setSeedRank(Integer seedRank) { this.seedRank = seedRank; }
    }
}
