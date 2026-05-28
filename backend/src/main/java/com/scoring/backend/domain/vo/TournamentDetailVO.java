package com.scoring.backend.domain.vo;

public class TournamentDetailVO {

    private String id;
    private String name;
    private String location;
    private Integer status;
    private Integer tournamentType;
    private Integer knockoutSlots;
    private Integer qualifiersPerGroup;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private Integer favoriteCount;
    private String creatorUserId;
    private String createTime;
    private Boolean favorite;
    private Boolean creator;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
    public Integer getBestOf() { return bestOf; }
    public void setBestOf(Integer bestOf) { this.bestOf = bestOf; }
    public Integer getGamesToWin() { return gamesToWin; }
    public void setGamesToWin(Integer gamesToWin) { this.gamesToWin = gamesToWin; }
    public Integer getPointsToWin() { return pointsToWin; }
    public void setPointsToWin(Integer pointsToWin) { this.pointsToWin = pointsToWin; }
    public Boolean getEnableDeuce() { return enableDeuce; }
    public void setEnableDeuce(Boolean enableDeuce) { this.enableDeuce = enableDeuce; }
    public Integer getCapPoint() { return capPoint; }
    public void setCapPoint(Integer capPoint) { this.capPoint = capPoint; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }
    public String getCreateTime() { return createTime; }
    public void setCreateTime(String createTime) { this.createTime = createTime; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
}
