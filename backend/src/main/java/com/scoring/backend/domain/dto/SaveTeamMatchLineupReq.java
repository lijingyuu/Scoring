package com.scoring.backend.domain.dto;

import java.util.List;

public class SaveTeamMatchLineupReq {
    private List<ItemLineup> items;

    public List<ItemLineup> getItems() { return items; }
    public void setItems(List<ItemLineup> items) { this.items = items; }

    public static class ItemLineup {
        private String itemCode;
        private List<String> leftMemberIds;
        private List<String> rightMemberIds;

        public String getItemCode() { return itemCode; }
        public void setItemCode(String itemCode) { this.itemCode = itemCode; }
        public List<String> getLeftMemberIds() { return leftMemberIds; }
        public void setLeftMemberIds(List<String> leftMemberIds) { this.leftMemberIds = leftMemberIds; }
        public List<String> getRightMemberIds() { return rightMemberIds; }
        public void setRightMemberIds(List<String> rightMemberIds) { this.rightMemberIds = rightMemberIds; }
    }
}
