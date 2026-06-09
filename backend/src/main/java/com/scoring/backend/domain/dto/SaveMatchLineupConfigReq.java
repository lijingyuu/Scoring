package com.scoring.backend.domain.dto;

import java.util.List;

public class SaveMatchLineupConfigReq {

    private Integer gameNo;

    private String serveSide;

    private TeamLineupConfig left;

    private TeamLineupConfig right;

    public Integer getGameNo() { return gameNo; }
    public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
    public String getServeSide() { return serveSide; }
    public void setServeSide(String serveSide) { this.serveSide = serveSide; }
    public TeamLineupConfig getLeft() { return left; }
    public void setLeft(TeamLineupConfig left) { this.left = left; }
    public TeamLineupConfig getRight() { return right; }
    public void setRight(TeamLineupConfig right) { this.right = right; }

    public static class TeamLineupConfig {

        private List<String> court;

        private List<Integer> middlePairIndexes;

        private String libero1Id;

        private String libero2Id;

        public List<String> getCourt() { return court; }
        public void setCourt(List<String> court) { this.court = court; }
        public List<Integer> getMiddlePairIndexes() { return middlePairIndexes; }
        public void setMiddlePairIndexes(List<Integer> middlePairIndexes) { this.middlePairIndexes = middlePairIndexes; }
        public String getLibero1Id() { return libero1Id; }
        public void setLibero1Id(String libero1Id) { this.libero1Id = libero1Id; }
        public String getLibero2Id() { return libero2Id; }
        public void setLibero2Id(String libero2Id) { this.libero2Id = libero2Id; }
    }
}
