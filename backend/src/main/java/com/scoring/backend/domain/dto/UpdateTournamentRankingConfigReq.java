package com.scoring.backend.domain.dto;

import java.util.List;

public class UpdateTournamentRankingConfigReq {

    private String template;
    private List<String> priorities;

    public String getTemplate() { return template; }
    public void setTemplate(String template) { this.template = template; }
    public List<String> getPriorities() { return priorities; }
    public void setPriorities(List<String> priorities) { this.priorities = priorities; }
}
