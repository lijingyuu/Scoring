package com.scoring.backend.service;

import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.domain.vo.MatchLockVO;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;

public interface MatchService {

    void updateMatchResult(String userId, String matchId, UpdateScoreReq req);
    void updateMatchResult(String userId, String matchId, UpdateScoreReq req, String lockToken);

    void finishMatch(String userId, String matchId, FinishMatchReq req);
    void finishMatch(String userId, String matchId, FinishMatchReq req, String lockToken);

    void settleTeamMatch(String userId, String matchId);
    void settleTeamMatch(String userId, String matchId, String lockToken);

    void restartMatch(String userId, String matchId);
    void restartMatch(String userId, String matchId, String lockToken);

    void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req);
    void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req, String lockToken);

    void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req);
    void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req, String lockToken);

    void saveMatchReportMeta(String userId, String matchId, SaveMatchReportMetaReq req);

    void sealMatchReport(String userId, String matchId);

    MatchLockVO acquireMatchLock(String userId, String matchId, MatchLockReq req);

    MatchLockVO heartbeatMatchLock(String userId, String matchId, MatchLockReq req);

    void releaseMatchLock(String userId, String matchId, MatchLockReq req);

    // ==== 已废弃：配色改为前端硬编码直选 ====
    // void saveMatchThemeConfig(String userId, String matchId, SaveMatchThemeConfigReq req);

    MatchLineupConfigVO getEffectiveLineupConfig(String currentUserId, String matchId, Integer gameNo);

    // ==== 已废弃：配色改为前端硬编码直选 ====
    // MatchThemeConfigVO getMatchThemeConfig(String matchId);

    MatchRecordDetailVO getMatchRecordDetail(String currentUserId, String matchId);
    boolean canOperateMatch(String userId, String matchId);
}
