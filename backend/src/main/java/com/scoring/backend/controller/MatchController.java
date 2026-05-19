package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;

    public MatchController(MatchService matchService) {
        this.matchService = matchService;
    }

    @PutMapping("/{id}/score")
    public ApiResponse<Void> updateScore(@PathVariable("id") String id,
                                         @Valid @RequestBody UpdateScoreReq req) {
        matchService.updateMatchResult(id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/finish")
    public ApiResponse<Void> finishMatch(@PathVariable("id") String id,
                                         @Valid @RequestBody FinishMatchReq req) {
        matchService.finishMatch(id, req);
        return ApiResponse.ok();
    }
}
