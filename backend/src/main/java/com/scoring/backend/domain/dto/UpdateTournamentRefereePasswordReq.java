package com.scoring.backend.domain.dto;

/**
 * 设置/更新裁判密码（双级口令模型）：
 * password = 现场执裁密码（SCORER），adminPassword = 赛务编排密码（ADMIN）。
 * 两者均可选但至少提供一个（服务层校验）；均须不少于 8 位数字，且两级密码明文不得相同。
 */
public class UpdateTournamentRefereePasswordReq {

    private String password;

    private String adminPassword;

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAdminPassword() {
        return adminPassword;
    }

    public void setAdminPassword(String adminPassword) {
        this.adminPassword = adminPassword;
    }
}
