package com.scoring.backend.domain.vo;

import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRoundRule;
 import com.scoring.backend.domain.entity.TournamentDivision;

public class MatchRuleConfig {
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    /** V32：三局两胜打满三局开关；终场条件从先到 gamesToWin 胜局改为打满 bestOf 局按胜局定胜负 */
    private Boolean forceFullGames;

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
        rule.setForceFullGames(Boolean.TRUE.equals(tournament.getForceFullGames()));
        return rule;
    }
 
     public static MatchRuleConfig fromDivision(TournamentDivision division) {
         MatchRuleConfig rule = new MatchRuleConfig();
         if (division == null) {
             rule.setBestOf(3);
             rule.setGamesToWin(2);
             rule.setPointsToWin(21);
             rule.setEnableDeuce(true);
             rule.setCapPoint(30);
             return rule;
         }
         rule.setBestOf(division.getBestOf());
         rule.setGamesToWin(division.getGamesToWin());
         rule.setPointsToWin(division.getPointsToWin());
         rule.setDecidingPointsToWin(division.getDecidingPointsToWin());
         rule.setEnableDeuce(division.getEnableDeuce());
         rule.setCapPoint(division.getCapPoint());
         rule.setForceFullGames(Boolean.TRUE.equals(division.getForceFullGames()));
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
        rule.setForceFullGames(Boolean.TRUE.equals(roundRule.getForceFullGames()));
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
        // 季军赛是淘汰赛，创建端不提供打满三局选项，恒 false 保持既有终场语义
        rule.setForceFullGames(false);
        return rule;
    }
 
     public static MatchRuleConfig fromThirdPlace(TournamentDivision division) {
         MatchRuleConfig rule = new MatchRuleConfig();
         if (division == null) {
             return fromDivision(null);
         }
         rule.setBestOf(division.getThirdPlaceBestOf() == null ? division.getBestOf() : division.getThirdPlaceBestOf());
         rule.setGamesToWin(division.getThirdPlaceGamesToWin() == null ? division.getGamesToWin() : division.getThirdPlaceGamesToWin());
         rule.setPointsToWin(division.getThirdPlacePointsToWin() == null ? division.getPointsToWin() : division.getThirdPlacePointsToWin());
         rule.setDecidingPointsToWin(division.getThirdPlaceDecidingPointsToWin() == null ? division.getDecidingPointsToWin() : division.getThirdPlaceDecidingPointsToWin());
         rule.setEnableDeuce(division.getThirdPlaceEnableDeuce() == null ? division.getEnableDeuce() : division.getThirdPlaceEnableDeuce());
         rule.setCapPoint(division.getThirdPlaceCapPoint() == null ? division.getCapPoint() : division.getThirdPlaceCapPoint());
         // 季军赛是淘汰赛，创建端不提供打满三局选项，恒 false 保持既有终场语义
         rule.setForceFullGames(false);
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
    public Boolean getForceFullGames() { return forceFullGames; }
    public void setForceFullGames(Boolean forceFullGames) { this.forceFullGames = forceFullGames; }
}
