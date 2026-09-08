package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.TournamentMatchAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeConfigMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.UserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 裁判认证与授权服务：裁判密码校验、授权/移除/列表、比赛操作权限判定。
 */
@Service
public class TournamentRefereeService {

    private static final String REFEREE_PASSWORD_PATTERN = "^\\d{8}$";
    private static final String REFEREE_HASH_SALT = "tournament_referee_password";
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TournamentMapper tournamentMapper;
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final UserMapper userMapper;
    private final TournamentAccessGuard accessGuard;

    public TournamentRefereeService(TournamentMapper tournamentMapper,
                                    TournamentRefereeConfigMapper tournamentRefereeConfigMapper,
                                    TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                    UserMapper userMapper,
                                    TournamentAccessGuard accessGuard) {
        this.tournamentMapper = tournamentMapper;
        this.tournamentRefereeConfigMapper = tournamentRefereeConfigMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.userMapper = userMapper;
        this.accessGuard = accessGuard;
    }

    @Transactional(rollbackFor = Exception.class)
    public TournamentRefereeAccessVO authenticateReferee(String userId, String tournamentId, TournamentRefereeAuthReq req) {
        accessGuard.requireCompletedProfile(userId);
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireNotArchived(tournament);

        TournamentRefereeConfig config = tournamentRefereeConfigMapper.selectOne(
                new QueryWrapper<TournamentRefereeConfig>()
                        .eq("tournament_id", tournamentId)
        );

        if (config == null) {
            throw new IllegalArgumentException("该赛事未设置裁判密码");
        }

        if (!verifyPassword(req.getPassword(), config.getPasswordHash())) {
            throw new IllegalArgumentException("裁判密码错误");
        }

        // 检查是否已授权
        TournamentRefereeGrant existing = tournamentRefereeGrantMapper.selectOne(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        );

        if (existing == null) {
            TournamentRefereeGrant grant = new TournamentRefereeGrant();
            grant.setTournamentId(tournamentId);
            grant.setUserId(userId);
            tournamentRefereeGrantMapper.insert(grant);
        }

        TournamentRefereeAccessVO vo = new TournamentRefereeAccessVO();
        vo.setGranted(true);
        vo.setReferees(buildRefereeVOList(tournamentId));
        return vo;
    }

    public List<TournamentRefereeVO> listReferees(String userId, String tournamentId) {
        accessGuard.requireTournament(tournamentId);
        accessGuard.requireCreatorOrReferee(userId, tournamentId);
        return buildRefereeVOList(tournamentId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void removeReferee(String userId, String tournamentId, String refereeUserId) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireNotArchived(tournament);

        // 只有创建者可以移除裁判
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以移除裁判");
        }

        // 不能移除创建者自己（虽然创建者不会出现在裁判列表中）
        if (StrUtil.equals(refereeUserId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("不能移除赛事创建者");
        }

        tournamentRefereeGrantMapper.delete(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", refereeUserId)
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRefereePassword(String userId, String tournamentId, UpdateTournamentRefereePasswordReq req) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireNotArchived(tournament);

        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以修改裁判密码");
        }

        validateRefereePassword(req.getPassword());

        TournamentRefereeConfig config = tournamentRefereeConfigMapper.selectOne(
                new QueryWrapper<TournamentRefereeConfig>()
                        .eq("tournament_id", tournamentId)
        );

        if (config == null) {
            config = new TournamentRefereeConfig();
            config.setTournamentId(tournamentId);
            config.setPasswordHash(hashPassword(req.getPassword()));
            tournamentRefereeConfigMapper.insert(config);
        } else {
            config.setPasswordHash(hashPassword(req.getPassword()));
            tournamentRefereeConfigMapper.updateById(config);
        }
    }

    public boolean canOperateVolleyballMatch(String userId, String tournamentId) {
        // This method is used as generic tournament match-operation access; the legacy name is kept for compatibility.
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(tournamentId)) {
            return false;
        }

        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null || accessGuard.isArchived(tournament)) {
            return false;
        }

        // 创建者永远可以操作
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return true;
        }

        // 检查是否为已授权裁判
        return tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        ) > 0;
    }

    // ======================== 裁判辅助方法 ========================

    private void validateRefereePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("裁判密码不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("裁判密码必须为8位数字");
        }
    }

    private String hashPassword(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword + REFEREE_HASH_SALT);
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        return hashPassword(rawPassword).equals(storedHash);
    }

    public void fillMatchAccess(TournamentMatchAccessVO vo, Tournament tournament, String currentUserId) {
        if (vo == null || tournament == null) {
            return;
        }

        boolean isCreator = StrUtil.equals(currentUserId, tournament.getCreatorUserId());
        if (accessGuard.isArchived(tournament)) {
            vo.setRefereeGranted(false);
            vo.setCanOperateMatches(false);
            vo.setCanManageReferees(false);
            return;
        }

        boolean isReferee = StrUtil.isNotBlank(currentUserId)
                && tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournament.getId())
                        .eq("user_id", currentUserId)
        ) > 0;

        vo.setRefereeGranted(isReferee);
        vo.setCanOperateMatches(isCreator || isReferee);
        vo.setCanManageReferees(isCreator);
    }

    private List<TournamentRefereeVO> buildRefereeVOList(String tournamentId) {
        List<TournamentRefereeGrant> grants = tournamentRefereeGrantMapper.selectList(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .orderByAsc("create_time")
        );

        if (CollUtil.isEmpty(grants)) {
            return List.of();
        }

        List<String> userIds = grants.stream()
                .map(TournamentRefereeGrant::getUserId)
                .collect(Collectors.toList());
        List<User> users = userMapper.selectList(
                new QueryWrapper<User>().in("id", userIds)
        );
        Map<String, User> userMap = users.stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        return grants.stream().map(grant -> {
            TournamentRefereeVO vo = new TournamentRefereeVO();
            vo.setUserId(grant.getUserId());
            User user = userMap.get(grant.getUserId());
            vo.setNickname(user == null ? "" : user.getNickname());
            vo.setAvatarUrl(user == null ? "" : user.getAvatarUrl());
            vo.setGrantedAt(grant.getCreateTime() == null ? "" : grant.getCreateTime().format(DATETIME_FORMATTER));
            return vo;
        }).collect(Collectors.toList());
    }
}
