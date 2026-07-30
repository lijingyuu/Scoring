package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRoundRule;

public class MatchRuleConfig {
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;

    public static MatchRuleConfig fromTournament(Tournament tournament) {
        MatchRuleConfig rule = new MatchRuleConfig();
        if (tournament == null) {
            rule.setBestOf(3);
            rule.setGamesToWin(2);
            rule.setPointsToWin(21);
            rule.setEnableDeuce(true);
            rule.setCapPoint(30);
            return rule;
        }
        rule.setBestOf(tournament.getBestOf());
        rule.setGamesToWin(tournament.getGamesToWin());
        rule.setPointsToWin(tournament.getPointsToWin());
        rule.setDecidingPointsToWin(tournament.getDecidingPointsToWin());
        rule.setEnableDeuce(tournament.getEnableDeuce());
        rule.setCapPoint(tournament.getCapPoint());
        return rule;
    }

    public static MatchRuleConfig fromRoundRule(TournamentRoundRule roundRule) {
        MatchRuleConfig rule = new MatchRuleConfig();
        rule.setBestOf(roundRule.getBestOf());
        rule.setGamesToWin(roundRule.getGamesToWin());
        rule.setPointsToWin(roundRule.getPointsToWin());
        rule.setDecidingPointsToWin(roundRule.getDecidingPointsToWin());
        rule.setEnableDeuce(roundRule.getEnableDeuce());
        rule.setCapPoint(roundRule.getCapPoint());
        return rule;
    }

    public static MatchRuleConfig fromThirdPlace(Tournament tournament) {
        MatchRuleConfig rule = new MatchRuleConfig();
        if (tournament == null) {
            return fromTournament(null);
        }
        rule.setBestOf(tournament.getThirdPlaceBestOf() == null ? tournament.getBestOf() : tournament.getThirdPlaceBestOf());
        rule.setGamesToWin(tournament.getThirdPlaceGamesToWin() == null ? tournament.getGamesToWin() : tournament.getThirdPlaceGamesToWin());
        rule.setPointsToWin(tournament.getThirdPlacePointsToWin() == null ? tournament.getPointsToWin() : tournament.getThirdPlacePointsToWin());
        rule.setDecidingPointsToWin(tournament.getThirdPlaceDecidingPointsToWin() == null ? tournament.getDecidingPointsToWin() : tournament.getThirdPlaceDecidingPointsToWin());
        rule.setEnableDeuce(tournament.getThirdPlaceEnableDeuce() == null ? tournament.getEnableDeuce() : tournament.getThirdPlaceEnableDeuce());
        rule.setCapPoint(tournament.getThirdPlaceCapPoint() == null ? tournament.getCapPoint() : tournament.getThirdPlaceCapPoint());
        return rule;
    }

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
