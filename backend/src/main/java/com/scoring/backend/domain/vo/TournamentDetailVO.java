package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.TournamentRoundRule;

import java.util.List;

public class TournamentDetailVO implements TournamentMatchAccessVO {

    private String id;
    private String name;
    private String location;
    private Integer status;
    private Integer sportType;
    private Integer participantType;
    private Integer teamMatchTemplate;
    private List<TeamMatchItemVO> teamMatchItems;
    private Integer tournamentType;
    private Integer knockoutSlots;
    private Integer qualifiersPerGroup;
    private Integer roundRobinRounds;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private Boolean roundRuleEnabled;
    private List<TournamentRoundRule> roundRules;
    private Integer favoriteCount;
    private String creatorUserId;
    private String createTime;
    private Boolean archived;
    private Boolean favorite;
    private Boolean creator;
    private Boolean refereeGranted;
    private Boolean canOperateMatches;
    private Boolean canManageReferees;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSportType() { return sportType; }
    public void setSportType(Integer sportType) { this.sportType = sportType; }
    public Integer getParticipantType() { return participantType; }
    public void setParticipantType(Integer participantType) { this.participantType = participantType; }
    public Integer getTeamMatchTemplate() { return teamMatchTemplate; }
    public void setTeamMatchTemplate(Integer teamMatchTemplate) { this.teamMatchTemplate = teamMatchTemplate; }
    public List<TeamMatchItemVO> getTeamMatchItems() { return teamMatchItems; }
    public void setTeamMatchItems(List<TeamMatchItemVO> teamMatchItems) { this.teamMatchItems = teamMatchItems; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
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
    public Boolean getRoundRuleEnabled() { return roundRuleEnabled; }
    public void setRoundRuleEnabled(Boolean roundRuleEnabled) { this.roundRuleEnabled = roundRuleEnabled; }
    public List<TournamentRoundRule> getRoundRules() { return roundRules; }
    public void setRoundRules(List<TournamentRoundRule> roundRules) { this.roundRules = roundRules; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
    public Boolean getRefereeGranted() { return refereeGranted; }
    public void setRefereeGranted(Boolean refereeGranted) { this.refereeGranted = refereeGranted; }
    public Boolean getCanOperateMatches() { return canOperateMatches; }
    public void setCanOperateMatches(Boolean canOperateMatches) { this.canOperateMatches = canOperateMatches; }
    public Boolean getCanManageReferees() { return canManageReferees; }
    public void setCanManageReferees(Boolean canManageReferees) { this.canManageReferees = canManageReferees; }
}
