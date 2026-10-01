package com.scoring.backend.domain.vo;

/**
 * 组别摘要（赛事详情页组别列表用）。
 */
public class DivisionSummaryVO {

    private String divisionId;
    private String name;
    private Integer sortOrder;
    private Integer status;
    private Integer tournamentType;
    private Boolean knockoutGenerated;
    private Integer drawMode;
    /** type1 专用展示信号：小组赛是否已有开赛痕迹（近似判定，服务端守卫为准）；非 type1 恒 false */
    private Boolean groupStageStarted;
    private Integer currentStage;
    private Integer playerCount;
    private Integer knockoutSlots;
    private Integer knockoutRounds;
    private Integer qualifiersPerGroup;
    private Integer roundRobinRounds;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private Boolean thirdPlaceEnabled;

    public String getDivisionId() { return divisionId; }
    public void setDivisionId(String divisionId) { this.divisionId = divisionId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Boolean getKnockoutGenerated() { return knockoutGenerated; }
    public void setKnockoutGenerated(Boolean knockoutGenerated) { this.knockoutGenerated = knockoutGenerated; }
    public Integer getDrawMode() { return drawMode; }
    public void setDrawMode(Integer drawMode) { this.drawMode = drawMode; }
    public Boolean getGroupStageStarted() { return groupStageStarted; }
    public void setGroupStageStarted(Boolean groupStageStarted) { this.groupStageStarted = groupStageStarted; }
    public Integer getCurrentStage() { return currentStage; }
    public void setCurrentStage(Integer currentStage) { this.currentStage = currentStage; }
    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getKnockoutRounds() { return knockoutRounds; }
    public void setKnockoutRounds(Integer knockoutRounds) { this.knockoutRounds = knockoutRounds; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
    public Integer getRoundRobinRounds() { return roundRobinRounds; }
    public void setRoundRobinRounds(Integer roundRobinRounds) { this.roundRobinRounds = roundRobinRounds; }
    public Integer getBestOf() { return bestOf; }
    public void setBestOf(Integer bestOf) { this.bestOf = bestOf; }
    public Integer getGamesToWin() { return gamesToWin; }
    public void setGamesToWin(Integer gamesToWin) { this.gamesToWin = gamesToWin; }
    public Integer getPointsToWin() { return pointsToWin; }
    public void setPointsToWin(Integer pointsToWin) { this.pointsToWin = pointsToWin; }
    public Integer getDecidingPointsToWin() { return decidingPointsToWin; }
    public void setDecidingPointsToWin(Integer decidingPointsToWin) { this.decidingPointsToWin = decidingPointsToWin; }
    public Boolean getEnableDeuce() { return enableDeuce; }
    public void setEnableDeuce(Boolean enableDeuce) { this.enableDeuce = enableDeuce; }
    public Integer getCapPoint() { return capPoint; }
    public void setCapPoint(Integer capPoint) { this.capPoint = capPoint; }
    public Boolean getThirdPlaceEnabled() { return thirdPlaceEnabled; }
    public void setThirdPlaceEnabled(Boolean thirdPlaceEnabled) { this.thirdPlaceEnabled = thirdPlaceEnabled; }
}
