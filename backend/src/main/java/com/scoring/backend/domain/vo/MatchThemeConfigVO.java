package com.scoring.backend.domain.vo;

import java.util.Map;

public class MatchThemeConfigVO {

    private String matchId;

    private Map<String, String> theme;

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public Map<String, String> getTheme() { return theme; }
    public void setTheme(Map<String, String> theme) { this.theme = theme; }
}
