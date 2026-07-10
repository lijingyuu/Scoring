package com.scoring.backend.domain.vo;

public class TeamMatchItemVO {
    private Integer displayOrder;
    private String code;
    private String name;
    private Integer playerCount;

    public TeamMatchItemVO() {
    }

    public TeamMatchItemVO(Integer displayOrder, String code, String name, Integer playerCount) {
        this.displayOrder = displayOrder;
        this.code = code;
        this.name = name;
        this.playerCount = playerCount;
    }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }
}
