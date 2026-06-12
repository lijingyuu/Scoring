package com.scoring.backend.domain.vo;

import java.util.Map;

public class MatchThemeConfigVO {

    private String matchId;

    private Map<String, String> theme;
    private Map<String, String> phoneTheme;
    private Map<String, String> padTheme;

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public Map<String, String> getTheme() { return theme; }
    public void setTheme(Map<String, String> theme) { this.theme = theme; }
    public Map<String, String> getPhoneTheme() { return phoneTheme; }
    public void setPhoneTheme(Map<String, String> phoneTheme) { this.phoneTheme = phoneTheme; }
    public Map<String, String> getPadTheme() { return padTheme; }
    public void setPadTheme(Map<String, String> padTheme) { this.padTheme = padTheme; }
}
