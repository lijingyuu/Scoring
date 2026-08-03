package com.scoring.backend.domain.vo;

import java.util.List;

public class GroupStandingsVO {

    private String id;
    private Integer knockoutSlots;
    private Integer qualifiersPerGroup;
    private Boolean allGroupMatchesFinished;
    private Boolean hasUnresolvedTie;
    private List<GroupVO> groups;

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
    public List<GroupVO> getGroups() { return groups; }
    public void setGroups(List<GroupVO> groups) { this.groups = groups; }

    public static class GroupVO {

        private Integer groupNo;
        private List<StandingVO> standings;

        public Integer getGroupNo() { return groupNo; }
        public void setGroupNo(Integer groupNo) { this.groupNo = groupNo; }
        public List<StandingVO> getStandings() { return standings; }
        public void setStandings(List<StandingVO> standings) { this.standings = standings; }
    }

    public static class StandingVO {

        private String playerId;
        private String playerName;
        private Integer seedRank;
        private Integer rank;
        private String displayRankText;
        private Boolean qualified;
        private Boolean tieUnresolved;
        private Integer matchWins;
        private Integer matchLosses;
        private String matchWinRate;
        private Integer matchPoints;
        private Integer teamItemWins;
        private Integer teamItemLosses;
        private Integer teamItemNetWins;
        private String teamItemWinRate;
        private Integer gameWins;
        private Integer gameLosses;
        private Integer netGames;
        private Integer pointsFor;
        private Integer pointsAgainst;
        private Integer netPoints;
        private String gameWinRate;
        private String pointWinRate;

        public String getPlayerId() { return playerId; }
        public void setPlayerId(String playerId) { this.playerId = playerId; }
        public String getPlayerName() { return playerName; }
        public void setPlayerName(String playerName) { this.playerName = playerName; }
        public Integer getSeedRank() { return seedRank; }
        public void setSeedRank(Integer seedRank) { this.seedRank = seedRank; }
        public Integer getRank() { return rank; }
        public void setRank(Integer rank) { this.rank = rank; }
        public String getDisplayRankText() { return displayRankText; }
        public void setDisplayRankText(String displayRankText) { this.displayRankText = displayRankText; }
        public Boolean getQualified() { return qualified; }
        public void setQualified(Boolean qualified) { this.qualified = qualified; }
        public Boolean getTieUnresolved() { return tieUnresolved; }
        public void setTieUnresolved(Boolean tieUnresolved) { this.tieUnresolved = tieUnresolved; }
        public Integer getMatchWins() { return matchWins; }
        public void setMatchWins(Integer matchWins) { this.matchWins = matchWins; }
        public Integer getMatchLosses() { return matchLosses; }
        public void setMatchLosses(Integer matchLosses) { this.matchLosses = matchLosses; }
        public String getMatchWinRate() { return matchWinRate; }
        public void setMatchWinRate(String matchWinRate) { this.matchWinRate = matchWinRate; }
        public Integer getMatchPoints() { return matchPoints; }
        public void setMatchPoints(Integer matchPoints) { this.matchPoints = matchPoints; }
        public Integer getTeamItemWins() { return teamItemWins; }
        public void setTeamItemWins(Integer teamItemWins) { this.teamItemWins = teamItemWins; }
        public Integer getTeamItemLosses() { return teamItemLosses; }
        public void setTeamItemLosses(Integer teamItemLosses) { this.teamItemLosses = teamItemLosses; }
        public Integer getTeamItemNetWins() { return teamItemNetWins; }
        public void setTeamItemNetWins(Integer teamItemNetWins) { this.teamItemNetWins = teamItemNetWins; }
        public String getTeamItemWinRate() { return teamItemWinRate; }
        public void setTeamItemWinRate(String teamItemWinRate) { this.teamItemWinRate = teamItemWinRate; }
        public Integer getGameWins() { return gameWins; }
        public void setGameWins(Integer gameWins) { this.gameWins = gameWins; }
        public Integer getGameLosses() { return gameLosses; }
        public void setGameLosses(Integer gameLosses) { this.gameLosses = gameLosses; }
        public Integer getNetGames() { return netGames; }
        public void setNetGames(Integer netGames) { this.netGames = netGames; }
        public Integer getPointsFor() { return pointsFor; }
        public void setPointsFor(Integer pointsFor) { this.pointsFor = pointsFor; }
        public Integer getPointsAgainst() { return pointsAgainst; }
        public void setPointsAgainst(Integer pointsAgainst) { this.pointsAgainst = pointsAgainst; }
        public Integer getNetPoints() { return netPoints; }
        public void setNetPoints(Integer netPoints) { this.netPoints = netPoints; }
        public String getGameWinRate() { return gameWinRate; }
        public void setGameWinRate(String gameWinRate) { this.gameWinRate = gameWinRate; }
        public String getPointWinRate() { return pointWinRate; }
        public void setPointWinRate(String pointWinRate) { this.pointWinRate = pointWinRate; }
    }
}
