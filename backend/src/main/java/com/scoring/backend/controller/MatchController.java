package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.dto.SaveTeamMatchLineupReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.domain.vo.MatchLockVO;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.domain.vo.TeamMatchChildMatchVO;
import com.scoring.backend.domain.vo.TeamMatchLineupVO;
import com.scoring.backend.security.AuthContext;
import com.scoring.backend.security.AuthGuard;
import com.scoring.backend.service.MatchService;
import com.scoring.backend.service.TeamMatchService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/matches")
public class MatchController {

    private static final String MATCH_LOCK_TOKEN_HEADER = "X-Match-Lock-Token";

    private final MatchService matchService;
    private final TeamMatchService teamMatchService;
    private final AuthGuard authGuard;

    public MatchController(MatchService matchService, TeamMatchService teamMatchService, AuthGuard authGuard) {
        this.matchService = matchService;
        this.teamMatchService = teamMatchService;
        this.authGuard = authGuard;
    }

    @PutMapping("/{id}/score")
    public ApiResponse<Void> updateScore(@PathVariable("id") String id,
                                         @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                         @Valid @RequestBody UpdateScoreReq req) {
        matchService.updateMatchResult(authGuard.requireUserId(), id, req, lockToken);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/lock")
    public ApiResponse<MatchLockVO> acquireMatchLock(@PathVariable("id") String id,
                                                     @Valid @RequestBody MatchLockReq req) {
        return ApiResponse.ok(matchService.acquireMatchLock(authGuard.requireUserId(), id, req));
    }

    @PostMapping("/{id}/heartbeat")
    public ApiResponse<MatchLockVO> heartbeatMatchLock(@PathVariable("id") String id,
                                                       @Valid @RequestBody MatchLockReq req) {
        return ApiResponse.ok(matchService.heartbeatMatchLock(authGuard.requireUserId(), id, req));
    }

    @PostMapping("/{id}/release")
    public ApiResponse<Void> releaseMatchLock(@PathVariable("id") String id,
                                              @Valid @RequestBody MatchLockReq req) {
        matchService.releaseMatchLock(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/lineup-config")
    public ApiResponse<MatchLineupConfigVO> getLineupConfig(@PathVariable("id") String id,
                                                            @RequestParam("gameNo") Integer gameNo) {
        return ApiResponse.ok(matchService.getEffectiveLineupConfig(AuthContext.getUserId(), id, gameNo));
    }

   @GetMapping("/{id}/record")
   public ApiResponse<MatchRecordDetailVO> getMatchRecord(@PathVariable("id") String id) {
       return ApiResponse.ok(matchService.getMatchRecordDetail(AuthContext.getUserId(), id));
   }

    @GetMapping("/{id}/can-operate")
    public ApiResponse<Boolean> canOperateMatch(@PathVariable("id") String id) {
        return ApiResponse.ok(matchService.canOperateMatch(AuthContext.getUserId(), id));
    }

    @GetMapping("/{id}/team-lineup")
    public ApiResponse<TeamMatchLineupVO> getTeamMatchLineup(@PathVariable("id") String id) {
        return ApiResponse.ok(teamMatchService.getLineup(AuthContext.getUserId(), id));
    }

    @PutMapping("/{id}/team-lineup")
    public ApiResponse<TeamMatchLineupVO> saveTeamMatchLineup(@PathVariable("id") String id,
                                                              @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                                              @RequestBody SaveTeamMatchLineupReq req) {
        return ApiResponse.ok(teamMatchService.saveLineup(authGuard.requireUserId(), id, req, lockToken));
    }

    @PutMapping("/{id}/team-items/{itemCode}/start")
    public ApiResponse<TeamMatchChildMatchVO> startTeamMatchItem(@PathVariable("id") String id,
                                                                 @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                                                 @PathVariable("itemCode") String itemCode) {
        return ApiResponse.ok(teamMatchService.startChildMatch(authGuard.requireUserId(), id, itemCode, lockToken));
    }

    // ==== 已废弃：配色改为前端硬编码直选，不再从后端存取 ====
    // @GetMapping("/{id}/theme-config")
    // public ApiResponse<MatchThemeConfigVO> getThemeConfig(@PathVariable("id") String id) {
    //     return ApiResponse.ok(matchService.getMatchThemeConfig(id));
    // }

    @PutMapping("/{id}/team-match/settle")
    public ApiResponse<Void> settleTeamMatch(@PathVariable("id") String id,
                                             @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken) {
        matchService.settleTeamMatch(authGuard.requireUserId(), id, lockToken);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/lineup-config")
    public ApiResponse<Void> saveLineupConfig(@PathVariable("id") String id,
                                              @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                              @RequestBody SaveMatchLineupConfigReq req) {
        matchService.saveLineupConfig(authGuard.requireUserId(), id, req, lockToken);
        return ApiResponse.ok();
    }

    // ==== 已废弃：配色改为前端硬编码直选，不再从后端存取 ====
    // @PutMapping("/{id}/theme-config")
    // public ApiResponse<Void> saveThemeConfig(@PathVariable("id") String id,
    //                                          @RequestBody SaveMatchThemeConfigReq req) {
    //     matchService.saveMatchThemeConfig(authGuard.requireUserId(), id, req);
    //     return ApiResponse.ok();
    // }

    @PutMapping("/{id}/report-meta")
    public ApiResponse<Void> saveReportMeta(@PathVariable("id") String id,
                                            @RequestBody SaveMatchReportMetaReq req) {
        matchService.saveMatchReportMeta(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/report-seal")
    public ApiResponse<Void> sealReport(@PathVariable("id") String id) {
        matchService.sealMatchReport(authGuard.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/events")
    public ApiResponse<Void> saveMatchEvents(@PathVariable("id") String id,
                                             @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                             @Valid @RequestBody SaveMatchEventsReq req) {
        matchService.saveMatchEvents(authGuard.requireUserId(), id, req, lockToken);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/finish")
    public ApiResponse<Void> finishMatch(@PathVariable("id") String id,
                                         @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken,
                                         @Valid @RequestBody FinishMatchReq req) {
        matchService.finishMatch(authGuard.requireUserId(), id, req, lockToken);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/restart")
    public ApiResponse<Void> restartMatch(@PathVariable("id") String id,
                                          @RequestHeader(value = MATCH_LOCK_TOKEN_HEADER, required = false) String lockToken) {
        matchService.restartMatch(authGuard.requireUserId(), id, lockToken);
        return ApiResponse.ok();
    }
}
