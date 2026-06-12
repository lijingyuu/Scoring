package com.scoring.backend.domain.dto;

import java.util.Map;

public class SaveMatchThemeConfigReq {

    private String device;

    private Map<String, String> theme;

    public String getDevice() { return device; }
    public void setDevice(String device) { this.device = device; }
    public Map<String, String> getTheme() { return theme; }
    public void setTheme(Map<String, String> theme) { this.theme = theme; }
}
