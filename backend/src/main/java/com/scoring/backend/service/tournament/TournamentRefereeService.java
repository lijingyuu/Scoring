package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.DuplicateKeySupport;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 裁判认证与授权服务：裁判密码校验、授权/移除/列表、比赛操作权限判定。
 */
@Service
public class TournamentRefereeService {

    private static final String REFEREE_PASSWORD_PATTERN = "^\\d{8,}$";
    private static final String LEGACY_REFEREE_HASH_SALT = "tournament_referee_password";
    private static final int REFEREE_MAX_FAILURES = 5;
    private static final Duration REFEREE_FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final TournamentMapper tournamentMapper;
    private final TournamentRefereeConfigMapper tournamentRefereeConfigMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final UserMapper userMapper;
    private final TournamentAccessGuard accessGuard;
    /** 密码失败计数/锁定态：按 (tournamentId, userId) 维度内存持有，进程重启即清零（与 RequestRateLimiter 同模式） */
    private final Map<String, FailureRecord> passwordFailures = new ConcurrentHashMap<>();

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

        String failureKey = tournamentId + ":" + userId;
        requireNotLocked(failureKey);
        if (!verifyAndUpgradePassword(req.getPassword(), config)) {
            recordFailure(failureKey);
            throw new IllegalArgumentException("裁判密码错误");
        }
        passwordFailures.remove(failureKey);

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
            try {
                tournamentRefereeGrantMapper.insert(grant);
            } catch (RuntimeException ex) {
                // 并发认证下唯一键兜底：另一请求已完成授权，本次按已授权成功返回
                if (!DuplicateKeySupport.isDuplicateKey(ex)) {
                    throw ex;
                }
            }
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
            // 只更新变更字段并显式刷新 update_time：整实体回写会携带旧 update_time，抑制列的 ON UPDATE（update_time 停摆问题）
            TournamentRefereeConfig update = new TournamentRefereeConfig();
            update.setId(config.getId());
            update.setPasswordHash(hashPassword(req.getPassword()));
            update.setUpdateTime(LocalDateTime.now());
            tournamentRefereeConfigMapper.updateById(update);
        }
    }

    // ======================== 裁判辅助方法 ========================

    private void validateRefereePassword(String password) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException("裁判密码不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException("裁判密码必须不少于8位数字");
        }
    }

    /** 新密码用 BCrypt（每条记录随机盐）存储，与用户密码同一套工具。 */
    private String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 校验裁判密码：库中为 BCrypt 走 BCrypt；旧库为单轮 SHA256+静态盐，
     * 比对成功后立即改写为 BCrypt（一次透明迁移，旧短密码无需用户操作即可继续使用）。
     */
    private boolean verifyAndUpgradePassword(String rawPassword, TournamentRefereeConfig config) {
        String storedHash = config.getPasswordHash();
        if (StrUtil.isNotBlank(storedHash) && storedHash.startsWith("$2")) {
            return BCrypt.checkpw(rawPassword, storedHash);
        }
        if (!legacyHashPassword(rawPassword).equals(storedHash)) {
            return false;
        }
        TournamentRefereeConfig upgrade = new TournamentRefereeConfig();
        upgrade.setId(config.getId());
        upgrade.setPasswordHash(hashPassword(rawPassword));
        upgrade.setUpdateTime(LocalDateTime.now());
        tournamentRefereeConfigMapper.updateById(upgrade);
        return true;
    }

    private String legacyHashPassword(String rawPassword) {
        return DigestUtil.sha256Hex(rawPassword + LEGACY_REFEREE_HASH_SALT);
    }

    private void requireNotLocked(String failureKey) {
        FailureRecord record = passwordFailures.get(failureKey);
        if (record != null && record.lockedUntil > System.currentTimeMillis()) {
            throw new IllegalArgumentException("尝试次数过多，请15分钟后再试");
        }
    }

    private void recordFailure(String failureKey) {
        long now = System.currentTimeMillis();
        long windowMillis = REFEREE_FAILURE_WINDOW.toMillis();
        passwordFailures.compute(failureKey, (key, existing) -> {
            FailureRecord record = existing;
            if (record == null || now - record.windowStartAt >= windowMillis) {
                record = new FailureRecord();
                record.windowStartAt = now;
            }
            record.count++;
            if (record.count >= REFEREE_MAX_FAILURES) {
                record.lockedUntil = now + windowMillis;
            }
            return record;
        });
        // 防止恶意构造 tournamentId 撑爆内存（同 RequestRateLimiter 的清理阈值）
        if (passwordFailures.size() > 4096) {
            passwordFailures.entrySet().removeIf(entry -> now - entry.getValue().windowStartAt >= windowMillis * 2);
        }
    }

    private static class FailureRecord {
        private long windowStartAt;
        private long lockedUntil;
        private int count;
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
