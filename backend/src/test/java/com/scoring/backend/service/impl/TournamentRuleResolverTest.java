package com.scoring.backend.service.impl;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class TournamentRuleResolverTest {

    @Mock
    private TournamentRoundRuleMapper tournamentRoundRuleMapper;

    @Mock
    private TeamMatchItemMapper teamMatchItemMapper;

    @Mock
    private MatchRecordMapper matchRecordMapper;

    @Test
    void resolveForMatch_shouldFallbackToTournamentRuleWhenKnockoutRoundNumMissing() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");
        tournament.setRoundRuleEnabled(true);
        tournament.setBestOf(3);
        tournament.setGamesToWin(2);
        tournament.setPointsToWin(21);
        tournament.setEnableDeuce(true);
        tournament.setCapPoint(30);

        MatchRecord match = new MatchRecord();
        match.setStageType(1);
        match.setRoundNum(null);

        TournamentRuleResolver resolver = new TournamentRuleResolver(
                tournamentRoundRuleMapper,
                teamMatchItemMapper,
                matchRecordMapper);

        MatchRuleConfig rule = resolver.resolveForMatch(tournament, match);

        assertEquals(3, rule.getBestOf());
        assertEquals(2, rule.getGamesToWin());
        assertEquals(21, rule.getPointsToWin());
        verifyNoInteractions(tournamentRoundRuleMapper);
    }
}
