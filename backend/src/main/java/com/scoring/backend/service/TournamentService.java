package com.scoring.backend.service;

import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.TournamentBracketVO;

import java.util.List;

public interface TournamentService {

    String createTournament(CreateTournamentReq req);

    List<Tournament> listTournaments();

    TournamentBracketVO getBracket(String tournamentId);
}
