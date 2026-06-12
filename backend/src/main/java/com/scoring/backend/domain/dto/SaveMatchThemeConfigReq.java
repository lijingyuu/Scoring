package com.scoring.backend.domain.dto;

import java.util.Map;

public class SaveMatchThemeConfigReq {

    private Map<String, String> theme;

    public Map<String, String> getTheme() { return theme; }
    public void setTheme(Map<String, String> theme) { this.theme = theme; }
}
