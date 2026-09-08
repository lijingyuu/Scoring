package com.scoring.backend.service.match;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import org.springframework.stereotype.Service;

/**
 * 比赛维度操作权限守卫：创建者 / 授权裁判 的判定与可读性校验。
 * 无状态查询组件，不承担事务边界，仅负责"谁有权动这场比赛"的裁决。
 */
@Service
public class MatchAccessGuard {

    private final TournamentMapper tournamentMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    public MatchAccessGuard(TournamentMapper tournamentMapper,
                            TournamentRefereeGrantMapper tournamentRefereeGrantMapper) {
        this.tournamentMapper = tournamentMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
    }

    public Tournament requireMatchOperator(String userId, String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (isOperator(userId, tournament)) {
            return tournament;
        }
        throw new IllegalArgumentException("only creator or referee can modify this match");
    }

    public Tournament requireReportOperator(String userId, String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        if (isOperator(userId, tournament)) {
            return tournament;
        }
        throw new IllegalArgumentException("只有赛事创建者或裁判可以修改战报");
    }

    public Tournament requireMatchReadable(String userId, MatchRecord match) {
        Tournament tournament = tournamentMapper.selectById(match.getTournamentId());
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + match.getTournamentId());
        }
        if (!Boolean.TRUE.equals(tournament.getArchived())) {
            return tournament;
        }
        if (StrUtil.isNotBlank(userId) && StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return tournament;
        }
        throw new IllegalArgumentException("archived tournament is only visible to creator");
    }

    private Tournament requireTournament(String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            throw new IllegalStateException("archived tournament is read-only");
        }
        return tournament;
    }

    private boolean isOperator(String userId, Tournament tournament) {
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return true;
        }
        Long refereeCount = tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournament.getId())
                        .eq("user_id", userId)
        );
        return refereeCount > 0;
    }
}
