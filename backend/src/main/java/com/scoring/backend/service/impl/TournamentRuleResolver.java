package com.scoring.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class TournamentRuleResolver {
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;

    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final MatchRecordMapper matchRecordMapper;

    public TournamentRuleResolver(TournamentRoundRuleMapper tournamentRoundRuleMapper,
                                  TeamMatchItemMapper teamMatchItemMapper,
                                  MatchRecordMapper matchRecordMapper) {
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.matchRecordMapper = matchRecordMapper;
    }

    public MatchRuleConfig resolveForMatch(Tournament tournament, MatchRecord match) {
        if (tournament == null || match == null || !Boolean.TRUE.equals(tournament.getRoundRuleEnabled())) {
            return MatchRuleConfig.fromTournament(tournament);
        }

        MatchRecord scopeMatch = resolveScopeMatch(match);
        Integer stageType = scopeMatch.getStageType();
        Integer roundNum = Integer.valueOf(STAGE_GROUP).equals(stageType) ? 0 : scopeMatch.getRoundNum();
        if (!Integer.valueOf(STAGE_GROUP).equals(stageType) && !Integer.valueOf(STAGE_KNOCKOUT).equals(stageType)) {
            return MatchRuleConfig.fromTournament(tournament);
        }

        List<TournamentRoundRule> rules = tournamentRoundRuleMapper.selectList(new QueryWrapper<TournamentRoundRule>()
                .eq("tournament_id", tournament.getId()));
        for (TournamentRoundRule rule : rules) {
            if (stageType.equals(rule.getStageType()) && roundNum.equals(rule.getRoundNum())) {
                return MatchRuleConfig.fromRoundRule(rule);
            }
        }
        return MatchRuleConfig.fromTournament(tournament);
    }

    private MatchRecord resolveScopeMatch(MatchRecord match) {
        if (!Integer.valueOf(STAGE_TEAM_CHILD).equals(match.getStageType())) {
            return match;
        }
        TeamMatchItem item = teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("child_match_id", match.getId())
                .last("LIMIT 1"));
        if (item == null || item.getMatchId() == null) {
            return match;
        }
        MatchRecord parent = matchRecordMapper.selectById(item.getMatchId());
        return parent == null ? match : parent;
    }
}
