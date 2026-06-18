package com.scoring.backend.domain.vo;

public interface TournamentMatchAccessVO {

    Boolean getRefereeGranted();

    void setRefereeGranted(Boolean refereeGranted);

    Boolean getCanOperateMatches();

    void setCanOperateMatches(Boolean canOperateMatches);

    Boolean getCanManageReferees();

    void setCanManageReferees(Boolean canManageReferees);
}
