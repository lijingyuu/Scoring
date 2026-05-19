package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("player")
public class Player {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    @TableField("tournament_id")
    private String tournamentId;

    private String name;

    @TableField("seed_rank")
    private Integer seedRank;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getSeedRank() { return seedRank; }
    public void setSeedRank(Integer seedRank) { this.seedRank = seedRank; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
