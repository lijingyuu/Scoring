package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.security.AuthGuard;
import com.scoring.backend.service.MatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private final MatchService matchService;
    private final AuthGuard authGuard;

    public MatchController(MatchService matchService, AuthGuard authGuard) {
        this.matchService = matchService;
        this.authGuard = authGuard;
    }

    @PutMapping("/{id}/score")
    public ApiResponse<Void> updateScore(@PathVariable("id") String id,
                                         @Valid @RequestBody UpdateScoreReq req) {
        matchService.updateMatchResult(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/lineup-config")
    public ApiResponse<MatchLineupConfigVO> getLineupConfig(@PathVariable("id") String id,
                                                            @RequestParam("gameNo") Integer gameNo) {
        return ApiResponse.ok(matchService.getEffectiveLineupConfig(id, gameNo));
    }

    @PutMapping("/{id}/lineup-config")
    public ApiResponse<Void> saveLineupConfig(@PathVariable("id") String id,
                                              @RequestBody SaveMatchLineupConfigReq req) {
        matchService.saveLineupConfig(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/events")
    public ApiResponse<Void> saveMatchEvents(@PathVariable("id") String id,
                                             @Valid @RequestBody SaveMatchEventsReq req) {
        matchService.saveMatchEvents(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/finish")
    public ApiResponse<Void> finishMatch(@PathVariable("id") String id,
                                         @Valid @RequestBody FinishMatchReq req) {
        matchService.finishMatch(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }
}
