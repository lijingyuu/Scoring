package com.scoring.backend.domain.dto;

import java.util.List;

public class UpdateTournamentRankingConfigReq {

    private String template;
    private List<String> priorities;
    /** 人工纠错逃生通道：默认缺省即 false，只有显式传 true 才允许在已有完赛小组赛时改排名规则 */
    private Boolean force;

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public List<String> getPriorities() { return priorities; }
    public void setPriorities(List<String> priorities) { this.priorities = priorities; }
    public Boolean getForce() { return force; }
    public void setForce(Boolean force) { this.force = force; }
}
