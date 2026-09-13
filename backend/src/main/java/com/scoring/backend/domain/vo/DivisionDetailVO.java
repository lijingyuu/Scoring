package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TournamentRoundRule;

import java.util.List;

/**
 * 组别详情：一个组别 = 一个完整子赛事（赛制 + 规则 + 选手 + 进度）。
 */
public class DivisionDetailVO {

    private String tournamentId;
    private String tournamentName;
    private String divisionId;
    private String divisionName;
    private Integer sortOrder;
    private Integer status;
    private Integer sportType;
    private Integer participantType;
    private Integer tournamentType;
    private Integer knockoutSlots;
    private Integer knockoutRounds;
    private Integer qualifiersPerGroup;
    private Integer roundRobinRounds;
    private Integer currentStage;
    private Boolean knockoutGenerated;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private Boolean thirdPlaceEnabled;
    private Integer thirdPlaceBestOf;
    private Integer thirdPlaceGamesToWin;
    private Integer thirdPlacePointsToWin;
    private Integer thirdPlaceDecidingPointsToWin;
    private Boolean thirdPlaceEnableDeuce;
    private Integer thirdPlaceCapPoint;
    private Boolean roundRuleEnabled;
    private List<TournamentRoundRule> roundRules;
    private List<Player> players;
    private Boolean creator;

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getTournamentName() { return tournamentName; }
    public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
    public String getDivisionId() { return divisionId; }
    public void setDivisionId(String divisionId) { this.divisionId = divisionId; }
    public String getDivisionName() { return divisionName; }
    public void setDivisionName(String divisionName) { this.divisionName = divisionName; }
    public Integer getSortOrder() { return sortOrder; }
    public void setSortOrder(Integer sortOrder) { this.sortOrder = sortOrder; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSportType() { return sportType; }
    public void setSportType(Integer sportType) { this.sportType = sportType; }
    public Integer getParticipantType() { return participantType; }
    public void setParticipantType(Integer participantType) { this.participantType = participantType; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getKnockoutRounds() { return knockoutRounds; }
    public void setKnockoutRounds(Integer knockoutRounds) { this.knockoutRounds = knockoutRounds; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
    public Integer getRoundRobinRounds() { return roundRobinRounds; }
    public void setRoundRobinRounds(Integer roundRobinRounds) { this.roundRobinRounds = roundRobinRounds; }
    public Integer getCurrentStage() { return currentStage; }
    public void setCurrentStage(Integer currentStage) { this.currentStage = currentStage; }
    public Boolean getKnockoutGenerated() { return knockoutGenerated; }
    public void setKnockoutGenerated(Boolean knockoutGenerated) { this.knockoutGenerated = knockoutGenerated; }
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
    public Integer getThirdPlaceBestOf() { return thirdPlaceBestOf; }
    public void setThirdPlaceBestOf(Integer thirdPlaceBestOf) { this.thirdPlaceBestOf = thirdPlaceBestOf; }
    public Integer getThirdPlaceGamesToWin() { return thirdPlaceGamesToWin; }
    public void setThirdPlaceGamesToWin(Integer thirdPlaceGamesToWin) { this.thirdPlaceGamesToWin = thirdPlaceGamesToWin; }
    public Integer getThirdPlacePointsToWin() { return thirdPlacePointsToWin; }
    public void setThirdPlacePointsToWin(Integer thirdPlacePointsToWin) { this.thirdPlacePointsToWin = thirdPlacePointsToWin; }
    public Integer getThirdPlaceDecidingPointsToWin() { return thirdPlaceDecidingPointsToWin; }
    public void setThirdPlaceDecidingPointsToWin(Integer thirdPlaceDecidingPointsToWin) { this.thirdPlaceDecidingPointsToWin = thirdPlaceDecidingPointsToWin; }
    public Boolean getThirdPlaceEnableDeuce() { return thirdPlaceEnableDeuce; }
    public void setThirdPlaceEnableDeuce(Boolean thirdPlaceEnableDeuce) { this.thirdPlaceEnableDeuce = thirdPlaceEnableDeuce; }
    public Integer getThirdPlaceCapPoint() { return thirdPlaceCapPoint; }
    public void setThirdPlaceCapPoint(Integer thirdPlaceCapPoint) { this.thirdPlaceCapPoint = thirdPlaceCapPoint; }
    public Boolean getRoundRuleEnabled() { return roundRuleEnabled; }
    public void setRoundRuleEnabled(Boolean roundRuleEnabled) { this.roundRuleEnabled = roundRuleEnabled; }
    public List<TournamentRoundRule> getRoundRules() { return roundRules; }
    public void setRoundRules(List<TournamentRoundRule> roundRules) { this.roundRules = roundRules; }
    public List<Player> getPlayers() { return players; }
    public void setPlayers(List<Player> players) { this.players = players; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
}
