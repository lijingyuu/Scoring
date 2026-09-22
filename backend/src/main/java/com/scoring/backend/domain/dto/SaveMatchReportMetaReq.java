package com.scoring.backend.domain.dto;

import jakarta.validation.constraints.Size;

/**
 * 战报元信息：字段落 match_report_meta.meta_json，全部字段长度受限，避免超长文本直捅 DB。
 * 长度按语义分档：普通文本/姓名 200、备注 2000、签名 200000（前端 canvas 导出的 base64 data URL）。
 */
public class SaveMatchReportMetaReq {

    @Size(max = 200, message = "比赛类型名称最长200个字符")
    private String matchTypeLabel;
    @Size(max = 200, message = "比赛时间文本最长200个字符")
    private String matchTimeText;
    @Size(max = 200, message = "首发球方最长200个字符")
    private String initialCoinTossServeTeam;
    @Size(max = 200, message = "首发选边方最长200个字符")
    private String initialCoinTossChooseSideTeam;
    private Boolean decidingSetCoinTossEnabled;
    @Size(max = 200, message = "决胜局首发球方最长200个字符")
    private String decidingSetCoinTossServeTeam;
    @Size(max = 200, message = "决胜局首发选边方最长200个字符")
    private String decidingSetCoinTossChooseSideTeam;
    @Size(max = 200, message = "裁判长姓名最长200个字符")
    private String chiefRefereeName;
    @Size(max = 200, message = "副裁判姓名最长200个字符")
    private String assistantRefereeName;
    @Size(max = 2000, message = "备注最长2000个字符")
    private String notes;
    @Size(max = 200, message = "A队队长标签最长200个字符")
    private String aCaptainLabel;
    @Size(max = 200, message = "B队队长标签最长200个字符")
    private String bCaptainLabel;
    @Size(max = 200, message = "裁判长标签最长200个字符")
    private String chiefRefereeLabel;
    @Size(max = 200, message = "副裁判标签最长200个字符")
    private String assistantRefereeLabel;
    @Size(max = 200000, message = "队长签名过大")
    private String teamLeftCaptainSignature;
    @Size(max = 200000, message = "队长签名过大")
    private String teamRightCaptainSignature;
    @Size(max = 200000, message = "裁判签名过大")
    private String teamRefereeSignature;
    @Size(max = 200000, message = "裁判长签名过大")
    private String teamChiefRefereeSignature;
    @Size(max = 200000, message = "副裁判签名过大")
    private String teamAssistantRefereeSignature;
    @Size(max = 200, message = "比赛日期文本最长200个字符")
    private String teamMatchDateText;
    @Size(max = 200000, message = "参赛方签名过大")
    private String reportLeftParticipantSignature;
    @Size(max = 200000, message = "参赛方签名过大")
    private String reportRightParticipantSignature;
    @Size(max = 200000, message = "裁判签名过大")
    private String reportRefereeSignature;
    @Size(max = 200000, message = "裁判长签名过大")
    private String reportChiefRefereeSignature;
    @Size(max = 200000, message = "副裁判签名过大")
    private String reportAssistantRefereeSignature;
    @Size(max = 200, message = "比赛日期文本最长200个字符")
    private String reportMatchDateText;

    public String getMatchTypeLabel() { return matchTypeLabel; }
    public void setMatchTypeLabel(String matchTypeLabel) { this.matchTypeLabel = matchTypeLabel; }
    public String getMatchTimeText() { return matchTimeText; }
    public void setMatchTimeText(String matchTimeText) { this.matchTimeText = matchTimeText; }
    public String getInitialCoinTossServeTeam() { return initialCoinTossServeTeam; }
    public void setInitialCoinTossServeTeam(String initialCoinTossServeTeam) { this.initialCoinTossServeTeam = initialCoinTossServeTeam; }
    public String getInitialCoinTossChooseSideTeam() { return initialCoinTossChooseSideTeam; }
    public void setInitialCoinTossChooseSideTeam(String initialCoinTossChooseSideTeam) { this.initialCoinTossChooseSideTeam = initialCoinTossChooseSideTeam; }
    public Boolean getDecidingSetCoinTossEnabled() { return decidingSetCoinTossEnabled; }
    public void setDecidingSetCoinTossEnabled(Boolean decidingSetCoinTossEnabled) { this.decidingSetCoinTossEnabled = decidingSetCoinTossEnabled; }
    public String getDecidingSetCoinTossServeTeam() { return decidingSetCoinTossServeTeam; }
    public void setDecidingSetCoinTossServeTeam(String decidingSetCoinTossServeTeam) { this.decidingSetCoinTossServeTeam = decidingSetCoinTossServeTeam; }
    public String getDecidingSetCoinTossChooseSideTeam() { return decidingSetCoinTossChooseSideTeam; }
    public void setDecidingSetCoinTossChooseSideTeam(String decidingSetCoinTossChooseSideTeam) { this.decidingSetCoinTossChooseSideTeam = decidingSetCoinTossChooseSideTeam; }
    public String getChiefRefereeName() { return chiefRefereeName; }
    public void setChiefRefereeName(String chiefRefereeName) { this.chiefRefereeName = chiefRefereeName; }
    public String getAssistantRefereeName() { return assistantRefereeName; }
    public void setAssistantRefereeName(String assistantRefereeName) { this.assistantRefereeName = assistantRefereeName; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public String getACaptainLabel() { return aCaptainLabel; }
    public void setACaptainLabel(String aCaptainLabel) { this.aCaptainLabel = aCaptainLabel; }
    public String getBCaptainLabel() { return bCaptainLabel; }
    public void setBCaptainLabel(String bCaptainLabel) { this.bCaptainLabel = bCaptainLabel; }
    public String getChiefRefereeLabel() { return chiefRefereeLabel; }
    public void setChiefRefereeLabel(String chiefRefereeLabel) { this.chiefRefereeLabel = chiefRefereeLabel; }
    public String getAssistantRefereeLabel() { return assistantRefereeLabel; }
    public void setAssistantRefereeLabel(String assistantRefereeLabel) { this.assistantRefereeLabel = assistantRefereeLabel; }
    public String getTeamLeftCaptainSignature() { return teamLeftCaptainSignature; }
    public void setTeamLeftCaptainSignature(String teamLeftCaptainSignature) { this.teamLeftCaptainSignature = teamLeftCaptainSignature; }
    public String getTeamRightCaptainSignature() { return teamRightCaptainSignature; }
    public void setTeamRightCaptainSignature(String teamRightCaptainSignature) { this.teamRightCaptainSignature = teamRightCaptainSignature; }
    public String getTeamRefereeSignature() { return teamRefereeSignature; }
    public void setTeamRefereeSignature(String teamRefereeSignature) { this.teamRefereeSignature = teamRefereeSignature; }
    public String getTeamChiefRefereeSignature() { return teamChiefRefereeSignature; }
    public void setTeamChiefRefereeSignature(String teamChiefRefereeSignature) { this.teamChiefRefereeSignature = teamChiefRefereeSignature; }
    public String getTeamAssistantRefereeSignature() { return teamAssistantRefereeSignature; }
    public void setTeamAssistantRefereeSignature(String teamAssistantRefereeSignature) { this.teamAssistantRefereeSignature = teamAssistantRefereeSignature; }
    public String getTeamMatchDateText() { return teamMatchDateText; }
    public void setTeamMatchDateText(String teamMatchDateText) { this.teamMatchDateText = teamMatchDateText; }
    public String getReportLeftParticipantSignature() { return reportLeftParticipantSignature; }
    public void setReportLeftParticipantSignature(String reportLeftParticipantSignature) { this.reportLeftParticipantSignature = reportLeftParticipantSignature; }
    public String getReportRightParticipantSignature() { return reportRightParticipantSignature; }
    public void setReportRightParticipantSignature(String reportRightParticipantSignature) { this.reportRightParticipantSignature = reportRightParticipantSignature; }
    public String getReportRefereeSignature() { return reportRefereeSignature; }
    public void setReportRefereeSignature(String reportRefereeSignature) { this.reportRefereeSignature = reportRefereeSignature; }
    public String getReportChiefRefereeSignature() { return reportChiefRefereeSignature; }
    public void setReportChiefRefereeSignature(String reportChiefRefereeSignature) { this.reportChiefRefereeSignature = reportChiefRefereeSignature; }
    public String getReportAssistantRefereeSignature() { return reportAssistantRefereeSignature; }
    public void setReportAssistantRefereeSignature(String reportAssistantRefereeSignature) { this.reportAssistantRefereeSignature = reportAssistantRefereeSignature; }
    public String getReportMatchDateText() { return reportMatchDateText; }
    public void setReportMatchDateText(String reportMatchDateText) { this.reportMatchDateText = reportMatchDateText; }
}
