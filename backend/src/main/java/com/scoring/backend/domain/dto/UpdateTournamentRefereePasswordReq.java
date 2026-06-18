package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.NotBlank;

public class UpdateTournamentRefereePasswordReq {

    @NotBlank(message = "裁判密码不能为空")
    private String password;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
