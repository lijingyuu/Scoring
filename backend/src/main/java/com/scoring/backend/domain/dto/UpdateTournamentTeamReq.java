package com.scoring.backend.domain.dto;

import java.util.List;

/**
 * 创建者编辑队伍请求：修改队名、追加队员。
 * 不支持增删队伍与删除队员（有意收窄，见需求约定）。
 */
public class UpdateTournamentTeamReq {

    /** 新队名；null 表示不修改 */
    private String name;

    /** 追加的队员；null/空表示不追加 */
    private List<TeamMemberEntry> addMembers;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TeamMemberEntry> getAddMembers() { return addMembers; }
    public void setAddMembers(List<TeamMemberEntry> addMembers) { this.addMembers = addMembers; }

    public static class TeamMemberEntry {
        private String name;
        private Integer jerseyNumber;
        private Boolean libero;
        private Boolean captain;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getJerseyNumber() { return jerseyNumber; }
        public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }
        public Boolean getLibero() { return libero; }
        public void setLibero(Boolean libero) { this.libero = libero; }
        public Boolean getCaptain() { return captain; }
        public void setCaptain(Boolean captain) { this.captain = captain; }
    }
}
