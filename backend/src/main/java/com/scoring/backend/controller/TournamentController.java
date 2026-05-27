package com.scoring.backend.controller;

import com.scoring.backend.common.ApiResponse;
import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.service.TournamentService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
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

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping
    public ApiResponse<List<Tournament>> listTournaments() {
        return ApiResponse.ok(tournamentService.listTournaments());
    }

    @PostMapping
    public ApiResponse<Map<String, String>> createTournament(@Valid @RequestBody CreateTournamentReq req) {
        String tournamentId = tournamentService.createTournament(req);
        Map<String, String> data = new HashMap<>();
        data.put("tournamentId", tournamentId);
        return ApiResponse.ok(data);
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

    @PostMapping("/{id}/generate-knockout")
    public ApiResponse<Void> generateKnockout(@PathVariable("id") String id) {
        tournamentService.generateKnockout(id);
        return ApiResponse.ok();
    }
}
