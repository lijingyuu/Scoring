package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public class CreateTournamentReq {

    @NotBlank(message = "赛事名称不能为空")
    private String name;

    private String location;

    @NotEmpty(message = "选手列表不能为空")
    private List<PlayerEntry> players;

    private Integer tournamentType;

    private Integer knockoutSlots;

    private Integer qualifiersPerGroup;

    private RuleConfig rule;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public List<PlayerEntry> getPlayers() {
        return players;
    }

    public void setPlayers(List<PlayerEntry> players) {
        this.players = players;
    }

    public Integer getTournamentType() {
        return tournamentType;
    }

    public void setTournamentType(Integer tournamentType) {
        this.tournamentType = tournamentType;
    }

    public Integer getKnockoutSlots() {
        return knockoutSlots;
    }

    public void setKnockoutSlots(Integer knockoutSlots) {
        this.knockoutSlots = knockoutSlots;
    }

    public Integer getQualifiersPerGroup() {
        return qualifiersPerGroup;
    }

    public void setQualifiersPerGroup(Integer qualifiersPerGroup) {
        this.qualifiersPerGroup = qualifiersPerGroup;
    }

    public RuleConfig getRule() {
        return rule;
    }

    public void setRule(RuleConfig rule) {
        this.rule = rule;
    }

    public static class PlayerEntry {

        private String name;

        private Integer seed;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Integer getSeed() {
            return seed;
        }

        public void setSeed(Integer seed) {
            this.seed = seed;
        }
    }

    public static class RuleConfig {

        private Integer bestOf;

        private Integer gamesToWin;

        private Integer pointsToWin;

        private Boolean enableDeuce;

        private Integer capPoint;

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
    }
}
