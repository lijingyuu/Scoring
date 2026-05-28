package com.scoring.backend.service;

import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;

import java.util.List;

public interface TournamentService {

    String createTournament(String creatorUserId, CreateTournamentReq req);

    List<Tournament> listTournaments(String currentUserId, String keyword);

    TournamentDetailVO getTournamentDetail(String tournamentId, String currentUserId);

    List<Tournament> listFavoriteTournaments(String userId);

    List<Tournament> listCreatedTournaments(String userId);

    void favoriteTournament(String userId, String tournamentId);

    void unfavoriteTournament(String userId, String tournamentId);

    TournamentBracketVO getBracket(String tournamentId);

    TournamentGroupsVO getGroups(String tournamentId);

    GroupStandingsVO getGroupStandings(String tournamentId);

    void generateKnockout(String userId, String tournamentId);
}
