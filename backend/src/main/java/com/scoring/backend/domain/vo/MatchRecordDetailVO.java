package com.scoring.backend.domain.vo;

import java.util.List;

public class MatchRecordDetailVO {

    private String matchId;
    private String tournamentId;
    private String tournamentName;
    private String location;
    private Integer roundNum;
    private Integer matchIndex;
    private Integer status;
    private Integer bestOf;
    private Integer gamesToWin;
    private Integer pointsToWin;
    private Boolean enableDeuce;
    private Integer capPoint;
    private String scoreDisplay;
    private Integer leftGameWins;
    private Integer rightGameWins;
    private String winnerSide;
    private String retiredSide;
    private ParticipantRecord left;
    private ParticipantRecord right;
    private List<GameScoreRecord> gameScores;
    private RosterSnapshot rosterSnapshot;
    private List<LineupSnapshotRecord> lineupSnapshots;
    private List<EventRecord> events;
    private ReportMetaRecord reportMeta;
    private ReportRenderRecord reportRender;

    public String getMatchId() { return matchId; }
    public void setMatchId(String matchId) { this.matchId = matchId; }
    public String getTournamentId() { return tournamentId; }
    public void setTournamentId(String tournamentId) { this.tournamentId = tournamentId; }
    public String getTournamentName() { return tournamentName; }
    public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public Integer getRoundNum() { return roundNum; }
    public void setRoundNum(Integer roundNum) { this.roundNum = roundNum; }
    public Integer getMatchIndex() { return matchIndex; }
    public void setMatchIndex(Integer matchIndex) { this.matchIndex = matchIndex; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getBestOf() { return bestOf; }
    public void setBestOf(Integer bestOf) { this.bestOf = bestOf; }
    public Integer getGamesToWin() { return gamesToWin; }
    public void setGamesToWin(Integer gamesToWin) { this.gamesToWin = gamesToWin; }
    public Integer getPointsToWin() { return pointsToWin; }
    public void setPointsToWin(Integer pointsToWin) { this.pointsToWin = pointsToWin; }
    public Boolean getEnableDeuce() { return enableDeuce; }
    public void setEnableDeuce(Boolean enableDeuce) { this.enableDeuce = enableDeuce; }
    public Integer getCapPoint() { return capPoint; }
    public void setCapPoint(Integer capPoint) { this.capPoint = capPoint; }
    public String getScoreDisplay() { return scoreDisplay; }
    public void setScoreDisplay(String scoreDisplay) { this.scoreDisplay = scoreDisplay; }
    public Integer getLeftGameWins() { return leftGameWins; }
    public void setLeftGameWins(Integer leftGameWins) { this.leftGameWins = leftGameWins; }
    public Integer getRightGameWins() { return rightGameWins; }
    public void setRightGameWins(Integer rightGameWins) { this.rightGameWins = rightGameWins; }
    public String getWinnerSide() { return winnerSide; }
    public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    public String getRetiredSide() { return retiredSide; }
    public void setRetiredSide(String retiredSide) { this.retiredSide = retiredSide; }
    public ParticipantRecord getLeft() { return left; }
    public void setLeft(ParticipantRecord left) { this.left = left; }
    public ParticipantRecord getRight() { return right; }
    public void setRight(ParticipantRecord right) { this.right = right; }
    public List<GameScoreRecord> getGameScores() { return gameScores; }
    public void setGameScores(List<GameScoreRecord> gameScores) { this.gameScores = gameScores; }
    public RosterSnapshot getRosterSnapshot() { return rosterSnapshot; }
    public void setRosterSnapshot(RosterSnapshot rosterSnapshot) { this.rosterSnapshot = rosterSnapshot; }
    public List<LineupSnapshotRecord> getLineupSnapshots() { return lineupSnapshots; }
    public void setLineupSnapshots(List<LineupSnapshotRecord> lineupSnapshots) { this.lineupSnapshots = lineupSnapshots; }
    public List<EventRecord> getEvents() { return events; }
    public void setEvents(List<EventRecord> events) { this.events = events; }
    public ReportMetaRecord getReportMeta() { return reportMeta; }
    public void setReportMeta(ReportMetaRecord reportMeta) { this.reportMeta = reportMeta; }
    public ReportRenderRecord getReportRender() { return reportRender; }
    public void setReportRender(ReportRenderRecord reportRender) { this.reportRender = reportRender; }

    public static class ParticipantRecord {
        private String id;
        private String name;
        private List<MemberRecord> members;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public List<MemberRecord> getMembers() { return members; }
        public void setMembers(List<MemberRecord> members) { this.members = members; }
    }

    public static class MemberRecord {
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

    public static class GameScoreRecord {
        private Integer gameNo;
        private Integer leftScore;
        private Integer rightScore;
        private String winnerSide;

        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public Integer getLeftScore() { return leftScore; }
        public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
        public Integer getRightScore() { return rightScore; }
        public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
        public String getWinnerSide() { return winnerSide; }
        public void setWinnerSide(String winnerSide) { this.winnerSide = winnerSide; }
    }

    public static class RosterSnapshot {
        private List<MemberRecord> leftMembers;
        private List<MemberRecord> rightMembers;

        public List<MemberRecord> getLeftMembers() { return leftMembers; }
        public void setLeftMembers(List<MemberRecord> leftMembers) { this.leftMembers = leftMembers; }
        public List<MemberRecord> getRightMembers() { return rightMembers; }
        public void setRightMembers(List<MemberRecord> rightMembers) { this.rightMembers = rightMembers; }
    }

    public static class LineupSnapshotRecord {
        private Integer gameNo;
        private String serveSide;
        private TeamLineupRecord left;
        private TeamLineupRecord right;

        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public String getServeSide() { return serveSide; }
        public void setServeSide(String serveSide) { this.serveSide = serveSide; }
        public TeamLineupRecord getLeft() { return left; }
        public void setLeft(TeamLineupRecord left) { this.left = left; }
        public TeamLineupRecord getRight() { return right; }
        public void setRight(TeamLineupRecord right) { this.right = right; }
    }

    public static class TeamLineupRecord {
        private List<CourtSlotRecord> court;
        private List<Integer> middlePairIndexes;
        private String libero1Id;
        private String libero1Name;
        private String libero2Id;
        private String libero2Name;

        public List<CourtSlotRecord> getCourt() { return court; }
        public void setCourt(List<CourtSlotRecord> court) { this.court = court; }
        public List<Integer> getMiddlePairIndexes() { return middlePairIndexes; }
        public void setMiddlePairIndexes(List<Integer> middlePairIndexes) { this.middlePairIndexes = middlePairIndexes; }
        public String getLibero1Id() { return libero1Id; }
        public void setLibero1Id(String libero1Id) { this.libero1Id = libero1Id; }
        public String getLibero1Name() { return libero1Name; }
        public void setLibero1Name(String libero1Name) { this.libero1Name = libero1Name; }
        public String getLibero2Id() { return libero2Id; }
        public void setLibero2Id(String libero2Id) { this.libero2Id = libero2Id; }
        public String getLibero2Name() { return libero2Name; }
        public void setLibero2Name(String libero2Name) { this.libero2Name = libero2Name; }
    }

    public static class CourtSlotRecord {
        private Integer slotIndex;
        private String positionLabel;
        private String memberId;
        private String memberName;
        private Integer jerseyNumber;

        public Integer getSlotIndex() { return slotIndex; }
        public void setSlotIndex(Integer slotIndex) { this.slotIndex = slotIndex; }
        public String getPositionLabel() { return positionLabel; }
        public void setPositionLabel(String positionLabel) { this.positionLabel = positionLabel; }
        public String getMemberId() { return memberId; }
        public void setMemberId(String memberId) { this.memberId = memberId; }
        public String getMemberName() { return memberName; }
        public void setMemberName(String memberName) { this.memberName = memberName; }
        public Integer getJerseyNumber() { return jerseyNumber; }
        public void setJerseyNumber(Integer jerseyNumber) { this.jerseyNumber = jerseyNumber; }
    }

    public static class EventRecord {
        private Integer eventSeq;
        private String eventType;
        private String eventTypeLabel;
        private Integer gameNo;
        private Integer leftScore;
        private Integer rightScore;
        private String serveSide;
        private String summary;
        private List<String> detailLines;
        private String createTime;

        public Integer getEventSeq() { return eventSeq; }
        public void setEventSeq(Integer eventSeq) { this.eventSeq = eventSeq; }
        public String getEventType() { return eventType; }
        public void setEventType(String eventType) { this.eventType = eventType; }
        public String getEventTypeLabel() { return eventTypeLabel; }
        public void setEventTypeLabel(String eventTypeLabel) { this.eventTypeLabel = eventTypeLabel; }
        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public Integer getLeftScore() { return leftScore; }
        public void setLeftScore(Integer leftScore) { this.leftScore = leftScore; }
        public Integer getRightScore() { return rightScore; }
        public void setRightScore(Integer rightScore) { this.rightScore = rightScore; }
        public String getServeSide() { return serveSide; }
        public void setServeSide(String serveSide) { this.serveSide = serveSide; }
        public String getSummary() { return summary; }
        public void setSummary(String summary) { this.summary = summary; }
        public List<String> getDetailLines() { return detailLines; }
        public void setDetailLines(List<String> detailLines) { this.detailLines = detailLines; }
        public String getCreateTime() { return createTime; }
        public void setCreateTime(String createTime) { this.createTime = createTime; }
    }

    public static class ReportMetaRecord {
        private String matchTypeLabel;
        private String matchTimeText;
        private CoinTossRecord initialCoinToss;
        private CoinTossRecord decidingSetCoinToss;
        private String chiefRefereeName;
        private String assistantRefereeName;
        private String notes;
        private SignatureRecord signatures;

        public String getMatchTypeLabel() { return matchTypeLabel; }
        public void setMatchTypeLabel(String matchTypeLabel) { this.matchTypeLabel = matchTypeLabel; }
        public String getMatchTimeText() { return matchTimeText; }
        public void setMatchTimeText(String matchTimeText) { this.matchTimeText = matchTimeText; }
        public CoinTossRecord getInitialCoinToss() { return initialCoinToss; }
        public void setInitialCoinToss(CoinTossRecord initialCoinToss) { this.initialCoinToss = initialCoinToss; }
        public CoinTossRecord getDecidingSetCoinToss() { return decidingSetCoinToss; }
        public void setDecidingSetCoinToss(CoinTossRecord decidingSetCoinToss) { this.decidingSetCoinToss = decidingSetCoinToss; }
        public String getChiefRefereeName() { return chiefRefereeName; }
        public void setChiefRefereeName(String chiefRefereeName) { this.chiefRefereeName = chiefRefereeName; }
        public String getAssistantRefereeName() { return assistantRefereeName; }
        public void setAssistantRefereeName(String assistantRefereeName) { this.assistantRefereeName = assistantRefereeName; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public SignatureRecord getSignatures() { return signatures; }
        public void setSignatures(SignatureRecord signatures) { this.signatures = signatures; }
    }

    public static class CoinTossRecord {
        private Boolean enabled;
        private String serveTeam;
        private String chooseSideTeam;

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public String getServeTeam() { return serveTeam; }
        public void setServeTeam(String serveTeam) { this.serveTeam = serveTeam; }
        public String getChooseSideTeam() { return chooseSideTeam; }
        public void setChooseSideTeam(String chooseSideTeam) { this.chooseSideTeam = chooseSideTeam; }
    }

    public static class SignatureRecord {
        private String aCaptainLabel;
        private String bCaptainLabel;
        private String chiefRefereeLabel;
        private String assistantRefereeLabel;
        private String chiefRefereeName;
        private String assistantRefereeName;

        public String getACaptainLabel() { return aCaptainLabel; }
        public void setACaptainLabel(String aCaptainLabel) { this.aCaptainLabel = aCaptainLabel; }
        public String getBCaptainLabel() { return bCaptainLabel; }
        public void setBCaptainLabel(String bCaptainLabel) { this.bCaptainLabel = bCaptainLabel; }
        public String getChiefRefereeLabel() { return chiefRefereeLabel; }
        public void setChiefRefereeLabel(String chiefRefereeLabel) { this.chiefRefereeLabel = chiefRefereeLabel; }
        public String getAssistantRefereeLabel() { return assistantRefereeLabel; }
        public void setAssistantRefereeLabel(String assistantRefereeLabel) { this.assistantRefereeLabel = assistantRefereeLabel; }
        public String getChiefRefereeName() { return chiefRefereeName; }
        public void setChiefRefereeName(String chiefRefereeName) { this.chiefRefereeName = chiefRefereeName; }
        public String getAssistantRefereeName() { return assistantRefereeName; }
        public void setAssistantRefereeName(String assistantRefereeName) { this.assistantRefereeName = assistantRefereeName; }
    }

    public static class ReportRenderRecord {
        private HeaderRecord header;
        private RosterRenderRecord roster;
        private List<CoinTossBlockRecord> coinTossBlocks;
        private List<GameRenderRecord> games;
        private SignatureRecord signatures;
        private String notes;

        public HeaderRecord getHeader() { return header; }
        public void setHeader(HeaderRecord header) { this.header = header; }
        public RosterRenderRecord getRoster() { return roster; }
        public void setRoster(RosterRenderRecord roster) { this.roster = roster; }
        public List<CoinTossBlockRecord> getCoinTossBlocks() { return coinTossBlocks; }
        public void setCoinTossBlocks(List<CoinTossBlockRecord> coinTossBlocks) { this.coinTossBlocks = coinTossBlocks; }
        public List<GameRenderRecord> getGames() { return games; }
        public void setGames(List<GameRenderRecord> games) { this.games = games; }
        public SignatureRecord getSignatures() { return signatures; }
        public void setSignatures(SignatureRecord signatures) { this.signatures = signatures; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    public static class HeaderRecord {
        private String tournamentName;
        private String matchTypeLabel;
        private String matchTimeText;
        private String leftTeamName;
        private String rightTeamName;
        private Integer leftGameWins;
        private Integer rightGameWins;
        private List<GameScoreRecord> gameScores;

        public String getTournamentName() { return tournamentName; }
        public void setTournamentName(String tournamentName) { this.tournamentName = tournamentName; }
        public String getMatchTypeLabel() { return matchTypeLabel; }
        public void setMatchTypeLabel(String matchTypeLabel) { this.matchTypeLabel = matchTypeLabel; }
        public String getMatchTimeText() { return matchTimeText; }
        public void setMatchTimeText(String matchTimeText) { this.matchTimeText = matchTimeText; }
        public String getLeftTeamName() { return leftTeamName; }
        public void setLeftTeamName(String leftTeamName) { this.leftTeamName = leftTeamName; }
        public String getRightTeamName() { return rightTeamName; }
        public void setRightTeamName(String rightTeamName) { this.rightTeamName = rightTeamName; }
        public Integer getLeftGameWins() { return leftGameWins; }
        public void setLeftGameWins(Integer leftGameWins) { this.leftGameWins = leftGameWins; }
        public Integer getRightGameWins() { return rightGameWins; }
        public void setRightGameWins(Integer rightGameWins) { this.rightGameWins = rightGameWins; }
        public List<GameScoreRecord> getGameScores() { return gameScores; }
        public void setGameScores(List<GameScoreRecord> gameScores) { this.gameScores = gameScores; }
    }

    public static class RosterRenderRecord {
        private List<List<MemberRecord>> leftRows;
        private List<List<MemberRecord>> rightRows;

        public List<List<MemberRecord>> getLeftRows() { return leftRows; }
        public void setLeftRows(List<List<MemberRecord>> leftRows) { this.leftRows = leftRows; }
        public List<List<MemberRecord>> getRightRows() { return rightRows; }
        public void setRightRows(List<List<MemberRecord>> rightRows) { this.rightRows = rightRows; }
    }

    public static class CoinTossBlockRecord {
        private Integer gameNo;
        private String label;
        private String text;

        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }

    public static class GameRenderRecord {
        private Integer gameNo;
        private Boolean played;
        private String title;
        private String leftTeamLabel;
        private String rightTeamLabel;
        private List<RotationCellRecord> leftRotationGrid;
        private List<RotationCellRecord> rightRotationGrid;
        private List<String> timeoutLines;

        public Integer getGameNo() { return gameNo; }
        public void setGameNo(Integer gameNo) { this.gameNo = gameNo; }
        public Boolean getPlayed() { return played; }
        public void setPlayed(Boolean played) { this.played = played; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getLeftTeamLabel() { return leftTeamLabel; }
        public void setLeftTeamLabel(String leftTeamLabel) { this.leftTeamLabel = leftTeamLabel; }
        public String getRightTeamLabel() { return rightTeamLabel; }
        public void setRightTeamLabel(String rightTeamLabel) { this.rightTeamLabel = rightTeamLabel; }
        public List<RotationCellRecord> getLeftRotationGrid() { return leftRotationGrid; }
        public void setLeftRotationGrid(List<RotationCellRecord> leftRotationGrid) { this.leftRotationGrid = leftRotationGrid; }
        public List<RotationCellRecord> getRightRotationGrid() { return rightRotationGrid; }
        public void setRightRotationGrid(List<RotationCellRecord> rightRotationGrid) { this.rightRotationGrid = rightRotationGrid; }
        public List<String> getTimeoutLines() { return timeoutLines; }
        public void setTimeoutLines(List<String> timeoutLines) { this.timeoutLines = timeoutLines; }
    }

    public static class RotationCellRecord {
        private Integer slotIndex;
        private Integer primaryJerseyNumber;
        private Integer secondaryJerseyNumber;
        private Boolean slashed;

        public Integer getSlotIndex() { return slotIndex; }
        public void setSlotIndex(Integer slotIndex) { this.slotIndex = slotIndex; }
        public Integer getPrimaryJerseyNumber() { return primaryJerseyNumber; }
        public void setPrimaryJerseyNumber(Integer primaryJerseyNumber) { this.primaryJerseyNumber = primaryJerseyNumber; }
        public Integer getSecondaryJerseyNumber() { return secondaryJerseyNumber; }
        public void setSecondaryJerseyNumber(Integer secondaryJerseyNumber) { this.secondaryJerseyNumber = secondaryJerseyNumber; }
        public Boolean getSlashed() { return slashed; }
        public void setSlashed(Boolean slashed) { this.slashed = slashed; }
    }
}
