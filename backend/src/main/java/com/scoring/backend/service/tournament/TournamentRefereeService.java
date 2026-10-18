package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.scoring.backend.common.DuplicateKeySupport;
import com.scoring.backend.common.FailureLockTracker;
import com.scoring.backend.domain.dto.TournamentRefereeAuthReq;
import com.scoring.backend.domain.dto.UpdateTournamentRefereePasswordReq;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeConfig;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.User;
import com.scoring.backend.domain.vo.TournamentMatchAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeAccessVO;
import com.scoring.backend.domain.vo.TournamentRefereeVO;
import com.scoring.backend.mapper.MatchRecordMapper;
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
import java.util.stream.Collectors;

/**
 * 裁判认证与授权服务（双级口令模型）：
 * - 【现场执裁密码】password_hash → SCORER 授权（单场记分/阵容/战报等单场操作）；
 * - 【赛务编排密码】admin_password_hash → ADMIN 授权（出线覆盖/排签/重开已完赛比赛，含执裁能力）；
 * - 认证入口单一：同一输入框命中哪级密码即授予哪级角色，已是 ADMIN 不因误输执裁密码降级；
 * - 改密精准吊销：改哪级密码仅吊销哪级已授权记录，并同步清理被撤权人的活跃执裁锁。
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
    private final MatchRecordMapper matchRecordMapper;
    private final TournamentAccessGuard accessGuard;
    /** 密码失败计数/锁定态：按 (tournamentId, userId) 维度内存持有，进程重启即清零（实现见 FailureLockTracker） */
    private final FailureLockTracker passwordFailures =
            new FailureLockTracker(REFEREE_MAX_FAILURES, REFEREE_FAILURE_WINDOW);

    public TournamentRefereeService(TournamentMapper tournamentMapper,
                                    TournamentRefereeConfigMapper tournamentRefereeConfigMapper,
                                    TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                                    UserMapper userMapper,
                                    MatchRecordMapper matchRecordMapper,
                                    TournamentAccessGuard accessGuard) {
        this.tournamentMapper = tournamentMapper;
        this.tournamentRefereeConfigMapper = tournamentRefereeConfigMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.userMapper = userMapper;
        this.matchRecordMapper = matchRecordMapper;
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
        passwordFailures.requireNotLocked(failureKey);
        String targetRole = resolveGrantRole(req.getPassword(), config);
        if (targetRole == null) {
            passwordFailures.recordFailure(failureKey);
            throw new IllegalArgumentException("裁判密码错误");
        }
        passwordFailures.clear(failureKey);

        String effectiveRole = upsertGrantWithRole(userId, tournamentId, targetRole);

        TournamentRefereeAccessVO vo = new TournamentRefereeAccessVO();
        vo.setGranted(true);
        vo.setGrantRole(effectiveRole);
        vo.setReferees(buildRefereeVOList(tournamentId));
        return vo;
    }

    /**
     * 双密码智能识别：先比对赛务编排密码（命中 → ADMIN），未命中再比对现场执裁密码（命中 → SCORER）。
     * 两级均未命中返回 null（由调用方记失败次数）。
     */
    private String resolveGrantRole(String rawPassword, TournamentRefereeConfig config) {
        if (StrUtil.isBlank(rawPassword)) {
            return null;
        }
        // 编排密码为新增列，恒为 BCrypt，无历史哈希迁移负担
        String adminHash = config.getAdminPasswordHash();
        if (StrUtil.isNotBlank(adminHash) && adminHash.startsWith("$2")
                && BCrypt.checkpw(rawPassword, adminHash)) {
            return TournamentRefereeGrant.GRANT_ROLE_ADMIN;
        }
        if (verifyAndUpgradePassword(rawPassword, config)) {
            return TournamentRefereeGrant.GRANT_ROLE_SCORER;
        }
        return null;
    }

    /**
     * 写入/晋升授权记录：
     * - 无既有记录 → 新增（grant_role = 命中角色）；
     * - 已是 SCORER 且本次命中 ADMIN → 原地晋升（现场裁判补输编排密码升级为赛务副手）；
     * - 已是 ADMIN → 保持 ADMIN（防降级：误输执裁密码不降低既有权限）。
     *
     * @return 本次认证最终落定的角色
     */
    private String upsertGrantWithRole(String userId, String tournamentId, String targetRole) {
        TournamentRefereeGrant existing = tournamentRefereeGrantMapper.selectOne(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        );

        if (existing == null) {
            TournamentRefereeGrant grant = new TournamentRefereeGrant();
            grant.setTournamentId(tournamentId);
            grant.setUserId(userId);
            grant.setGrantRole(targetRole);
            try {
                tournamentRefereeGrantMapper.insert(grant);
                return targetRole;
            } catch (RuntimeException ex) {
                // 并发认证下唯一键兜底：另一请求已完成授权，本次按已授权成功返回
                if (!DuplicateKeySupport.isDuplicateKey(ex)) {
                    throw ex;
                }
                TournamentRefereeGrant concurrent = tournamentRefereeGrantMapper.selectOne(
                        new QueryWrapper<TournamentRefereeGrant>()
                                .eq("tournament_id", tournamentId)
                                .eq("user_id", userId)
                );
                if (concurrent != null && TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(targetRole)
                        && !TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(concurrent.getGrantRole())) {
                    TournamentRefereeGrant promotion = new TournamentRefereeGrant();
                    promotion.setId(concurrent.getId());
                    promotion.setGrantRole(TournamentRefereeGrant.GRANT_ROLE_ADMIN);
                    tournamentRefereeGrantMapper.updateById(promotion);
                    return TournamentRefereeGrant.GRANT_ROLE_ADMIN;
                }
                return concurrent != null
                        ? StrUtil.blankToDefault(concurrent.getGrantRole(), targetRole)
                        : targetRole;
            }
        }

        String currentRole = StrUtil.blankToDefault(existing.getGrantRole(),
                TournamentRefereeGrant.GRANT_ROLE_SCORER);
        if (TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(targetRole)
                && !TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(currentRole)) {
            TournamentRefereeGrant promotion = new TournamentRefereeGrant();
            promotion.setId(existing.getId());
            promotion.setGrantRole(TournamentRefereeGrant.GRANT_ROLE_ADMIN);
            tournamentRefereeGrantMapper.updateById(promotion);
            return TournamentRefereeGrant.GRANT_ROLE_ADMIN;
        }
        return TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(currentRole)
                ? TournamentRefereeGrant.GRANT_ROLE_ADMIN
                : TournamentRefereeGrant.GRANT_ROLE_SCORER;
    }

    public List<TournamentRefereeVO> listReferees(String userId, String tournamentId) {
        accessGuard.requireTournament(tournamentId);
        // 双级口令模型：裁判名单含角色信息，仅创建者与赛务管理员（ADMIN）可见，普通执裁裁判不可见
        accessGuard.requireCreatorOrAdmin(userId, tournamentId);
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
        // 撤权即清锁：被移除裁判若正持锁执裁，立即失效，避免残留的僵尸会话继续写分
        clearMatchLocksByUsers(tournamentId, List.of(refereeUserId));
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateRefereePassword(String userId, String tournamentId, UpdateTournamentRefereePasswordReq req) {
        Tournament tournament = accessGuard.requireTournament(tournamentId);
        accessGuard.requireNotArchived(tournament);

        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("只有创建者可以修改裁判密码");
        }

        String scorerPassword = StrUtil.trimToNull(req == null ? null : req.getPassword());
        String adminPassword = StrUtil.trimToNull(req == null ? null : req.getAdminPassword());
        if (scorerPassword == null && adminPassword == null) {
            throw new IllegalArgumentException("请至少提供现场执裁密码或赛务编排密码之一");
        }
        if (scorerPassword != null) {
            validateRefereePassword(scorerPassword, "裁判密码");
        }
        if (adminPassword != null) {
            validateRefereePassword(adminPassword, "赛务编排密码");
        }
        if (scorerPassword != null && adminPassword != null && StrUtil.equals(scorerPassword, adminPassword)) {
            throw new IllegalArgumentException("两级密码不可相同");
        }

        TournamentRefereeConfig config = tournamentRefereeConfigMapper.selectOne(
                new QueryWrapper<TournamentRefereeConfig>()
                        .eq("tournament_id", tournamentId)
        );

        // 跨级撞车校验：仅改其中一级时，新密码明文不得与库中另一级密码相同
        if (adminPassword == null && config != null && StrUtil.isNotBlank(config.getAdminPasswordHash())
                && BCrypt.checkpw(scorerPassword, config.getAdminPasswordHash())) {
            throw new IllegalArgumentException("两级密码不可相同");
        }
        if (scorerPassword == null && config != null && StrUtil.isNotBlank(config.getPasswordHash())) {
            String storedScorerHash = config.getPasswordHash();
            if (storedScorerHash.startsWith("$2")) {
                if (BCrypt.checkpw(adminPassword, storedScorerHash)) {
                    throw new IllegalArgumentException("两级密码不可相同");
                }
            } else if (legacyHashPassword(adminPassword).equals(storedScorerHash)) {
                throw new IllegalArgumentException("两级密码不可相同");
            }
        }

        if (config == null) {
            if (scorerPassword == null) {
                throw new IllegalArgumentException("首次配置裁判密码时，必须提供现场执裁密码");
            }
            config = new TournamentRefereeConfig();
            config.setTournamentId(tournamentId);
            config.setPasswordHash(hashPassword(scorerPassword));
            config.setAdminPasswordHash(adminPassword == null ? null : hashPassword(adminPassword));
            tournamentRefereeConfigMapper.insert(config);
        } else {
            // 只更新变更字段并显式刷新 update_time：整实体回写会携带旧 update_time，抑制列的 ON UPDATE（update_time 停摆问题）
            TournamentRefereeConfig update = new TournamentRefereeConfig();
            update.setId(config.getId());
            if (scorerPassword != null) {
                update.setPasswordHash(hashPassword(scorerPassword));
            }
            if (adminPassword != null) {
                update.setAdminPasswordHash(hashPassword(adminPassword));
            }
            update.setUpdateTime(LocalDateTime.now());
            tournamentRefereeConfigMapper.updateById(update);
        }

        // 精准吊销：修改哪一级密码仅吊销哪一级已授权记录，互不影响另一级；
        // 吊销同时清理被撤权人在该赛事下的活跃执裁锁。
        if (scorerPassword != null) {
            revokeGrantsOfRole(tournamentId, TournamentRefereeGrant.GRANT_ROLE_SCORER);
        }
        if (adminPassword != null) {
            revokeGrantsOfRole(tournamentId, TournamentRefereeGrant.GRANT_ROLE_ADMIN);
        }
    }

    /** 吊销指定角色的全部授权并清理其执裁锁（用户清单须在删除授权前收集） */
    private void revokeGrantsOfRole(String tournamentId, String grantRole) {
        QueryWrapper<TournamentRefereeGrant> wrapper = new QueryWrapper<TournamentRefereeGrant>()
                .eq("tournament_id", tournamentId);
        if (TournamentRefereeGrant.GRANT_ROLE_SCORER.equals(grantRole)) {
            wrapper.and(w -> w.eq("grant_role", TournamentRefereeGrant.GRANT_ROLE_SCORER)
                    .or().isNull("grant_role")
                    .or().eq("grant_role", ""));
        } else {
            wrapper.eq("grant_role", grantRole);
        }

        List<TournamentRefereeGrant> grants = tournamentRefereeGrantMapper.selectList(wrapper);
        if (CollUtil.isEmpty(grants)) {
            return;
        }
        tournamentRefereeGrantMapper.delete(wrapper);
        clearMatchLocksByUsers(tournamentId,
                grants.stream().map(TournamentRefereeGrant::getUserId).collect(Collectors.toList()));
    }

    /** 清理指定用户在某赛事下持有的全部执裁锁（撤权/移除裁判时调用，防止僵尸会话继续写分） */
    private void clearMatchLocksByUsers(String tournamentId, List<String> userIds) {
        if (StrUtil.isBlank(tournamentId) || CollUtil.isEmpty(userIds)) {
            return;
        }
        matchRecordMapper.update(
                null,
                new UpdateWrapper<MatchRecord>()
                        .eq("tournament_id", tournamentId)
                        .in("locked_by_user_id", userIds)
                        .set("locked_by_user_id", null)
                        .set("lock_token", null)
                        .set("lock_expire_time", null)
        );
    }

    // ======================== 裁判辅助方法 ========================

    private void validateRefereePassword(String password, String label) {
        if (StrUtil.isBlank(password)) {
            throw new IllegalArgumentException(label + "不能为空");
        }
        if (!password.matches(REFEREE_PASSWORD_PATTERN)) {
            throw new IllegalArgumentException(label + "必须不少于8位数字");
        }
    }

    /** 新密码用 BCrypt（每条记录随机盐）存储，与用户密码同一套工具。 */
    private String hashPassword(String rawPassword) {
        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    /**
     * 校验现场执裁密码：库中为 BCrypt 走 BCrypt；旧库为单轮 SHA256+静态盐，
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

    public void fillMatchAccess(TournamentMatchAccessVO vo, Tournament tournament, String currentUserId) {
        if (vo == null || tournament == null) {
            return;
        }

        boolean isCreator = StrUtil.equals(currentUserId, tournament.getCreatorUserId());
        if (accessGuard.isArchived(tournament)) {
            vo.setRefereeGranted(false);
            vo.setCanOperateMatches(false);
            vo.setCanManageReferees(false);
            vo.setRefereeRole(null);
            vo.setCanManageKnockout(false);
            return;
        }

        String refereeRole = accessGuard.findRefereeRole(currentUserId, tournament.getId());
        boolean isReferee = refereeRole != null;
        boolean isAdmin = TournamentRefereeGrant.GRANT_ROLE_ADMIN.equals(refereeRole);

        vo.setRefereeGranted(isReferee);
        vo.setCanOperateMatches(isCreator || isReferee);
        vo.setCanManageReferees(isCreator);
        vo.setRefereeRole(isReferee ? refereeRole : null);
        // 编排操作（出线覆盖/排签）：创建者或 ADMIN；未启用编排密码的赛事自然退化为仅创建者
        vo.setCanManageKnockout(isCreator || isAdmin);
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
            vo.setGrantRole(StrUtil.blankToDefault(grant.getGrantRole(),
                    TournamentRefereeGrant.GRANT_ROLE_SCORER));
            return vo;
        }).collect(Collectors.toList());
    }
}
