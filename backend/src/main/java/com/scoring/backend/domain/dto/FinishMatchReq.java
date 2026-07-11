package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class FinishMatchReq {

    @NotBlank(message = "winnerSide不能为空")
    private String winnerSide;

    @NotNull(message = "leftScore不能为空")
    private Integer leftScore;

    @NotNull(message = "rightScore不能为空")
    private Integer rightScore;

    private String retiredSide;

    private Integer leftGameWins;

    private Integer rightGameWins;

    private List<GameScore> gameScores;

    private List<GameScore> relaySegmentScores;

    public String getWinnerSide() { return winnerSide; }
    public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    public Integer getLeftScore() { return leftScore; }
    public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
    public Integer getRightScore() { return rightScore; }
    public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
    public String getRetiredSide() { return retiredSide; }
    public void setRetiredSide(String retiredSide) { this.retiredSide = retiredSide; }
    public Integer getLeftGameWins() { return leftGameWins; }
    public void setLeftGameWins(Integer leftGameWins) { this.leftGameWins = leftGameWins; }
    public Integer getRightGameWins() { return rightGameWins; }
    public void setRightGameWins(Integer rightGameWins) { this.rightGameWins = rightGameWins; }
    public List<GameScore> getGameScores() { return gameScores; }
    public void setGameScores(List<GameScore> gameScores) { this.gameScores = gameScores; }
    public List<GameScore> getRelaySegmentScores() { return relaySegmentScores; }
    public void setRelaySegmentScores(List<GameScore> relaySegmentScores) { this.relaySegmentScores = relaySegmentScores; }

    public static class GameScore {

        private Integer gameNo;

        private Integer leftScore;

        private Integer rightScore;

        private String winnerSide;

        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public Integer getLeftScore() { return leftScore; }
        public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
        public Integer getRightScore() { return rightScore; }
        public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
        public String getWinnerSide() { return winnerSide; }
        public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    }
}
