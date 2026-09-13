package com.scoring.backend.service.impl;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TournamentRuleResolverTest {

    @Mock
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Mock
    private TeamMatchItemMapper teamMatchItemMapper;

    @Mock
    private MatchRecordMapper matchRecordMapper;

    @Mock
    private TournamentDivisionMapper tournamentDivisionMapper;

    private TournamentRuleResolver resolver() {
        return new TournamentRuleResolver(
                tournamentRoundRuleMapper,
                teamMatchItemMapper,
                matchRecordMapper,
                tournamentDivisionMapper);
    }

    private TournamentDivision division(String id) {
        TournamentDivision division = new TournamentDivision();
        division.setId(id);
        division.setTournamentId("t-1");
        division.setRoundRuleEnabled(true);
        division.setBestOf(3);
        division.setGamesToWin(2);
        division.setPointsToWin(21);
        division.setEnableDeuce(true);
        division.setCapPoint(30);
        return division;
    }

    @Test
    void resolveForMatch_shouldFallbackToDivisionRuleWhenKnockoutRoundNumMissing() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");

        MatchRecord match = new MatchRecord();
        match.setDivisionId("d-1");
        match.setStageType(1);
        match.setRoundNum(null);

        when(tournamentDivisionMapper.selectById("d-1")).thenReturn(division("d-1"));

        MatchRuleConfig rule = resolver().resolveForMatch(tournament, match);

        assertEquals(3, rule.getBestOf());
        assertEquals(2, rule.getGamesToWin());
        assertEquals(21, rule.getPointsToWin());
        verifyNoInteractions(tournamentRoundRuleMapper);
    }

    @Test
    void resolveForMatch_shouldFallbackToTournamentRuleWhenDivisionMissing() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");
        tournament.setBestOf(5);
        tournament.setGamesToWin(3);
        tournament.setPointsToWin(15);
        tournament.setEnableDeuce(false);
        tournament.setCapPoint(21);

        MatchRecord match = new MatchRecord();
        match.setStageType(1);
        match.setRoundNum(null);

        MatchRuleConfig rule = resolver().resolveForMatch(tournament, match);

        assertEquals(5, rule.getBestOf());
        assertEquals(3, rule.getGamesToWin());
        assertEquals(15, rule.getPointsToWin());
        verifyNoInteractions(tournamentRoundRuleMapper);
    }

    @Test
    void resolveForMatch_shouldUseDivisionRoundRule() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");

        MatchRecord match = new MatchRecord();
        match.setDivisionId("d-1");
        match.setStageType(1);
        match.setRoundNum(2);

        when(tournamentDivisionMapper.selectById("d-1")).thenReturn(division("d-1"));
        com.scoring.backend.domain.entity.TournamentRoundRule roundRule =
                new com.scoring.backend.domain.entity.TournamentRoundRule();
        roundRule.setDivisionId("d-1");
        roundRule.setStageType(1);
        roundRule.setRoundNum(2);
        roundRule.setBestOf(1);
        roundRule.setGamesToWin(1);
        roundRule.setPointsToWin(11);
        roundRule.setEnableDeuce(true);
        roundRule.setCapPoint(15);
        when(tournamentRoundRuleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(java.util.List.of(roundRule));

        MatchRuleConfig rule = resolver().resolveForMatch(tournament, match);

        assertEquals(1, rule.getBestOf());
        assertEquals(11, rule.getPointsToWin());
    }
}
