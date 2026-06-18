package com.scoring.backend.domain.vo;

import java.util.List;

public class TournamentRefereeAccessVO {

    private Boolean granted;
    private List<TournamentRefereeVO> referees;

    public Boolean getGranted() {
        return granted;
    }

    public void setGranted(Boolean granted) {
        this.granted = granted;
    }

    public List<TournamentRefereeVO> getReferees() {
        return referees;
    }

    public void setReferees(List<TournamentRefereeVO> referees) {
        this.referees = referees;
    }
}
