package com.scoring.backend.service.match;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.JsonUtils;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import org.springframework.stereotype.Service;

/**
 * 战报元数据装配器：负责 report JSON 的状态机（draft → complete → sealed）
 * 与读写两侧的 VO 组装。纯内存 JSON 操作 + 单表读取，不承担事务边界。
 */
@Service
public class MatchReportAssembler {

    private final MatchReportMetaMapper matchReportMetaMapper;

    public MatchReportAssembler(MatchReportMetaMapper matchReportMetaMapper) {
        this.matchReportMetaMapper = matchReportMetaMapper;
    }

    public MatchReportMeta findMatchReportMeta(String matchId) {
        return matchReportMetaMapper.selectOne(
                new QueryWrapper<MatchReportMeta>().eq("match_id", matchId)
        );
    }

    public void ensureReportNotSealed(String matchId) {
        MatchReportMeta entity = findMatchReportMeta(matchId);
        JSONObject root = JsonUtils.parseObject(entity == null ? null : entity.getMetaJson());
        if ("sealed".equals(reportStateObject(root).getStr("status"))) {
            throw new IllegalArgumentException("战报已封存，不能重启比赛");
        }
    }

    public void ensureReportDraft(JSONObject root) {
        if ("sealed".equals(reportStateObject(root).getStr("status"))) {
            throw new IllegalArgumentException("战报已封存，不能修改");
        }
    }

    public void ensureReportComplete(JSONObject root) {
        JSONObject signatures = reportSignaturesObject(root);
        boolean hasParticipants = StrUtil.isNotBlank(signatures.getStr("leftParticipant"))
                && StrUtil.isNotBlank(signatures.getStr("rightParticipant"));
        boolean legacyComplete = hasParticipants
                && StrUtil.isNotBlank(signatures.getStr("referee"))
                && StrUtil.isNotBlank(signatures.getStr("matchDateText"));
        boolean refereePairComplete = hasParticipants
                && StrUtil.isNotBlank(signatures.getStr("chiefReferee"))
                && StrUtil.isNotBlank(signatures.getStr("assistantReferee"));
        if (!legacyComplete && !refereePairComplete) {
            throw new IllegalArgumentException("战报签名和日期未填写完整");
        }
    }

    public JSONObject reportStateObject(JSONObject root) {
        JSONObject state = root == null ? null : root.getJSONObject("reportState");
        if (state == null) {
            state = new JSONObject();
        }
        state.set("status", StrUtil.blankToDefault(StrUtil.trim(state.getStr("status")), "draft"));
        state.set("sealedAt", StrUtil.trimToEmpty(state.getStr("sealedAt")));
        state.set("sealedBy", StrUtil.trimToEmpty(state.getStr("sealedBy")));
        return state;
    }

    public String buildReportMetaJson(SaveMatchReportMetaReq req, JSONObject current) {
        JSONObject root = current == null ? new JSONObject() : current;
        root.set("reportState", reportStateObject(root));
        putTextIfPresent(root, "matchTypeLabel", req == null ? null : req.getMatchTypeLabel(), "");
        putTextIfPresent(root, "matchTimeText", req == null ? null : req.getMatchTimeText(), "");
        putTextIfPresent(root, "chiefRefereeName", req == null ? null : req.getChiefRefereeName(), "");
        putTextIfPresent(root, "assistantRefereeName", req == null ? null : req.getAssistantRefereeName(), "");
        putTextIfPresent(root, "notes", req == null ? null : req.getNotes(), "");

        JSONObject initialCoinToss = childObject(root, "initialCoinToss");
        if (!initialCoinToss.containsKey("enabled")) {
            initialCoinToss.set("enabled", true);
        }
        putTeamLabelIfPresent(initialCoinToss, "serveTeam", req == null ? null : req.getInitialCoinTossServeTeam());
        putTeamLabelIfPresent(initialCoinToss, "chooseSideTeam", req == null ? null : req.getInitialCoinTossChooseSideTeam());
        root.set("initialCoinToss", initialCoinToss);

        JSONObject decidingSetCoinToss = childObject(root, "decidingSetCoinToss");
        if (!decidingSetCoinToss.containsKey("enabled")) {
            decidingSetCoinToss.set("enabled", false);
        }
        if (req != null && req.getDecidingSetCoinTossEnabled() != null) {
            decidingSetCoinToss.set("enabled", Boolean.TRUE.equals(req.getDecidingSetCoinTossEnabled()));
        }
        putTeamLabelIfPresent(decidingSetCoinToss, "serveTeam", req == null ? null : req.getDecidingSetCoinTossServeTeam());
        putTeamLabelIfPresent(decidingSetCoinToss, "chooseSideTeam", req == null ? null : req.getDecidingSetCoinTossChooseSideTeam());
        root.set("decidingSetCoinToss", decidingSetCoinToss);

        JSONObject signatures = childObject(root, "signatures");
        putTextIfPresent(signatures, "aCaptainLabel", req == null ? null : req.getACaptainLabel(), "A队队长");
        putTextIfPresent(signatures, "bCaptainLabel", req == null ? null : req.getBCaptainLabel(), "B队队长");
        putTextIfPresent(signatures, "chiefRefereeLabel", req == null ? null : req.getChiefRefereeLabel(), "主裁");
        putTextIfPresent(signatures, "assistantRefereeLabel", req == null ? null : req.getAssistantRefereeLabel(), "副裁");
        putTextIfPresent(signatures, "chiefRefereeName", req == null ? null : req.getChiefRefereeName(), "");
        putTextIfPresent(signatures, "assistantRefereeName", req == null ? null : req.getAssistantRefereeName(), "");
        root.set("signatures", signatures);

        JSONObject teamRecordSignatures = new JSONObject();
        JSONObject currentTeamRecordSignatures = current == null ? null : current.getJSONObject("teamRecordSignatures");
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "leftCaptain", req == null ? null : req.getTeamLeftCaptainSignature());
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "rightCaptain", req == null ? null : req.getTeamRightCaptainSignature());
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "referee", req == null ? null : req.getTeamRefereeSignature());
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "chiefReferee", req == null ? null : req.getTeamChiefRefereeSignature());
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "assistantReferee", req == null ? null : req.getTeamAssistantRefereeSignature());
        putSealedTeamRecordValue(teamRecordSignatures, currentTeamRecordSignatures, "matchDateText", req == null ? null : req.getTeamMatchDateText());
        root.set("teamRecordSignatures", teamRecordSignatures);

        JSONObject reportSignatures = reportSignaturesObject(root);
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "leftParticipant", firstNonNull(req == null ? null : req.getReportLeftParticipantSignature(), req == null ? null : req.getTeamLeftCaptainSignature()));
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "rightParticipant", firstNonNull(req == null ? null : req.getReportRightParticipantSignature(), req == null ? null : req.getTeamRightCaptainSignature()));
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "referee", firstNonNull(req == null ? null : req.getReportRefereeSignature(), req == null ? null : req.getTeamRefereeSignature()));
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "chiefReferee", firstNonNull(req == null ? null : req.getReportChiefRefereeSignature(), req == null ? null : req.getTeamChiefRefereeSignature()));
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "assistantReferee", firstNonNull(req == null ? null : req.getReportAssistantRefereeSignature(), req == null ? null : req.getTeamAssistantRefereeSignature()));
        putSealedTeamRecordValue(reportSignatures, reportSignatures, "matchDateText", firstNonNull(req == null ? null : req.getReportMatchDateText(), req == null ? null : req.getTeamMatchDateText()));
        root.set("reportSignatures", reportSignatures);
        return JSONUtil.toJsonStr(root);
    }

    public MatchRecordDetailVO.ReportMetaRecord buildReportMetaRecord(MatchReportMeta entity) {
        JSONObject object = JsonUtils.parseObject(entity == null ? null : entity.getMetaJson());
        MatchRecordDetailVO.ReportMetaRecord record = new MatchRecordDetailVO.ReportMetaRecord();
        record.setMatchTypeLabel(StrUtil.trimToEmpty(object.getStr("matchTypeLabel")));
        record.setMatchTimeText(StrUtil.trimToEmpty(object.getStr("matchTimeText")));
        record.setChiefRefereeName(StrUtil.trimToEmpty(object.getStr("chiefRefereeName")));
        record.setAssistantRefereeName(StrUtil.trimToEmpty(object.getStr("assistantRefereeName")));
        record.setNotes(StrUtil.trimToEmpty(object.getStr("notes")));
        record.setInitialCoinToss(buildCoinTossRecord(object.getJSONObject("initialCoinToss"), true));
        record.setDecidingSetCoinToss(buildCoinTossRecord(object.getJSONObject("decidingSetCoinToss"), false));
        record.setSignatures(buildSignatureRecord(object.getJSONObject("signatures")));
        record.setReportState(buildReportStateRecord(object));
        record.setReportSignatures(buildReportSignaturesRecord(object));
        return record;
    }

    public MatchRecordDetailVO.SignatureRecord buildSignatureRecord(JSONObject object) {
        MatchRecordDetailVO.SignatureRecord record = new MatchRecordDetailVO.SignatureRecord();
        record.setACaptainLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("aCaptainLabel")), "A队队长"));
        record.setBCaptainLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("bCaptainLabel")), "B队队长"));
        record.setChiefRefereeLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("chiefRefereeLabel")), "主裁"));
        record.setAssistantRefereeLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("assistantRefereeLabel")), "副裁"));
        record.setChiefRefereeName(StrUtil.trimToEmpty(object == null ? null : object.getStr("chiefRefereeName")));
        record.setAssistantRefereeName(StrUtil.trimToEmpty(object == null ? null : object.getStr("assistantRefereeName")));
        return record;
    }

    private MatchRecordDetailVO.ReportStateRecord buildReportStateRecord(JSONObject root) {
        JSONObject object = reportStateObject(root);
        MatchRecordDetailVO.ReportStateRecord record = new MatchRecordDetailVO.ReportStateRecord();
        record.setStatus(object.getStr("status"));
        record.setSealedAt(object.getStr("sealedAt"));
        record.setSealedBy(object.getStr("sealedBy"));
        return record;
    }

    private MatchRecordDetailVO.ReportSignaturesRecord buildReportSignaturesRecord(JSONObject root) {
        JSONObject object = reportSignaturesObject(root);
        MatchRecordDetailVO.ReportSignaturesRecord record = new MatchRecordDetailVO.ReportSignaturesRecord();
        record.setLeftParticipant(StrUtil.trimToEmpty(object.getStr("leftParticipant")));
        record.setRightParticipant(StrUtil.trimToEmpty(object.getStr("rightParticipant")));
        record.setReferee(StrUtil.trimToEmpty(object.getStr("referee")));
        record.setChiefReferee(StrUtil.trimToEmpty(object.getStr("chiefReferee")));
        record.setAssistantReferee(StrUtil.trimToEmpty(object.getStr("assistantReferee")));
        record.setMatchDateText(StrUtil.trimToEmpty(object.getStr("matchDateText")));
        return record;
    }

    private MatchRecordDetailVO.CoinTossRecord buildCoinTossRecord(JSONObject object, boolean defaultEnabled) {
        MatchRecordDetailVO.CoinTossRecord record = new MatchRecordDetailVO.CoinTossRecord();
        record.setEnabled(object == null ? defaultEnabled : Boolean.TRUE.equals(object.getBool("enabled", defaultEnabled)));
        record.setServeTeam(normalizeReportTeamLabel(object == null ? null : object.getStr("serveTeam")));
        record.setChooseSideTeam(normalizeReportTeamLabel(object == null ? null : object.getStr("chooseSideTeam")));
        return record;
    }

    private JSONObject reportSignaturesObject(JSONObject root) {
        JSONObject signatures = root == null ? null : root.getJSONObject("reportSignatures");
        if (signatures == null) {
            signatures = new JSONObject();
        }
        JSONObject legacy = root == null ? null : root.getJSONObject("teamRecordSignatures");
        putSignatureDefault(signatures, "leftParticipant", legacy == null ? null : legacy.getStr("leftCaptain"));
        putSignatureDefault(signatures, "rightParticipant", legacy == null ? null : legacy.getStr("rightCaptain"));
        putSignatureDefault(signatures, "referee", legacy == null ? null : legacy.getStr("referee"));
        putSignatureDefault(signatures, "chiefReferee", legacy == null ? null : legacy.getStr("chiefReferee"));
        putSignatureDefault(signatures, "assistantReferee", legacy == null ? null : legacy.getStr("assistantReferee"));
        putSignatureDefault(signatures, "matchDateText", legacy == null ? null : legacy.getStr("matchDateText"));
        return signatures;
    }

    private void putSignatureDefault(JSONObject target, String key, String fallback) {
        if (!target.containsKey(key)) {
            target.set(key, StrUtil.trimToEmpty(fallback));
        }
    }

    private String firstNonNull(String first, String second) {
        return first != null ? first : second;
    }

    private JSONObject childObject(JSONObject root, String key) {
        JSONObject child = root == null ? null : root.getJSONObject(key);
        return child == null ? new JSONObject() : child;
    }

    private void putTextIfPresent(JSONObject target, String key, String incoming, String defaultValue) {
        if (incoming != null) {
            target.set(key, StrUtil.blankToDefault(StrUtil.trim(incoming), defaultValue));
            return;
        }
        if (!target.containsKey(key)) {
            target.set(key, defaultValue);
        }
    }

    private void putTeamLabelIfPresent(JSONObject target, String key, String incoming) {
        if (incoming != null) {
            target.set(key, normalizeReportTeamLabel(incoming));
            return;
        }
        if (!target.containsKey(key)) {
            target.set(key, "");
        }
    }

    private void putSealedTeamRecordValue(JSONObject target, JSONObject current, String key, String incoming) {
        String existingValue = StrUtil.trimToEmpty(current == null ? null : current.getStr(key));
        String incomingValue = StrUtil.trimToEmpty(incoming);
        if (StrUtil.isNotBlank(existingValue)) {
            if (StrUtil.isNotBlank(incomingValue) && !StrUtil.equals(existingValue, incomingValue)) {
                throw new IllegalArgumentException("战报签名已确认，不能修改");
            }
            target.set(key, existingValue);
            return;
        }
        target.set(key, incomingValue);
    }

    private String normalizeReportTeamLabel(String value) {
        String text = StrUtil.trimToEmpty(value).toUpperCase();
        if ("B".equals(text)) {
            return "B";
        }
        return "A".equals(text) ? "A" : "";
    }
}
