package com.scoring.backend.service;

import com.scoring.backend.domain.dto.SaveTeamMatchLineupReq;
import com.scoring.backend.domain.vo.TeamMatchChildMatchVO;
import com.scoring.backend.domain.vo.TeamMatchLineupVO;

public interface TeamMatchService {
    TeamMatchLineupVO getLineup(String currentUserId, String matchId);

    TeamMatchLineupVO saveLineup(String userId, String matchId, SaveTeamMatchLineupReq req);
    TeamMatchLineupVO saveLineup(String userId, String matchId, SaveTeamMatchLineupReq req, String lockToken);

    TeamMatchChildMatchVO startChildMatch(String userId, String matchId, String itemCode);
    TeamMatchChildMatchVO startChildMatch(String userId, String matchId, String itemCode, String lockToken);
}
