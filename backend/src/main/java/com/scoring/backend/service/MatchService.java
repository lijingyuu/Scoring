package com.scoring.backend.service;

import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;

public interface MatchService {

    void updateMatchResult(String userId, String matchId, UpdateScoreReq req);

    void finishMatch(String userId, String matchId, FinishMatchReq req);

    void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req);

    MatchLineupConfigVO getEffectiveLineupConfig(String matchId, Integer gameNo);
}
