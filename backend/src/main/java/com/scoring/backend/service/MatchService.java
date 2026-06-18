package com.scoring.backend.service;

import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.dto.SaveMatchThemeConfigReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.domain.vo.MatchThemeConfigVO;

public interface MatchService {

    void updateMatchResult(String userId, String matchId, UpdateScoreReq req);

    void finishMatch(String userId, String matchId, FinishMatchReq req);

    void restartMatch(String userId, String matchId);

    void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req);

    void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req);

    void saveMatchReportMeta(String userId, String matchId, SaveMatchReportMetaReq req);

    // ==== 已废弃：配色改为前端硬编码直选 ====
    // void saveMatchThemeConfig(String userId, String matchId, SaveMatchThemeConfigReq req);

    MatchLineupConfigVO getEffectiveLineupConfig(String matchId, Integer gameNo);

    // ==== 已废弃：配色改为前端硬编码直选 ====
    // MatchThemeConfigVO getMatchThemeConfig(String matchId);

    MatchRecordDetailVO getMatchRecordDetail(String matchId);
}
