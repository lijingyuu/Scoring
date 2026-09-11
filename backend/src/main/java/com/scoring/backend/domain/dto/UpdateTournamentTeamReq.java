package com.scoring.backend.domain.dto;

import java.util.List;

/**
 * 创建者编辑队伍请求：修改队名、追加队员、修改已有队员姓名/号码。
 * 不支持增删队伍与删除队员（有意收窄，见需求约定）。
 */
public class UpdateTournamentTeamReq {

    /** 新队名；null 表示不修改 */
    private String name;

    /** 追加的队员；null/空表示不追加 */
    private List<TeamMemberEntry> addMembers;

    /** 修改已有队员的姓名/号码；null/空表示不修改 */
    private List<MemberUpdateEntry> updateMembers;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public List<TeamMemberEntry> getAddMembers() { return addMembers; }
    public void setAddMembers(List<TeamMemberEntry> addMembers) { this.addMembers = addMembers; }
    public List<MemberUpdateEntry> getUpdateMembers() { return updateMembers; }
    public void setUpdateMembers(List<MemberUpdateEntry> updateMembers) { this.updateMembers = updateMembers; }

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

    public static class MemberUpdateEntry {
        /** 要修改的队员 id，必填 */
        private String memberId;
        /** 新姓名；null 表示不修改 */
        private String name;
        /** 新球衣号码（仅排球）；null 表示不修改 */
        private Integer jerseyNumber;

        public String getMemberId() { return memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getJerseyNumber() { return jerseyNumber; }
        public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }
    }
}
