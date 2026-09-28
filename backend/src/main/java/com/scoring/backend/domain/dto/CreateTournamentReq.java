package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

public class CreateTournamentReq {

    @NotBlank(message = "赛事名称不能为空")
    @Size(max = 128, message = "赛事名称不能超过128字")
    private String name;

    @Size(max = 255, message = "比赛地点不能超过255字")
    private String location;

    private List<PlayerEntry> players;

    private Integer sportType;

    private Integer participantType;

    private Integer teamMatchTemplate;

    /** 仅 teamMatchTemplate=3（自定义多项）时有效，描述每个子项的类型 */
    @Size(max = 7, message = "自定义子项不能超过7项")
    private List<CustomItemSpec> customItems;

    private List<TeamEntry> teams;

    private Integer tournamentType;

    private Integer knockoutSlots;

    private Integer knockoutRounds;

    private Integer qualifiersPerGroup;

    private Integer roundRobinRounds;

    private String rankingTemplate;

    private List<String> rankingPriorities;

    private RuleConfig rule;

    private Boolean roundRuleEnabled;

    private List<RoundRuleConfig> roundRules;

    private Boolean thirdPlaceEnabled;

    private RuleConfig thirdPlaceRule;

    private List<DivisionSpec> divisions;

    private String refereePassword;

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

    public Integer getSportType() {
        return sportType;
    }

    public void setSportType(Integer sportType) {
        this.sportType = sportType;
    }

    public Integer getParticipantType() {
        return participantType;
    }

    public void setParticipantType(Integer participantType) {
        this.participantType = participantType;
    }

    public Integer getTeamMatchTemplate() {
        return teamMatchTemplate;
    }

    public void setTeamMatchTemplate(Integer teamMatchTemplate) {
        this.teamMatchTemplate = teamMatchTemplate;
    }

    public List<CustomItemSpec> getCustomItems() {
        return customItems;
    }

    public void setCustomItems(List<CustomItemSpec> customItems) {
        this.customItems = customItems;
    }

    public List<TeamEntry> getTeams() {
        return teams;
    }

    public void setTeams(List<TeamEntry> teams) {
        this.teams = teams;
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

    public Integer getKnockoutRounds() {
        return knockoutRounds;
    }

    public void setKnockoutRounds(Integer knockoutRounds) {
        this.knockoutRounds = knockoutRounds;
    }

    public Integer getQualifiersPerGroup() {
        return qualifiersPerGroup;
    }

    public void setQualifiersPerGroup(Integer qualifiersPerGroup) {
        this.qualifiersPerGroup = qualifiersPerGroup;
    }

    public Integer getRoundRobinRounds() { return roundRobinRounds; }
    public void setRoundRobinRounds(Integer roundRobinRounds) { this.roundRobinRounds = roundRobinRounds; }

    public String getRankingTemplate() {
        return rankingTemplate;
    }

    public void setRankingTemplate(String rankingTemplate) {
        this.rankingTemplate = rankingTemplate;
    }

    public List<String> getRankingPriorities() {
        return rankingPriorities;
    }

    public void setRankingPriorities(List<String> rankingPriorities) {
        this.rankingPriorities = rankingPriorities;
    }

    public RuleConfig getRule() {
        return rule;
    }

    public void setRule(RuleConfig rule) {
        this.rule = rule;
    }

    public Boolean getRoundRuleEnabled() {
        return roundRuleEnabled;
    }

    public void setRoundRuleEnabled(Boolean roundRuleEnabled) {
        this.roundRuleEnabled = roundRuleEnabled;
    }

    public List<RoundRuleConfig> getRoundRules() {
        return roundRules;
    }

    public void setRoundRules(List<RoundRuleConfig> roundRules) {
        this.roundRules = roundRules;
    }

    public Boolean getThirdPlaceEnabled() {
        return thirdPlaceEnabled;
    }

    public void setThirdPlaceEnabled(Boolean thirdPlaceEnabled) {
        this.thirdPlaceEnabled = thirdPlaceEnabled;
    }

    public RuleConfig getThirdPlaceRule() {
        return thirdPlaceRule;
    }

    public void setThirdPlaceRule(RuleConfig thirdPlaceRule) {
        this.thirdPlaceRule = thirdPlaceRule;
    }

    public List<DivisionSpec> getDivisions() {
        return divisions;
    }

    public void setDivisions(List<DivisionSpec> divisions) {
        this.divisions = divisions;
    }

    public String getRefereePassword() {
        return refereePassword;
    }

    public void setRefereePassword(String refereePassword) {
        this.refereePassword = refereePassword;
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

    public static class TeamEntry {

        private String name;

        private Integer seed;

        private List<TeamMemberEntry> members;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getSeed() { return seed; }
        public void setSeed(Integer seed) { this.seed = seed; }
        public List<TeamMemberEntry> getMembers() { return members; }
        public void setMembers(List<TeamMemberEntry> members) { this.members = members; }
    }

    public static class TeamMemberEntry {

        private String name;

        private Integer jerseyNumber;

        private Boolean libero;

        private Boolean captain;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getJerseyNumber() { return jerseyNumber; }
        public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }
        public Boolean getLibero() { return libero; }
        public void setLibero(Boolean libero) { this.libero = libero; }
        public Boolean getCaptain() { return captain; }
        public void setCaptain(Boolean captain) { this.captain = captain; }
    }

    public static class RuleConfig {

        private Integer bestOf;

        private Integer gamesToWin;

        private Integer pointsToWin;

        private Integer decidingPointsToWin;

        private Boolean enableDeuce;

        private Integer capPoint;

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
    }

    public static class RoundRuleConfig {
        private Integer stageType;
        private Integer roundNum;
        private RuleConfig rule;

        public Integer getStageType() { return stageType; }
        public void setStageType(Integer stageType) { this.stageType = stageType; }
        public Integer getRoundNum() { return roundNum; }
        public void setRoundNum(Integer roundNum) { this.roundNum = roundNum; }
        public RuleConfig getRule() { return rule; }
        public void setRule(RuleConfig rule) { this.rule = rule; }
    }

    /**
     * 组别创建参数：一个赛事可包含多个组别，每组别独立的名单 / 赛制 / 计分规则 / 分轮规则 / 季军赛 / 排名配置。
     * divisions 为空时走旧扁平 payload 兼容路径（归一化为单个匿名默认组别）。
     */
    public static class DivisionSpec {

        private String name;

        private List<PlayerEntry> players;

        private Integer tournamentType;

        private Integer knockoutSlots;

        private Integer knockoutRounds;

        private Integer qualifiersPerGroup;

        private Integer roundRobinRounds;

        private RuleConfig rule;

        private Boolean roundRuleEnabled;

        private List<RoundRuleConfig> roundRules;

        private Boolean thirdPlaceEnabled;

        private RuleConfig thirdPlaceRule;

        private String rankingTemplate;

        private List<String> rankingPriorities;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<PlayerEntry> getPlayers() { return players; }
        public void setPlayers(List<PlayerEntry> players) { this.players = players; }
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
        public RuleConfig getRule() { return rule; }
        public void setRule(RuleConfig rule) { this.rule = rule; }
        public Boolean getRoundRuleEnabled() { return roundRuleEnabled; }
        public void setRoundRuleEnabled(Boolean roundRuleEnabled) { this.roundRuleEnabled = roundRuleEnabled; }
        public List<RoundRuleConfig> getRoundRules() { return roundRules; }
        public void setRoundRules(List<RoundRuleConfig> roundRules) { this.roundRules = roundRules; }
        public Boolean getThirdPlaceEnabled() { return thirdPlaceEnabled; }
        public void setThirdPlaceEnabled(Boolean thirdPlaceEnabled) { this.thirdPlaceEnabled = thirdPlaceEnabled; }
        public RuleConfig getThirdPlaceRule() { return thirdPlaceRule; }
        public void setThirdPlaceRule(RuleConfig thirdPlaceRule) { this.thirdPlaceRule = thirdPlaceRule; }
        public String getRankingTemplate() { return rankingTemplate; }
        public void setRankingTemplate(String rankingTemplate) { this.rankingTemplate = rankingTemplate; }
        public List<String> getRankingPriorities() { return rankingPriorities; }
        public void setRankingPriorities(List<String> rankingPriorities) { this.rankingPriorities = rankingPriorities; }
    }

    /**
     * 自定义多项团体赛子项规格（teamMatchTemplate=3 时有效）。
     * displayOrder 从 1 开始，与最终写入 tournament_custom_item 的顺序对应。
     */
    public static class CustomItemSpec {

        /** 1-based 顺序，由前端按位置传入 */
        private Integer displayOrder;

        /**
         * 项目类型：S（单打不限性别）/ D（双打不限性别）/
         *           MS（男单）/ WS（女单）/ MD（男双）/ WD（女双）/ XD（混双）
         */
        private String itemType;

        public Integer getDisplayOrder() { return displayOrder; }
        public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
        public String getItemType() { return itemType; }
        public void setItemType(String itemType) { this.itemType = itemType; }
    }
}
