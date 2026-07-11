package com.scoring.backend.domain.vo;

import java.util.List;

public class TeamMatchLineupVO {
    private String matchId;
    private String tournamentId;
    private Integer teamMatchTemplate;
    private Integer matchStatus;
    private Integer stageType;
    private Integer tournamentType;
    private Integer relayBaseScore;
    private Integer relayMemberCount;
    private Integer relayTargetScore;
    private String scoreDisplay;
    private String winnerSide;
    private TeamVO leftTeam;
    private TeamVO rightTeam;
    private List<ItemVO> items;

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getTeamMatchTemplate() { return teamMatchTemplate; }
    public void setTeamMatchTemplate(Integer teamMatchTemplate) { this.teamMatchTemplate = teamMatchTemplate; }
    public Integer getMatchStatus() { return matchStatus; }
    public void setMatchStatus(Integer matchStatus) { this.matchStatus = matchStatus; }
    public Integer getStageType() { return stageType; }
    public void setStageType(Integer stageType) { this.stageType = stageType; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getRelayBaseScore() { return relayBaseScore; }
    public void setRelayBaseScore(Integer relayBaseScore) { this.relayBaseScore = relayBaseScore; }
    public Integer getRelayMemberCount() { return relayMemberCount; }
    public void setRelayMemberCount(Integer relayMemberCount) { this.relayMemberCount = relayMemberCount; }
    public Integer getRelayTargetScore() { return relayTargetScore; }
    public void setRelayTargetScore(Integer relayTargetScore) { this.relayTargetScore = relayTargetScore; }
    public String getScoreDisplay() { return scoreDisplay; }
    public void setScoreDisplay(String scoreDisplay) { this.scoreDisplay = scoreDisplay; }
    public String getWinnerSide() { return winnerSide; }
    public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    public TeamVO getLeftTeam() { return leftTeam; }
    public void setLeftTeam(TeamVO leftTeam) { this.leftTeam = leftTeam; }
    public TeamVO getRightTeam() { return rightTeam; }
    public void setRightTeam(TeamVO rightTeam) { this.rightTeam = rightTeam; }
    public List<ItemVO> getItems() { return items; }
    public void setItems(List<ItemVO> items) { this.items = items; }

    public static class TeamVO {
        private String id;
        private String name;
        private List<MemberVO> members;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<MemberVO> getMembers() { return members; }
        public void setMembers(List<MemberVO> members) { this.members = members; }
    }

    public static class MemberVO {
        private String id;
        private String name;
        private Boolean captain;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Boolean getCaptain() { return captain; }
        public void setCaptain(Boolean captain) { this.captain = captain; }
    }

    public static class ItemVO {
        private String id;
        private Integer displayOrder;
        private String itemCode;
        private String itemName;
        private Integer playerCount;
        private List<MemberVO> leftMembers;
        private List<MemberVO> rightMembers;
        private Integer status;
        private String childMatchId;
        private String winnerSide;
        private String childScoreDisplay;
        private Integer childLeftGameWins;
        private Integer childRightGameWins;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public Integer getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public String getItemName() { return itemName; }
        public void setItemName(String itemName) { this.itemName = itemName; }
        public Integer getPlayerCount() { return playerCount; }
        public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
        public List<MemberVO> getLeftMembers() { return leftMembers; }
        public void setLeftMembers(List<MemberVO> leftMembers) { this.leftMembers = leftMembers; }
        public List<MemberVO> getRightMembers() { return rightMembers; }
        public void setRightMembers(List<MemberVO> rightMembers) { this.rightMembers = rightMembers; }
        public Integer getStatus() { return status; }
        public void setStatus(Integer status) { this.status = status; }
        public String getChildMatchId() { return childMatchId; }
        public void setChildMatchId(String childMatchId) { this.childMatchId = childMatchId; }
        public String getWinnerSide() { return winnerSide; }
        public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
        public String getChildScoreDisplay() { return childScoreDisplay; }
        public void setChildScoreDisplay(String childScoreDisplay) { this.childScoreDisplay = childScoreDisplay; }
        public Integer getChildLeftGameWins() { return childLeftGameWins; }
        public void setChildLeftGameWins(Integer childLeftGameWins) { this.childLeftGameWins = childLeftGameWins; }
        public Integer getChildRightGameWins() { return childRightGameWins; }
        public void setChildRightGameWins(Integer childRightGameWins) { this.childRightGameWins = childRightGameWins; }
    }
}
