package com.scoring.backend.domain.vo;

import java.util.List;

public class TournamentTeamsVO {

    private String tournamentId;
    private Integer sportType;
    private Integer participantType;
    private List<TeamVO> teams;

    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public Integer getSportType() { return sportType; }
    public void setSportType(Integer sportType) { this.sportType = sportType; }
    public Integer getParticipantType() { return participantType; }
    public void setParticipantType(Integer participantType) { this.participantType = participantType; }
    public List<TeamVO> getTeams() { return teams; }
    public void setTeams(List<TeamVO> teams) { this.teams = teams; }

    public static class TeamVO {
        private String id;
        private String name;
        private Integer memberCount;
        private String captainName;
        private List<MemberVO> members;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getMemberCount() { return memberCount; }
        public void setMemberCount(Integer memberCount) { this.memberCount = memberCount; }
        public String getCaptainName() { return captainName; }
        public void setCaptainName(String captainName) { this.captainName = captainName; }
        public List<MemberVO> getMembers() { return members; }
        public void setMembers(List<MemberVO> members) { this.members = members; }
    }

    public static class MemberVO {
        private String id;
        private String name;
        private Integer jerseyNumber;
        private Boolean captain;
        private Boolean libero;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public Integer getJerseyNumber() { return jerseyNumber; }
        public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }
        public Boolean getCaptain() { return captain; }
        public void setCaptain(Boolean captain) { this.captain = captain; }
        public Boolean getLibero() { return libero; }
        public void setLibero(Boolean libero) { this.libero = libero; }
    }
}
