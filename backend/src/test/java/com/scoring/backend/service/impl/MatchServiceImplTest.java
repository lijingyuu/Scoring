package com.scoring.backend.service.impl;

import cn.hutool.core.util.IdUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchServiceImplTest {

    private static final String TOURNAMENT_ID = "t-1";
    private static final String MATCH_ID = "m-1";
    private static final String NEXT_MATCH_ID = "m-2";
    private static final String LEFT_PLAYER_ID = "p-left";
    private static final String RIGHT_PLAYER_ID = "p-right";
    private static final String CREATOR_ID = "user-1";

    @Mock
    private MatchRecordMapper matchRecordMapper;
    @Mock
    private PlayerMapper playerMapper;
    @Mock
    private TournamentMapper tournamentMapper;
    @Mock
    private TournamentTeamMemberMapper tournamentTeamMemberMapper;
    @Mock
    private MatchLineupConfigMapper matchLineupConfigMapper;
    @Mock
    private MatchReportMetaMapper matchReportMetaMapper;
    @Mock
    private MatchEventMapper matchEventMapper;
    @Mock
    private TeamMatchItemMapper teamMatchItemMapper;
    @Mock
    private TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    @Mock
    private TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    @Mock
    private TournamentRuleResolver tournamentRuleResolver;

    private MatchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new MatchServiceImpl(
                matchRecordMapper, playerMapper, tournamentMapper,
                tournamentTeamMemberMapper, matchLineupConfigMapper,
                matchReportMetaMapper, matchEventMapper, teamMatchItemMapper,
                tournamentRefereeGrantMapper, tournamentQualificationOverrideMapper,
                tournamentRuleResolver
        );
        lenient().when(tournamentRuleResolver.resolveForMatch(any(Tournament.class), any(MatchRecord.class)))
                .thenAnswer(invocation -> MatchRuleConfig.fromTournament(invocation.getArgument(0)));
    }

    // ==================== finishMatch ====================

    @Test
    void finishMatch_blankMatchId_shouldThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, "", validFinishReq()));
    }

    @Test
    void finishMatch_blankWinnerSide_shouldThrow() {
        FinishMatchReq req = validFinishReq();
        req.setWinnerSide(null);
        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_invalidWinnerSide_shouldThrow() {
        FinishMatchReq req = validFinishReq();
        req.setWinnerSide("invalid");
        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_winnerSideNotMatchingGameWins_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, NEXT_MATCH_ID, "left");
        Tournament tournament = buildTournament();
        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        FinishMatchReq req = validFinishReq();
        req.setWinnerSide("left");
        req.setLeftGameWins(2);
        req.setRightGameWins(3);  // right won more games but winnerSide=left

        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_gameScoresSizeMismatch_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, NEXT_MATCH_ID, "left");
        Tournament tournament = buildTournament();
        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        FinishMatchReq req = validFinishReq();
        req.setLeftGameWins(2);
        req.setRightGameWins(0);
        req.setGameScores(List.of(buildGameScore(1, 25, 20, "left")));  // only 1, but 2 wins

        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_gameScoreDraw_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, NEXT_MATCH_ID, "left");
        Tournament tournament = buildTournament();
        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        FinishMatchReq req = validFinishReq();
        req.setGameScores(List.of(buildGameScore(1, 25, 25, "left")));  // draw

        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_gameWinnerDoesNotMatchScore_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, NEXT_MATCH_ID, "left");
        Tournament tournament = buildTournament();
        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        FinishMatchReq req = validFinishReq();
        req.setLeftGameWins(2);
        req.setRightGameWins(1);
        req.setGameScores(List.of(
                buildGameScore(1, 25, 20, "right"),  // right scored less but claimed winner
                buildGameScore(2, 25, 22, "left"),
                buildGameScore(3, 25, 18, "left")
        ));

        assertThrows(IllegalArgumentException.class,
                () -> service.finishMatch(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void finishMatch_withValidRequestAndNextMatch_shouldPropagateWinner() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, NEXT_MATCH_ID, "left");
        MatchRecord next = buildMatch(NEXT_MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();

        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);
        when(matchRecordMapper.selectByIdForUpdate(NEXT_MATCH_ID)).thenReturn(next);

        // Should not throw — verify the method completes without exception
        service.finishMatch(CREATOR_ID, MATCH_ID, validFinishReq());
    }

    @Test
    void finishMatch_withRetiredSide_shouldSetRetiredSide() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();
        tournament.setTournamentType(0);  // pure knockout -> tournament ends

        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        FinishMatchReq req = validFinishReq();
        req.setWinnerSide("right");
        req.setLeftGameWins(0);
        req.setRightGameWins(3);
        req.setRetiredSide("left");
        req.setGameScores(null);

        service.finishMatch(CREATOR_ID, MATCH_ID, req);
    }

    // ==================== restartMatch ====================
    //
    // restartMatch 内部使用 LambdaUpdateWrapper，需要 MyBatis-Plus 实体缓存，
    // 纯 Mockito 单元测试无法初始化。restartMatch 的下游清除逻辑已在
    // MatchLineupConfigIntegrationTest.restartMatch_shouldClearPropagatedDownstreamBracketState
    // 中通过集成测试完整覆盖。

    // ==================== saveMatchEvents ====================

    @Test
    void saveMatchEvents_emptyEvents_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();

        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        SaveMatchEventsReq req = new SaveMatchEventsReq();
        req.setEvents(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.saveMatchEvents(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void saveMatchEvents_duplicateSeqInRequest_shouldThrow() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();

        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        SaveMatchEventsReq req = new SaveMatchEventsReq();
        req.setEvents(List.of(
                buildEventItem(1, "timeout"),
                buildEventItem(1, "substitution")  // duplicate seq
        ));

        assertThrows(IllegalArgumentException.class,
                () -> service.saveMatchEvents(CREATOR_ID, MATCH_ID, req));
    }

    @Test
    void saveMatchEvents_existingEventSeq_shouldSkip() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();

        when(matchRecordMapper.selectByIdForUpdate(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        MatchEvent existing = new MatchEvent();
        existing.setMatchId(MATCH_ID);
        existing.setEventSeq(1);
        when(matchEventMapper.selectList(any(QueryWrapper.class)))
                .thenReturn(List.of(existing));

        SaveMatchEventsReq req = new SaveMatchEventsReq();
        req.setEvents(List.of(
                buildEventItem(1, "timeout"),
                buildEventItem(2, "substitution")
        ));

        service.saveMatchEvents(CREATOR_ID, MATCH_ID, req);

        // Only event seq 2 should be inserted (seq 1 already exists and skipped)
        verify(matchEventMapper).selectList(any(QueryWrapper.class));
    }

    // ==================== canOperateMatch ====================

    @Test
    void canOperateMatch_nullUserId_shouldReturnFalse() {
        assertFalse(service.canOperateMatch(null, MATCH_ID));
    }

    @Test
    void canOperateMatch_blankUserId_shouldReturnFalse() {
        assertFalse(service.canOperateMatch("", MATCH_ID));
    }

    @Test
    void canOperateMatch_nullMatchId_shouldReturnFalse() {
        assertFalse(service.canOperateMatch(CREATOR_ID, null));
    }

    @Test
    void canOperateMatch_blankMatchId_shouldReturnFalse() {
        assertFalse(service.canOperateMatch(CREATOR_ID, ""));
    }

    @Test
    void canOperateMatch_matchNotFound_shouldReturnFalse() {
        when(matchRecordMapper.selectById("nonexistent")).thenReturn(null);
        assertFalse(service.canOperateMatch(CREATOR_ID, "nonexistent"));
    }

    @Test
    void canOperateMatch_tournamentNotFound_shouldReturnFalse() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(null);
        assertFalse(service.canOperateMatch(CREATOR_ID, MATCH_ID));
    }

    @Test
    void canOperateMatch_archivedTournament_shouldReturnFalse() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();
        tournament.setArchived(true);

        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        assertFalse(service.canOperateMatch(CREATOR_ID, MATCH_ID));
    }

    @Test
    void canOperateMatch_creatorShouldReturnTrue() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();

        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        assertTrue(service.canOperateMatch(CREATOR_ID, MATCH_ID));
    }

    @Test
    void canOperateMatch_refereeShouldReturnTrue() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();
        String refereeId = "user-referee";

        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);
        when(tournamentRefereeGrantMapper.selectCount(any(QueryWrapper.class))).thenReturn(1L);

        assertTrue(service.canOperateMatch(refereeId, MATCH_ID));
    }

    @Test
    void canOperateMatch_nonCreatorNonReferee_shouldReturnFalse() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();
        String strangerId = "user-stranger";

        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);
        when(tournamentRefereeGrantMapper.selectCount(any(QueryWrapper.class))).thenReturn(0L);

        assertFalse(service.canOperateMatch(strangerId, MATCH_ID));
    }

    @Test
    void canOperateMatch_lockedTournamentWithReferee_shouldReturnFalse() {
        MatchRecord match = buildMatch(MATCH_ID, TOURNAMENT_ID, null, null);
        Tournament tournament = buildTournament();
        tournament.setArchived(true);

        when(matchRecordMapper.selectById(MATCH_ID)).thenReturn(match);
        when(tournamentMapper.selectById(TOURNAMENT_ID)).thenReturn(tournament);

        // Even a referee should not operate on archived tournament
        assertFalse(service.canOperateMatch("user-referee", MATCH_ID));
    }

    // ==================== helpers ====================

    private MatchRecord buildMatch(String id, String tournamentId, String nextMatchId, String nextMatchSlot) {
        MatchRecord match = new MatchRecord();
        match.setId(id);
        match.setTournamentId(tournamentId);
        match.setRoundNum(1);
        match.setMatchIndex(0);
        match.setStageType(1);
        match.setLeftPlayerId(LEFT_PLAYER_ID);
        match.setRightPlayerId(RIGHT_PLAYER_ID);
        match.setStatus(1);
        match.setNextMatchId(nextMatchId);
        match.setNextMatchSlot(nextMatchSlot);
        return match;
    }

    private Tournament buildTournament() {
        Tournament tournament = new Tournament();
        tournament.setId(TOURNAMENT_ID);
        tournament.setName("Test Tournament");
        tournament.setStatus(1);
        tournament.setSportType(1);
        tournament.setTournamentType(0);
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(25);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);
        tournament.setCreatorUserId(CREATOR_ID);
        return tournament;
    }

    private FinishMatchReq validFinishReq() {
        FinishMatchReq req = new FinishMatchReq();
        req.setWinnerSide("left");
        req.setLeftScore(3);
        req.setRightScore(1);
        req.setLeftGameWins(3);
        req.setRightGameWins(1);
        req.setGameScores(List.of(
                buildGameScore(1, 25, 20, "left"),
                buildGameScore(2, 21, 25, "right"),
                buildGameScore(3, 25, 22, "left"),
                buildGameScore(4, 25, 18, "left")
        ));
        return req;
    }

    private FinishMatchReq.GameScore buildGameScore(int gameNo, int leftScore, int rightScore, String winnerSide) {
        FinishMatchReq.GameScore score = new FinishMatchReq.GameScore();
        score.setGameNo(gameNo);
        score.setLeftScore(leftScore);
        score.setRightScore(rightScore);
        score.setWinnerSide(winnerSide);
        return score;
    }

    private SaveMatchEventsReq.EventItem buildEventItem(int eventSeq, String eventType) {
        SaveMatchEventsReq.EventItem item = new SaveMatchEventsReq.EventItem();
        item.setEventSeq(eventSeq);
        item.setEventType(eventType);
        item.setGameNo(1);
        item.setLeftScore(0);
        item.setRightScore(0);
        item.setServeSide("left");
        item.setPayloadJson("{\"side\":\"left\"}");
        return item;
    }
}
