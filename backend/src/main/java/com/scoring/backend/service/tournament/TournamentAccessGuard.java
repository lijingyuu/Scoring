package com.scoring.backend.service.tournament;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.ConflictException;
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
        throw new IllegalArgumentException("赛事已归档，仅创建者可见");
    }

    public void requireNotArchived(Tournament tournament) {
        if (isArchived(tournament)) {
            // 归档是调用方可预期的业务状态，用 409 而非 IllegalStateException（会被兜底成 500）
            throw new ConflictException("赛事已归档，不可再操作");
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

    /**
     * 赛务编排级守卫：仅创建者或 ADMIN 授权（赛务编排密码认证）可继续。
     * 用于出线资格覆盖、淘汰赛预览与生成等编排决策操作；SCORER（现场执裁）不在放行范围。
     */
    public void requireCreatorOrAdmin(String userId, String tournamentId) {
        Tournament tournament = requireTournament(tournamentId);
        requireNotArchived(tournament);
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return;
        }
        if (hasAdminGrant(userId, tournamentId)) {
            return;
        }
        throw new IllegalArgumentException("仅创建者或赛务管理员可操作");
    }

    public boolean hasRefereeGrant(String userId, String tournamentId) {
        return hasGrantOfRole(userId, tournamentId, null);
    }

    /** 是否持有 ADMIN（赛务编排管理员）授权；未启用编排密码的历史赛事恒为 false */
    public boolean hasAdminGrant(String userId, String tournamentId) {
        return hasGrantOfRole(userId, tournamentId, TournamentRefereeGrant.GRANT_ROLE_ADMIN);
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

    /** 当前用户的裁判授权角色：null（未认证）/ ADMIN / SCORER */
    public String findRefereeRole(String userId, String tournamentId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return null;
        }
        TournamentRefereeGrant grant = tournamentRefereeGrantMapper.selectOne(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        );
        if (grant == null) {
            return null;
        }
        return StrUtil.blankToDefault(grant.getGrantRole(), TournamentRefereeGrant.GRANT_ROLE_SCORER);
    }
}
