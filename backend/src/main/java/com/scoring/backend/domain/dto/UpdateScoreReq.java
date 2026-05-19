package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateScoreReq {

    private String scoreDisplay;

    @NotBlank(message = "winnerId不能为空")
    private String winnerId;

    public String getScoreDisplay() {
        return scoreDisplay;
    }

    public void setScoreDisplay(String scoreDisplay) {
        this.scoreDisplay = scoreDisplay;
    }

    public String getWinnerId() {
        return winnerId;
    }

    public void setWinnerId(String winnerId) {
        this.winnerId = winnerId;
    }
}
