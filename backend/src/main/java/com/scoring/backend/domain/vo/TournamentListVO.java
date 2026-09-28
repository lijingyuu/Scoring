package com.scoring.backend.domain.vo;

import java.time.LocalDateTime;

/**
 * 赛事列表条目（大厅搜索 / 我的收藏 / 我创建 / 已归档 四个列表接口共用）。
 *
 * 此前列表直出 Tournament 实体：多组别赛事的赛制等下沉列固化的是第 1 组别的值，
 * 前端会把它当成本赛事的赛制摘要展示。VO 在覆盖全部列表在用字段的基础上补
 * divisionCount，供前端在多组别时显示"N 个组别"而不是第 1 组别的赛制；
 * 同时把列表契约与主表列解耦（新增列不再自动泄漏到列表响应）。
 */
public class TournamentListVO {

    private String id;
    private String name;
    private String location;
    private Integer status;
    private Integer sportType;
    private Integer participantType;
    private Integer teamMatchTemplate;
    /** 下沉列，取第 1 组别占位值；多组别时前端应优先展示 divisionCount */
    private Integer tournamentType;
    private Integer knockoutSlots;
    private Integer roundRobinRounds;
    private Integer bestOf;
    private Integer pointsToWin;
    private Integer favoriteCount;
    private Boolean favorite;
    private Boolean creator;
    private LocalDateTime createTime;
    private Integer divisionCount;

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
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getRoundRobinRounds() { return roundRobinRounds; }
    public void setRoundRobinRounds(Integer roundRobinRounds) { this.roundRobinRounds = roundRobinRounds; }
    public Integer getBestOf() { return bestOf; }
    public void setBestOf(Integer bestOf) { this.bestOf = bestOf; }
    public Integer getPointsToWin() { return pointsToWin; }
    public void setPointsToWin(Integer pointsToWin) { this.pointsToWin = pointsToWin; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public Integer getDivisionCount() { return divisionCount; }
    public void setDivisionCount(Integer divisionCount) { this.divisionCount = divisionCount; }
}
