package com.scoring.backend.service;

import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;

public interface MatchService {

    void updateMatchResult(String matchId, UpdateScoreReq req);

    void finishMatch(String matchId, FinishMatchReq req);
}
