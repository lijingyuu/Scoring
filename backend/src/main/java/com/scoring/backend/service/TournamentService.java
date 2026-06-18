package com.scoring.backend.service;

import com.scoring.backend.domain.dto.CreateTournamentReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import com.scoring.backend.domain.vo.TournamentDetailVO;
import com.scoring.backend.domain.vo.TournamentBracketVO;
import com.scoring.backend.domain.vo.TournamentGroupsVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.domain.vo.TournamentTeamsVO;

import java.util.List;

public interface TournamentService {

    String createTournament(String creatorUserId, CreateTournamentReq req);

    List<Tournament> listTournaments(String currentUserId, String keyword);

    TournamentDetailVO getTournamentDetail(String tournamentId, String currentUserId);

    List<Tournament> listFavoriteTournaments(String userId);

    List<Tournament> listCreatedTournaments(String userId);

    void favoriteTournament(String userId, String tournamentId);

    void unfavoriteTournament(String userId, String tournamentId);

    TournamentBracketVO getBracket(String tournamentId, String currentUserId);

    TournamentGroupsVO getGroups(String tournamentId, String currentUserId);

    GroupStandingsVO getGroupStandings(String tournamentId);

    TournamentTeamsVO getTeams(String tournamentId);

    void generateKnockout(String userId, String tournamentId);

    TournamentRefereeAccessVO authenticateReferee(String userId, String tournamentId, TournamentRefereeAuthReq req);

    List<TournamentRefereeVO> listReferees(String userId, String tournamentId);

    void removeReferee(String userId, String tournamentId, String refereeUserId);

    void updateRefereePassword(String userId, String tournamentId, UpdateTournamentRefereePasswordReq req);

    boolean canOperateVolleyballMatch(String userId, String tournamentId);
}
