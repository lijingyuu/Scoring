package com.scoring.backend.service.match;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.ConflictException;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import org.springframework.stereotype.Service;

/**
 * 比赛维度操作权限守卫：创建者 / 赛务管理员(ADMIN) / 执裁裁判(SCORER) 的判定与可读性校验。
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
        throw new IllegalArgumentException("赛事已归档，仅创建者可见");
    }

    /**
     * 重开比赛守卫（双级口令模型）：
     * - 未完赛（status &lt; 2，含未开赛/进行中）：创建者 / ADMIN / SCORER 均可重开，保留现场记分员纠错能力；
     * - 已完赛（status = 2 正常结束 / 3 判定结束）：仅创建者或 ADMIN（赛务编排管理员）可重开，
     *   防止普通执裁裁判抹掉已推进晋级的完赛结果。
     */
    public Tournament requireMatchRestartOperator(String userId, MatchRecord match) {
        Tournament tournament = requireTournament(match.getTournamentId());
        if (isCreator(userId, tournament)) {
            return tournament;
        }
        boolean finished = isFinishedMatch(match);
        if (!finished && hasGrantOfRole(userId, tournament.getId(), null)) {
            return tournament;
        }
        if (finished && hasGrantOfRole(userId, tournament.getId(), TournamentRefereeGrant.GRANT_ROLE_ADMIN)) {
            return tournament;
        }
        if (finished) {
            throw new IllegalArgumentException("已完赛的比赛仅赛务管理员或创建者可重开");
        }
        throw new IllegalArgumentException("only creator or referee can modify this match");
    }

    /** 当前用户可否重开该场（用于 VO 下发 canRestartMatch，口径与 requireMatchRestartOperator 一致） */
    public boolean canRestartMatch(String userId, MatchRecord match, Tournament tournament) {
        if (match == null || tournament == null) {
            return false;
        }
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            return false;
        }
        if (isCreator(userId, tournament)) {
            return true;
        }
        if (isFinishedMatch(match)) {
            return hasGrantOfRole(userId, tournament.getId(), TournamentRefereeGrant.GRANT_ROLE_ADMIN);
        }
        return hasGrantOfRole(userId, tournament.getId(), null);
    }

    /** 创建者或任一角色裁判（ADMIN/SCORER）：单场记分/阵容/战报操作口径，保持既有行为 */
    public boolean isMatchOperator(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        return tournament != null && isOperator(userId, tournament);
    }

    private boolean isFinishedMatch(MatchRecord match) {
        return Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus());
    }

    private Tournament requireTournament(String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            // 与 TournamentAccessGuard.requireNotArchived 同口径：归档写操作返回 409
            throw new ConflictException("赛事已归档，不可再操作");
        }
        return tournament;
    }

    private boolean isCreator(String userId, Tournament tournament) {
        return StrUtil.equals(userId, tournament.getCreatorUserId());
    }

    private boolean isOperator(String userId, Tournament tournament) {
        return isCreator(userId, tournament)
                || hasGrantOfRole(userId, tournament.getId(), null);
    }

    private boolean hasGrantOfRole(String userId, String tournamentId, String grantRole) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }
        QueryWrapper<TournamentRefereeGrant> wrapper = new QueryWrapper<TournamentRefereeGrant>()
                .eq("tournament_id", tournamentId)
                .eq("user_id", userId);
        if (StrUtil.isNotBlank(grantRole)) {
            wrapper.eq("grant_role", grantRole);
        }
        return tournamentRefereeGrantMapper.selectCount(wrapper) > 0;
    }
}
