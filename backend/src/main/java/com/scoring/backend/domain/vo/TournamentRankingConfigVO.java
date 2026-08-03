package com.scoring.backend.domain.vo;

import java.util.List;

public class TournamentRankingConfigVO {

    private String tournamentId;
    private Integer configVersion;
    private String template;
    private List<String> priorities;
    private Boolean pointsSystemEnabled;
    private String mathType;
    private Boolean twoWayTieH2HFirst;
    private String withdrawPolicy;
    private Boolean locked;
    private String lockedAt;
    private Boolean creator;

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getConfigVersion() { return configVersion; }
    public void setConfigVersion(Integer configVersion) { this.configVersion = configVersion; }
    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public List<String> getPriorities() { return priorities; }
    public void setPriorities(List<String> priorities) { this.priorities = priorities; }
    public Boolean getPointsSystemEnabled() { return pointsSystemEnabled; }
    public void setPointsSystemEnabled(Boolean pointsSystemEnabled) { this.pointsSystemEnabled = pointsSystemEnabled; }
    public String getMathType() { return mathType; }
    public void setMathType(String mathType) { this.mathType = mathType; }
    public Boolean getTwoWayTieH2HFirst() { return twoWayTieH2HFirst; }
    public void setTwoWayTieH2HFirst(Boolean twoWayTieH2HFirst) { this.twoWayTieH2HFirst = twoWayTieH2HFirst; }
    public String getWithdrawPolicy() { return withdrawPolicy; }
    public void setWithdrawPolicy(String withdrawPolicy) { this.withdrawPolicy = withdrawPolicy; }
    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean locked) { this.locked = locked; }
    public String getLockedAt() { return lockedAt; }
    public void setLockedAt(String lockedAt) { this.lockedAt = lockedAt; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
}
