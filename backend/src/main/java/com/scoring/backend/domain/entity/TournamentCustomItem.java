package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 自定义多项团体赛子项配置（team_match_template=3 时有效）。
 * 每条记录代表一个团体赛大局中的第 N 项子比赛配置，与 tournament 表 1:N 关联。
 */
@TableName("tournament_custom_item")
public class TournamentCustomItem {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tournament_id")
    private String tournamentId;

    /** 1-based 显示顺序 */
    @TableField("display_order")
    private Integer displayOrder;

    /**
     * 自动生成的编码，格式为 {@code itemType_displayOrder}，如 {@code MS_1}、{@code S_2}。
     * 保证同一 tournament 内唯一。
     */
    @TableField("item_code")
    private String itemCode;

    /** 原始类型：S / D / MS / WS / MD / WD / XD */
    @TableField("item_type")
    private String itemType;

    /** 前端展示名，如"男单"、"男单1" */
    @TableField("item_name")
    private String itemName;

    /** 1=单打，2=双打 */
    @TableField("player_count")
    private Integer playerCount;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }

    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }

    public String getItemCode() { return itemCode; }
    public void setItemCode(String itemCode) { this.itemCode = itemCode; }

    public String getItemType() { return itemType; }
    public void setItemType(String itemType) { this.itemType = itemType; }

    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }

    public Integer getPlayerCount() { return playerCount; }
    public void setPlayerCount(Integer playerCount) { this.playerCount = playerCount; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
