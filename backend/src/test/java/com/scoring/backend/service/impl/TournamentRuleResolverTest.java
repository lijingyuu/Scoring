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

    /**
     * 审查 §5.4-⑦：同一轮次上季军赛规则与分轮规则同时存在时，季军赛规则优先
     * （解析链第 1 优先级），分轮规则不参与——季军赛分支在查询 round_rule 之前就返回。
     */
    @Test
    void resolveForMatch_thirdPlaceRuleShouldWinOverRoundRuleAtSameRound() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");

        MatchRecord match = new MatchRecord();
        match.setDivisionId("d-1");
        match.setStageType(1);
        match.setRoundNum(1);
        match.setMatchRole(1); // 季军赛

        TournamentDivision division = division("d-1");
        division.setThirdPlaceEnabled(true);
        division.setThirdPlaceBestOf(1);
        division.setThirdPlaceGamesToWin(1);
        division.setThirdPlacePointsToWin(11);
        division.setThirdPlaceEnableDeuce(false);
        division.setThirdPlaceCapPoint(15);
        when(tournamentDivisionMapper.selectById("d-1")).thenReturn(division);

        MatchRuleConfig rule = resolver().resolveForMatch(tournament, match);

        assertEquals(1, rule.getBestOf());
        assertEquals(1, rule.getGamesToWin());
        assertEquals(11, rule.getPointsToWin());
        assertEquals(Boolean.FALSE, rule.getEnableDeuce());
        assertEquals(15, rule.getCapPoint());
        verifyNoInteractions(tournamentRoundRuleMapper);
    }

    /**
     * 审查 §5.4-⑦ 对照：组别配置了季军赛规则，不影响普通淘汰场次继续走分轮规则——
     * 半决赛轮（季军赛的挂载轮）的普通场次按 round_rule 生效。
     */
    @Test
    void resolveForMatch_roundRuleShouldStillApplyToNormalMatchesWhenThirdPlaceConfigured() {
        Tournament tournament = new Tournament();
        tournament.setId("t-1");

        MatchRecord match = new MatchRecord();
        match.setDivisionId("d-1");
        match.setStageType(1);
        match.setRoundNum(1); // 半决赛：与季军赛同轮，但 matchRole 为空

        TournamentDivision division = division("d-1");
        division.setThirdPlaceEnabled(true);
        division.setThirdPlaceBestOf(1);
        division.setThirdPlacePointsToWin(11);
        when(tournamentDivisionMapper.selectById("d-1")).thenReturn(division);
        com.scoring.backend.domain.entity.TournamentRoundRule roundRule =
                new com.scoring.backend.domain.entity.TournamentRoundRule();
        roundRule.setDivisionId("d-1");
        roundRule.setStageType(1);
        roundRule.setRoundNum(1);
        roundRule.setBestOf(5);
        roundRule.setGamesToWin(3);
        roundRule.setPointsToWin(15);
        roundRule.setEnableDeuce(true);
        roundRule.setCapPoint(20);
        when(tournamentRoundRuleMapper.selectList(any(com.baomidou.mybatisplus.core.conditions.Wrapper.class)))
                .thenReturn(java.util.List.of(roundRule));

        MatchRuleConfig rule = resolver().resolveForMatch(tournament, match);

        assertEquals(5, rule.getBestOf());
        assertEquals(3, rule.getGamesToWin());
        assertEquals(15, rule.getPointsToWin());
        assertEquals(20, rule.getCapPoint());
    }
}
