package com.scoring.backend.service.tournament;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;

/**
 * 赛事维度访问守卫：存在性、归档只读、创建者/裁判权限、资料完备性。
 * 无状态查询组件，不承担事务边界。
 */
@Service
public class TournamentAccessGuard {

    private final TournamentMapper tournamentMapper;
    private final UserMapper userMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    public TournamentAccessGuard(TournamentMapper tournamentMapper,
                                 UserMapper userMapper,
                                 TournamentRefereeGrantMapper tournamentRefereeGrantMapper) {
        this.tournamentMapper = tournamentMapper;
        this.userMapper = userMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
    }

    public Tournament requireTournament(String tournamentId) {
        if (StrUtil.isBlank(tournamentId)) {
            throw new IllegalArgumentException("tournamentId cannot be blank");
        }
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        return tournament;
    }

    public boolean isArchived(Tournament tournament) {
        return tournament != null && Boolean.TRUE.equals(tournament.getArchived());
    }

    public void requireArchivedReadable(Tournament tournament, String currentUserId) {
        if (!isArchived(tournament)) {
            return;
        }
        if (StrUtil.isNotBlank(currentUserId) && StrUtil.equals(currentUserId, tournament.getCreatorUserId())) {
            return;
        }
        throw new IllegalArgumentException("archived tournament is only visible to creator");
    }

    public void requireNotArchived(Tournament tournament) {
        if (isArchived(tournament)) {
            throw new IllegalStateException("archived tournament is read-only");
        }
    }

    public void requireCreator(String userId, Tournament tournament) {
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("only creator can operate this tournament");
        }
    }

    public void requireCompletedProfile(String userId) {
        if (StrUtil.isBlank(userId)) {
            throw new IllegalArgumentException("请先登录");
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }
        if (!Boolean.TRUE.equals(user.getProfileCompleted())) {
            throw new IllegalArgumentException("请先完善资料后再操作");
        }
    }

    public void requireCreatorOrReferee(String userId, String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        requireNotArchived(tournament);
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return;
        }
        if (hasRefereeGrant(userId, tournamentId)) {
            return;
        }
        throw new IllegalArgumentException("仅创建者或裁判可查看");
    }

    public boolean hasRefereeGrant(String userId, String tournamentId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }
        return tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        ) > 0;
    }
}
