package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.dto.GenerateKnockoutReq;
import com.scoring.backend.domain.dto.UpdateQualificationOverridesReq;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRankingConfigReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.KnockoutPreviewVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.domain.vo.TournamentRankingConfigVO;
import com.scoring.backend.domain.vo.TournamentTeamsVO;
import com.scoring.backend.security.AuthContext;
import com.scoring.backend.security.AuthGuard;
import com.scoring.backend.service.TournamentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;
    private final AuthGuard authGuard;

    public TournamentController(TournamentService tournamentService, AuthGuard authGuard) {
        this.tournamentService = tournamentService;
        this.authGuard = authGuard;
    }

    @GetMapping
    public ApiResponse<List<Tournament>> listTournaments(@RequestParam(value = "keyword", required = false) String keyword) {
        return ApiResponse.ok(tournamentService.listTournaments(AuthContext.getUserId(), keyword));
    }

    @PostMapping
    public ApiResponse<Map<String, String>> createTournament(@Valid @RequestBody CreateTournamentReq req) {
        String tournamentId = tournamentService.createTournament(authGuard.requireUserId(), req);
        Map<String, String> data = new HashMap<>();
        data.put("tournamentId", tournamentId);
        return ApiResponse.ok(data);
    }

    @GetMapping("/{id}")
    public ApiResponse<TournamentDetailVO> getTournamentDetail(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getTournamentDetail(id, AuthContext.getUserId()));
    }

    @PutMapping("/{id}/archive")
    public ApiResponse<Void> archiveTournament(@PathVariable("id") String id) {
        tournamentService.archiveTournament(authGuard.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PutMapping("/{id}/unarchive")
    public ApiResponse<Void> unarchiveTournament(@PathVariable("id") String id) {
        tournamentService.unarchiveTournament(authGuard.requireUserId(), id);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/favorite")
    public ApiResponse<Void> favoriteTournament(@PathVariable("id") String id) {
        tournamentService.favoriteTournament(authGuard.requireUserId(), id);
        return ApiResponse.ok();
    }

    @DeleteMapping("/{id}/favorite")
    public ApiResponse<Void> unfavoriteTournament(@PathVariable("id") String id) {
        tournamentService.unfavoriteTournament(authGuard.requireUserId(), id);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/bracket")
    public ApiResponse<TournamentBracketVO> getBracket(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getBracket(id, AuthContext.getUserId()));
    }

    @GetMapping("/{id}/groups")
    public ApiResponse<TournamentGroupsVO> getGroups(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getGroups(id, AuthContext.getUserId()));
    }

    @GetMapping("/{id}/group-standings")
    public ApiResponse<GroupStandingsVO> getGroupStandings(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getGroupStandings(id, AuthContext.getUserId()));
    }

    @GetMapping("/{id}/ranking-config")
    public ApiResponse<TournamentRankingConfigVO> getRankingConfig(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getRankingConfig(id, AuthContext.getUserId()));
    }

    @PutMapping("/{id}/ranking-config")
    public ApiResponse<TournamentRankingConfigVO> updateRankingConfig(@PathVariable("id") String id,
                                                                      @Valid @RequestBody UpdateTournamentRankingConfigReq req) {
        return ApiResponse.ok(tournamentService.updateRankingConfig(authGuard.requireUserId(), id, req));
    }

    @PutMapping("/{id}/qualification-overrides")
    public ApiResponse<Void> updateQualificationOverrides(@PathVariable("id") String id,
                                                          @RequestBody UpdateQualificationOverridesReq req) {
        tournamentService.updateQualificationOverrides(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @GetMapping("/{id}/teams")
    public ApiResponse<TournamentTeamsVO> getTeams(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getTeams(id, AuthContext.getUserId()));
    }

    @PostMapping("/{id}/knockout-preview")
    public ApiResponse<KnockoutPreviewVO> previewKnockout(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.previewKnockout(authGuard.requireUserId(), id));
    }

    @PostMapping("/{id}/generate-knockout")
    public ApiResponse<Void> generateKnockout(@PathVariable("id") String id,
                                              @RequestBody(required = false) GenerateKnockoutReq req) {
        tournamentService.generateKnockout(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/referee-auth")
    public ApiResponse<TournamentRefereeAccessVO> authenticateReferee(@PathVariable("id") String id,
                                                                      @Valid @RequestBody TournamentRefereeAuthReq req) {
        return ApiResponse.ok(tournamentService.authenticateReferee(authGuard.requireUserId(), id, req));
    }

    @GetMapping("/{id}/referees")
    public ApiResponse<List<TournamentRefereeVO>> listReferees(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.listReferees(authGuard.requireUserId(), id));
    }

    @DeleteMapping("/{id}/referees/{userId}")
    public ApiResponse<Void> removeReferee(@PathVariable("id") String id,
                                           @PathVariable("userId") String userId) {
        tournamentService.removeReferee(authGuard.requireUserId(), id, userId);
        return ApiResponse.ok();
    }

    @PostMapping("/{id}/referee-password")
    public ApiResponse<Void> updateRefereePassword(@PathVariable("id") String id,
                                                   @Valid @RequestBody UpdateTournamentRefereePasswordReq req) {
        tournamentService.updateRefereePassword(authGuard.requireUserId(), id, req);
        return ApiResponse.ok();
    }

    @GetMapping("/mine/favorites")
    public ApiResponse<List<Tournament>> listMyFavorites() {
        return ApiResponse.ok(tournamentService.listFavoriteTournaments(authGuard.requireUserId()));
    }

    @GetMapping("/mine/created")
    public ApiResponse<List<Tournament>> listMyCreated() {
        return ApiResponse.ok(tournamentService.listCreatedTournaments(authGuard.requireUserId()));
    }

    @GetMapping("/mine/archived")
    public ApiResponse<List<Tournament>> listMyArchived() {
        return ApiResponse.ok(tournamentService.listArchivedTournaments(authGuard.requireUserId()));
    }
}
