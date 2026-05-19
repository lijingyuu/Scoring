package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public class FinishMatchReq {

    @NotBlank(message = "winnerSide不能为空")
    private String winnerSide;

    @NotNull(message = "leftScore不能为空")
    private Integer leftScore;

    @NotNull(message = "rightScore不能为空")
    private Integer rightScore;

    public String getWinnerSide() { return winnerSide; }
    public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    public Integer getLeftScore() { return leftScore; }
    public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
    public Integer getRightScore() { return rightScore; }
    public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
}
