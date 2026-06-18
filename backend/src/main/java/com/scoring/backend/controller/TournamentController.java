package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TournamentTeamsVO;
import com.scoring.backend.security.AuthContext;
import com.scoring.backend.security.AuthGuard;
import com.scoring.backend.service.TournamentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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
        return ApiResponse.ok(tournamentService.getBracket(id));
    }

    @GetMapping("/{id}/groups")
    public ApiResponse<TournamentGroupsVO> getGroups(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getGroups(id));
    }

    @GetMapping("/{id}/group-standings")
    public ApiResponse<GroupStandingsVO> getGroupStandings(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getGroupStandings(id));
    }

    @GetMapping("/{id}/teams")
    public ApiResponse<TournamentTeamsVO> getTeams(@PathVariable("id") String id) {
        return ApiResponse.ok(tournamentService.getTeams(id));
    }

    @PostMapping("/{id}/generate-knockout")
    public ApiResponse<Void> generateKnockout(@PathVariable("id") String id) {
        tournamentService.generateKnockout(authGuard.requireUserId(), id);
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
}
