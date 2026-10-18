package com.scoring.backend.domain.vo;

import java.util.List;

public class TournamentRefereeAccessVO {

    private Boolean granted;
    /** 本次认证落定的授权角色：ADMIN(赛务编排管理员) / SCORER(现场执裁裁判)；已是 ADMIN 不因误输执裁密码降级 */
    private String grantRole;
    private List<TournamentRefereeVO> referees;

    public Boolean getGranted() {
        return granted;
    }

    public void setGranted(Boolean granted) {
        this.granted = granted;
    }

    public String getGrantRole() {
        return grantRole;
    }

    public void setGrantRole(String grantRole) {
        this.grantRole = grantRole;
    }

    public List<TournamentRefereeVO> getReferees() {
        return referees;
    }

    public void setReferees(List<TournamentRefereeVO> referees) {
        this.referees = referees;
    }
}
