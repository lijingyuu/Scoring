package com.scoring.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentRoundRule;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentRoundRuleMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 比赛生效规则解析器。解析链（优先级从高到低）：
 * 1. 季军赛规则（组别上的 third_place_* 字段）
 * 2. 轮次规则（tournament_round_rule，按 division_id + stage_type + round_num）
 * 3. 组别默认规则（tournament_division）
 * <p>
 * 对外签名保持 (Tournament, MatchRecord)：组别通过 match.division_id 加载；
 * 若比赛缺少组别（理论上 V23 之后不会发生），退回赛事级规则兜底。
 */
@Component
public class TournamentRuleResolver {
    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;
    private static final int MATCH_ROLE_THIRD_PLACE = 1;

    private final TournamentRoundRuleMapper tournamentRoundRuleMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentDivisionMapper tournamentDivisionMapper;

    public TournamentRuleResolver(TournamentRoundRuleMapper tournamentRoundRuleMapper,
                                  TeamMatchItemMapper teamMatchItemMapper,
                                  MatchRecordMapper matchRecordMapper,
                                  TournamentDivisionMapper tournamentDivisionMapper) {
        this.tournamentRoundRuleMapper = tournamentRoundRuleMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
    }

    public MatchRuleConfig resolveForMatch(Tournament tournament, MatchRecord match) {
        if (match == null) {
            return MatchRuleConfig.fromDivision(null);
        }

        TournamentDivision division = loadDivisionForMatch(match);
        if (division == null) {
            return MatchRuleConfig.fromTournament(tournament);
        }

        MatchRecord scopeMatch = resolveScopeMatch(match);
        if (Integer.valueOf(MATCH_ROLE_THIRD_PLACE).equals(scopeMatch.getMatchRole())) {
            return MatchRuleConfig.fromThirdPlace(division);
        }
        if (!Boolean.TRUE.equals(division.getRoundRuleEnabled())) {
            return MatchRuleConfig.fromDivision(division);
        }
        Integer stageType = scopeMatch.getStageType();
        Integer roundNum = Integer.valueOf(STAGE_GROUP).equals(stageType) ? Integer.valueOf(0) : scopeMatch.getRoundNum();
        if (!Integer.valueOf(STAGE_GROUP).equals(stageType) && !Integer.valueOf(STAGE_KNOCKOUT).equals(stageType)) {
            return MatchRuleConfig.fromDivision(division);
        }
        if (roundNum == null) {
            return MatchRuleConfig.fromDivision(division);
        }

        List<TournamentRoundRule> rules = tournamentRoundRuleMapper.selectList(new QueryWrapper<TournamentRoundRule>()
                .eq("division_id", division.getId()));
        for (TournamentRoundRule rule : rules) {
            if (stageType.equals(rule.getStageType()) && roundNum.equals(rule.getRoundNum())) {
                return MatchRuleConfig.fromRoundRule(rule);
            }
        }
        return MatchRuleConfig.fromDivision(division);
    }

    private TournamentDivision loadDivisionForMatch(MatchRecord match) {
        String divisionId = match.getDivisionId();
        if (divisionId == null || divisionId.isEmpty()) {
            return null;
        }
        return tournamentDivisionMapper.selectById(divisionId);
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
