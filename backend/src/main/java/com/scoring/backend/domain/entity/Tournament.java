package com.scoring.backend.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("tournament")
public class Tournament {

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String name;

    private String location;

    private Integer status;

    @TableField("sport_type")
    private Integer sportType;

    @TableField("participant_type")
    private Integer participantType;

    @TableField("team_match_template")
    private Integer teamMatchTemplate;

    @TableField("tournament_type")
    private Integer tournamentType;

    @TableField("group_size")
    private Integer groupSize;

    @TableField("knockout_slots")
    private Integer knockoutSlots;

    @TableField("knockout_rounds")
    private Integer knockoutRounds;

    @TableField("qualifiers_per_group")
    private Integer qualifiersPerGroup;

    @TableField("current_stage")
    private Integer currentStage;

    @TableField("knockout_generated")
    private Boolean knockoutGenerated;

    @TableField("best_of")
    private Integer bestOf;

    @TableField("games_to_win")
    private Integer gamesToWin;

    @TableField("points_to_win")
    private Integer pointsToWin;

    @TableField("deciding_points_to_win")
    private Integer decidingPointsToWin;

    @TableField("enable_deuce")
    private Boolean enableDeuce;

    @TableField("cap_point")
    private Integer capPoint;

    @TableField("round_robin_rounds")
    private Integer roundRobinRounds;

    @TableField("round_rule_enabled")
    private Boolean roundRuleEnabled;

    @TableField("third_place_enabled")
    private Boolean thirdPlaceEnabled;

    @TableField("third_place_best_of")
    private Integer thirdPlaceBestOf;

    @TableField("third_place_games_to_win")
    private Integer thirdPlaceGamesToWin;

    @TableField("third_place_points_to_win")
    private Integer thirdPlacePointsToWin;

    @TableField("third_place_deciding_points_to_win")
    private Integer thirdPlaceDecidingPointsToWin;

    @TableField("third_place_enable_deuce")
    private Boolean thirdPlaceEnableDeuce;

    @TableField("third_place_cap_point")
    private Integer thirdPlaceCapPoint;

    @TableField("creator_user_id")
    private String creatorUserId;

    @TableField("favorite_count")
    private Integer favoriteCount;

    private Boolean archived;

    @TableField(exist = false)
    private Boolean favorite;

    @TableField(exist = false)
    private Boolean creator;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getSportType() { return sportType; }
    public void setSportType(Integer sportType) { this.sportType = sportType; }
    public Integer getParticipantType() { return participantType; }
    public void setParticipantType(Integer participantType) { this.participantType = participantType; }
    public Integer getTeamMatchTemplate() { return teamMatchTemplate; }
    public void setTeamMatchTemplate(Integer teamMatchTemplate) { this.teamMatchTemplate = teamMatchTemplate; }
    public Integer getTournamentType() { return tournamentType; }
    public void setTournamentType(Integer tournamentType) { this.tournamentType = tournamentType; }
    public Integer getGroupSize() { return groupSize; }
    public void setGroupSize(Integer groupSize) { this.groupSize = groupSize; }
    public Integer getKnockoutSlots() { return knockoutSlots; }
    public void setKnockoutSlots(Integer knockoutSlots) { this.knockoutSlots = knockoutSlots; }
    public Integer getKnockoutRounds() { return knockoutRounds; }
    public void setKnockoutRounds(Integer knockoutRounds) { this.knockoutRounds = knockoutRounds; }
    public Integer getQualifiersPerGroup() { return qualifiersPerGroup; }
    public void setQualifiersPerGroup(Integer qualifiersPerGroup) { this.qualifiersPerGroup = qualifiersPerGroup; }
    public Integer getCurrentStage() { return currentStage; }
    public void setCurrentStage(Integer currentStage) { this.currentStage = currentStage; }
    public Boolean getKnockoutGenerated() { return knockoutGenerated; }
    public void setKnockoutGenerated(Boolean knockoutGenerated) { this.knockoutGenerated = knockoutGenerated; }
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
    public Integer getRoundRobinRounds() { return roundRobinRounds; }
    public void setRoundRobinRounds(Integer roundRobinRounds) { this.roundRobinRounds = roundRobinRounds; }
    public Boolean getRoundRuleEnabled() { return roundRuleEnabled; }
    public void setRoundRuleEnabled(Boolean roundRuleEnabled) { this.roundRuleEnabled = roundRuleEnabled; }
    public Boolean getThirdPlaceEnabled() { return thirdPlaceEnabled; }
    public void setThirdPlaceEnabled(Boolean thirdPlaceEnabled) { this.thirdPlaceEnabled = thirdPlaceEnabled; }
    public Integer getThirdPlaceBestOf() { return thirdPlaceBestOf; }
    public void setThirdPlaceBestOf(Integer thirdPlaceBestOf) { this.thirdPlaceBestOf = thirdPlaceBestOf; }
    public Integer getThirdPlaceGamesToWin() { return thirdPlaceGamesToWin; }
    public void setThirdPlaceGamesToWin(Integer thirdPlaceGamesToWin) { this.thirdPlaceGamesToWin = thirdPlaceGamesToWin; }
    public Integer getThirdPlacePointsToWin() { return thirdPlacePointsToWin; }
    public void setThirdPlacePointsToWin(Integer thirdPlacePointsToWin) { this.thirdPlacePointsToWin = thirdPlacePointsToWin; }
    public Integer getThirdPlaceDecidingPointsToWin() { return thirdPlaceDecidingPointsToWin; }
    public void setThirdPlaceDecidingPointsToWin(Integer thirdPlaceDecidingPointsToWin) { this.thirdPlaceDecidingPointsToWin = thirdPlaceDecidingPointsToWin; }
    public Boolean getThirdPlaceEnableDeuce() { return thirdPlaceEnableDeuce; }
    public void setThirdPlaceEnableDeuce(Boolean thirdPlaceEnableDeuce) { this.thirdPlaceEnableDeuce = thirdPlaceEnableDeuce; }
    public Integer getThirdPlaceCapPoint() { return thirdPlaceCapPoint; }
    public void setThirdPlaceCapPoint(Integer thirdPlaceCapPoint) { this.thirdPlaceCapPoint = thirdPlaceCapPoint; }
    public String getCreatorUserId() { return creatorUserId; }
    public void setCreatorUserId(String creatorUserId) { this.creatorUserId = creatorUserId; }
    public Integer getFavoriteCount() { return favoriteCount; }
    public void setFavoriteCount(Integer favoriteCount) { this.favoriteCount = favoriteCount; }
    public Boolean getArchived() { return archived; }
    public void setArchived(Boolean archived) { this.archived = archived; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Boolean getCreator() { return creator; }
    public void setCreator(Boolean creator) { this.creator = creator; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
