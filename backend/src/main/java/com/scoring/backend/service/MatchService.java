package com.scoring.backend.service;

import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;

public interface MatchService {

    void updateMatchResult(String userId, String matchId, UpdateScoreReq req);

    void finishMatch(String userId, String matchId, FinishMatchReq req);
}
