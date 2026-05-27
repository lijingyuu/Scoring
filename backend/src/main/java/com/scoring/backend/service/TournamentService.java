package com.scoring.backend.service;

import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;

import java.util.List;

public interface TournamentService {

    String createTournament(CreateTournamentReq req);

    List<Tournament> listTournaments();

    TournamentBracketVO getBracket(String tournamentId);

    TournamentGroupsVO getGroups(String tournamentId);

    GroupStandingsVO getGroupStandings(String tournamentId);

    void generateKnockout(String tournamentId);
}
