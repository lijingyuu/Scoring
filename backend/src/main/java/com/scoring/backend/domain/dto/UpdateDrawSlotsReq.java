package com.scoring.backend.domain.dto;

import java.util.List;

/**
 * 手写签表开赛前调整：全量提交签位顺序。
 * 元素 = player.id，null = 轮空（空签位）；长度 = 框架容量（≥名册数的最小 2 的幂）。
 */
public class UpdateDrawSlotsReq {

    private List<String> knockoutSlotOrder;

    public List<String> getKnockoutSlotOrder() {
        return knockoutSlotOrder;
    }

    public void setKnockoutSlotOrder(List<String> knockoutSlotOrder) {
        this.knockoutSlotOrder = knockoutSlotOrder;
    }
}
