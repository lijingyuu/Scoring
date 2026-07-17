package com.scoring.backend.domain.vo;

public class TeamMatchChildMatchVO {
    private String parentMatchId;
    private String childMatchId;
    private String itemCode;
    private String itemName;
    private String leftName;
    private String rightName;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Integer decidingPointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;

    public String getParentMatchId() { return parentMatchId; }
    public void setParentMatchId(String parentMatchId) { this.parentMatchId = parentMatchId; }
    public String getChildMatchId() { return childMatchId; }
    public void setChildMatchId(String childMatchId) { this.childMatchId = childMatchId; }
    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getLeftName() { return leftName; }
    public void setLeftName(String leftName) { this.leftName = leftName; }
    public String getRightName() { return rightName; }
    public void setRightName(String rightName) { this.rightName = rightName; }
    public Integer getBestOf() { return bestOf; }
    public void setBestOf(Integer bestOf) { this.bestOf = bestOf; }
    public Integer getGamesToWin() { return gamesToWin; }
    public void setGamesToWin(Integer gamesToWin) { this.gamesToWin = gamesToWin; }
    public Integer getPointsToWin() { return pointsToWin; }
    public void setPointsToWin(Integer pointsToWin) { this.pointsToWin = pointsToWin; }
    public Integer getDecidingPointsToWin() { return decidingPointsToWin; }
    public void setDecidingPointsToWin(Integer decidingPointsToWin) { this.decidingPointsToWin = decidingPointsToWin; }
    public Boolean getEnableDeuce() { return enableDeuce; }
    public void setEnableDeuce(Boolean enableDeuce) { this.enableDeuce = enableDeuce; }
    public Integer getCapPoint() { return capPoint; }
    public void setCapPoint(Integer capPoint) { this.capPoint = capPoint; }
}
