package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;

import java.util.List;

public class TournamentGroupsVO {

    private String id;
    private String name;
    private String location;
    private Integer status;
    private Integer sportType;
    private Integer tournamentType;
    private Integer groupSize;
    private Integer knockoutSlots;
    private Integer qualifiersPerGroup;
    private Integer currentStage;
    private Boolean knockoutGenerated;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private List<GroupVO> groups;

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
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getGroupSize() { return groupSize; }
    public void setGroupSize(Integer groupSize) { this.groupSize = groupSize; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
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
    public Boolean getEnableDeuce() { return enableDeuce; }
    public void setEnableDeuce(Boolean enableDeuce) { this.enableDeuce = enableDeuce; }
    public Integer getCapPoint() { return capPoint; }
    public void setCapPoint(Integer capPoint) { this.capPoint = capPoint; }
    public List<GroupVO> getGroups() { return groups; }
    public void setGroups(List<GroupVO> groups) { this.groups = groups; }

    public static class GroupVO {

        private Integer groupNo;
        private List<Player> players;
        private List<MatchRecord> matches;

        public Integer getGroupNo() { return groupNo; }
        public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }
        public List<Player> getPlayers() { return players; }
        public void setPlayers(List<Player> players) { this.players = players; }
        public List<MatchRecord> getMatches() { return matches; }
        public void setMatches(List<MatchRecord> matches) { this.matches = matches; }
    }
}
