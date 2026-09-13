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
    private Integer currentStage;
    private Integer playerCount;

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
    public Integer getCurrentStage() { return currentStage; }
    public void setCurrentStage(Integer currentStage) { this.currentStage = currentStage; }
    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
}
