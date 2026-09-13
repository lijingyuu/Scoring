package com.scoring.backend.service.match;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.domain.entity.TournamentQualificationOverride;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.service.impl.TournamentRuleResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 比赛写入与结算服务：比分更新、完赛、团体赛父场结算、重启比赛、晋级传播链。
 *
 * 事务语义：本类 public 入口均显式声明 @Transactional(rollbackFor = Exception.class)，
 * 经 Spring 容器注入调用（Facade 委托），确保 AOP 代理生效；
 * 晋级传播（propagateFinishedMatch -> finishTournamentIfReady / settleParentTeamMatch）
 * 与父场更新在同一物理事务内提交，调用方已有事务时按 REQUIRED 传播加入。
 *
 * 状态机红线（修改前必须与团队确认）：
 * - 已封存战报的比赛不可重启；
 * - 团体赛需全部子场完赛才结算（淘汰赛一方 3 胜可提前结算）；
 * - 晋级槽位写入前必须经 FOR UPDATE 行锁。
 * - 组别状态机：division.status 0 未开始 / 1 进行中 / 2 已结束；任一组别开赛 → 赛事进行中，
 *   全部组别完赛 → 赛事完赛；聚合判定必须在 match 行锁 + 该赛事全部组别行锁下完成（防并发漏判）。
 */
@Service
public class MatchSettlementService {

    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;

    private final MatchRecordMapper matchRecordMapper;
    private final TournamentMapper tournamentMapper;
    private final TournamentDivisionMapper tournamentDivisionMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final MatchEventMapper matchEventMapper;
    private final MatchLineupConfigMapper matchLineupConfigMapper;
    private final MatchReportMetaMapper matchReportMetaMapper;
    private final TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    private final TournamentRuleResolver tournamentRuleResolver;
    private final MatchAccessGuard matchAccessGuard;
    private final MatchReportAssembler reportAssembler;
    private final MatchLockService matchLockService;

    public MatchSettlementService(MatchRecordMapper matchRecordMapper,
                                  TournamentMapper tournamentMapper,
                                  TeamMatchItemMapper teamMatchItemMapper,
                                  TournamentDivisionMapper tournamentDivisionMapper,
                                  MatchEventMapper matchEventMapper,
                                  MatchLineupConfigMapper matchLineupConfigMapper,
                                  MatchReportMetaMapper matchReportMetaMapper,
                                  TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper,
                                  TournamentRuleResolver tournamentRuleResolver,
                                  MatchAccessGuard matchAccessGuard,
                                  MatchReportAssembler reportAssembler,
                                  MatchLockService matchLockService) {
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentMapper = tournamentMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
        this.matchEventMapper = matchEventMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.matchReportMetaMapper = matchReportMetaMapper;
        this.tournamentQualificationOverrideMapper = tournamentQualificationOverrideMapper;
        this.tournamentRuleResolver = tournamentRuleResolver;
        this.matchAccessGuard = matchAccessGuard;
        this.reportAssembler = reportAssembler;
        this.matchLockService = matchLockService;
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req) {
        updateMatchResultInternal(userId, matchId, req, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req, String lockToken) {
        updateMatchResultInternal(userId, matchId, req, lockToken, true);
    }

    private void updateMatchResultInternal(String userId, String matchId, UpdateScoreReq req, String lockToken, boolean requireLock) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerId())) {
            throw new IllegalArgumentException("winnerId cannot be blank");
        }

        MatchRecord current = requireMatchForUpdate(matchId);

        Tournament tournament = matchAccessGuard.requireMatchOperator(userId, current.getTournamentId());
        if (requireLock) {
            matchLockService.requireActiveMatchLock(current, userId, lockToken);
        }
        ensureMatchPlayableForResult(current);
        ensureWinnerBelongsToMatch(current, req.getWinnerId());
        clearQualificationOverridesIfRankingMatch(current);

        MatchRecord updateCurrent = new MatchRecord();
        updateCurrent.setId(matchId);
        updateCurrent.setScoreDisplay(req.getScoreDisplay());
        updateCurrent.setWinnerId(req.getWinnerId());
        updateCurrent.setStatus(2);
        matchRecordMapper.updateById(updateCurrent);
        matchLockService.clearMatchLock(matchId);

        propagateFinishedMatch(current, tournament, req.getWinnerId());
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req) {
        finishMatchInternal(userId, matchId, req, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req, String lockToken) {
        finishMatchInternal(userId, matchId, req, lockToken, true);
    }

    private void finishMatchInternal(String userId, String matchId, FinishMatchReq req, String lockToken, boolean requireLock) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerSide())) {
            throw new IllegalArgumentException("winnerSide cannot be blank");
        }

        MatchRecord current = requireMatchForUpdate(matchId);

        Tournament tournament = matchAccessGuard.requireMatchOperator(userId, current.getTournamentId());
        if (requireLock) {
            matchLockService.requireActiveMatchLock(current, userId, lockToken);
        }
        ensureMatchPlayableForResult(current);
        clearQualificationOverridesIfRankingMatch(current);

        String winnerId;
        if ("left".equals(req.getWinnerSide())) {
            winnerId = current.getLeftPlayerId();
        } else if ("right".equals(req.getWinnerSide())) {
            winnerId = current.getRightPlayerId();
        } else {
            throw new IllegalArgumentException("winnerSide must be left or right");
        }
        if (StrUtil.isBlank(winnerId)) {
            throw new IllegalStateException("winner participant is missing");
        }

        MatchRuleConfig matchRule = tournamentRuleResolver.resolveForMatch(tournament, current);
        validateFinishReq(req, matchRule);
        String scoreDisplay = buildScoreDisplay(req);

        MatchRecord updateCurrent = new MatchRecord();
        updateCurrent.setId(matchId);
        updateCurrent.setScoreDisplay(scoreDisplay);
        updateCurrent.setWinnerId(winnerId);
        updateCurrent.setLeftGameWins(req.getLeftGameWins());
        updateCurrent.setRightGameWins(req.getRightGameWins());
        if (CollUtil.isNotEmpty(req.getRelaySegmentScores())) {
            updateCurrent.setGameScores(JSONUtil.toJsonStr(req.getRelaySegmentScores()));
        } else if (req.getGameScores() != null) {
            updateCurrent.setGameScores(JSONUtil.toJsonStr(req.getGameScores()));
        }
        updateCurrent.setStatus(2);
        if (StrUtil.isNotBlank(req.getRetiredSide())) {
            updateCurrent.setRetiredSide(req.getRetiredSide());
        }
        matchRecordMapper.updateById(updateCurrent);
        matchLockService.clearMatchLock(matchId);

        TeamMatchItem childItem = findTeamChildItem(matchId);
        if (childItem != null) {
            TeamMatchItem updateItem = new TeamMatchItem();
            updateItem.setId(childItem.getId());
            updateItem.setStatus(2);
            updateItem.setWinnerSide(req.getWinnerSide());
            teamMatchItemMapper.updateById(updateItem);
            childItem.setStatus(2);
            childItem.setWinnerSide(req.getWinnerSide());
            finishParentTeamMatchIfSettled(childItem, tournament);
            return;
        }

        propagateFinishedMatch(current, tournament, winnerId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId) {
        settleTeamMatchInternal(userId, matchId, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId, String lockToken) {
        settleTeamMatchInternal(userId, matchId, lockToken, true);
    }

    private void settleTeamMatchInternal(String userId, String matchId, String lockToken, boolean requireLock) {
        MatchRecord parent = requireMatchForUpdate(matchId);
        Tournament tournament = matchAccessGuard.requireMatchOperator(userId, parent.getTournamentId());
        if (requireLock) {
            matchLockService.requireActiveMatchLock(parent, userId, lockToken);
        }
        clearQualificationOverridesIfRankingMatch(parent);
        settleParentTeamMatch(parent, tournament, true);
        matchLockService.clearMatchLock(matchId);
    }

    private void propagateFinishedMatch(MatchRecord current, Tournament tournament, String winnerId) {
        propagateLoserIfNeeded(current, winnerId);
        if (StrUtil.isBlank(current.getNextMatchId())) {
            // 赛制以组别为准（多组别赛事各组赛制独立），组别缺失时退回赛事级字段（旧数据兜底）
            TournamentDivision division = loadDivision(current);
            Integer effectiveTournamentType = division != null && division.getTournamentType() != null
                    ? division.getTournamentType()
                    : tournament.getTournamentType();
            if (Integer.valueOf(STAGE_GROUP).equals(current.getStageType())
                    && Integer.valueOf(1).equals(effectiveTournamentType)) {
                return;
            }
            // Round robin: only end tournament when ALL matches of the division have finished
            if (Integer.valueOf(2).equals(effectiveTournamentType)) {
                if (!allTournamentMatchesFinished(current.getDivisionId(), current.getTournamentId())) {
                    return;
                }
            }
            finishTournamentIfReady(current, tournament, division);
            return;
        }

        MatchRecord next = matchRecordMapper.selectByIdForUpdate(current.getNextMatchId());
        if (next == null) {
            throw new IllegalStateException("next match not found: " + current.getNextMatchId());
        }

        MatchRecord updateNext = new MatchRecord();
        updateNext.setId(next.getId());
        if ("left".equals(current.getNextMatchSlot())) {
            updateNext.setLeftPlayerId(winnerId);
        } else if ("right".equals(current.getNextMatchSlot())) {
            updateNext.setRightPlayerId(winnerId);
        } else {
            throw new IllegalStateException("invalid nextMatchSlot: " + current.getNextMatchSlot());
        }

        matchRecordMapper.updateById(updateNext);
    }

    private void propagateLoserIfNeeded(MatchRecord current, String winnerId) {
        if (StrUtil.isBlank(current.getLoserNextMatchId())) {
            return;
        }
        String loserId = resolveLoserId(current, winnerId);
        if (StrUtil.isBlank(loserId)) {
            return;
        }
        MatchRecord loserNext = matchRecordMapper.selectByIdForUpdate(current.getLoserNextMatchId());
        if (loserNext == null) {
            throw new IllegalStateException("loser next match not found: " + current.getLoserNextMatchId());
        }
        MatchRecord updateLoserNext = new MatchRecord();
        updateLoserNext.setId(loserNext.getId());
        if ("left".equals(current.getLoserNextMatchSlot())) {
            updateLoserNext.setLeftPlayerId(loserId);
        } else if ("right".equals(current.getLoserNextMatchSlot())) {
            updateLoserNext.setRightPlayerId(loserId);
        } else {
            throw new IllegalStateException("invalid loserNextMatchSlot: " + current.getLoserNextMatchSlot());
        }
        matchRecordMapper.updateById(updateLoserNext);
    }

    private String resolveLoserId(MatchRecord current, String winnerId) {
        if (StrUtil.equals(winnerId, current.getLeftPlayerId())) {
            return current.getRightPlayerId();
        }
        if (StrUtil.equals(winnerId, current.getRightPlayerId())) {
            return current.getLeftPlayerId();
        }
        return null;
    }

    /**
     * 完赛判定入口（两级）：先判当前组别是否完结，再聚合赛事状态。
     * 组别行锁在 {@link #finishDivisionIfReady} 内获取，与当前事务已持有的 match 行锁同事务提交。
     */
    private void finishTournamentIfReady(MatchRecord current, Tournament tournament, TournamentDivision division) {
        if (division == null) {
            // V23 迁移后比赛均已回填 division_id；命中此分支说明数据异常，跳过状态写入以免误判
            return;
        }
        finishDivisionIfReady(current, tournament, division);
    }

    /**
     * 组别级完赛判定（方案 §5）：
     * - 开了季军赛且当前为淘汰阶段时，须全部"无后续场次的淘汰场"（决赛+季军赛）完赛才可完结组别；
     * - 组别完结后聚合：该赛事全部组别 status=2 才把 tournament.status 置 2。
     *
     * 并发安全：锁顺序统一为 tournament 行 → 全部组别行（按 id 升序 FOR UPDATE），
     * 与 generateKnockout 的加锁顺序一致，避免交叉死锁；两个组别（或同组别两场）
     * 并发完赛时以相同顺序竞争同一组行锁，后到者必能读到先到者已提交的 status=2，
     * 避免"双方都判定未全部完赛"导致赛事状态卡在进行中。
     */
    private void finishDivisionIfReady(MatchRecord current, Tournament tournament, TournamentDivision division) {
        if (tournament != null && StrUtil.isNotBlank(tournament.getId())) {
            tournamentMapper.selectByIdForUpdate(tournament.getId());
        }
        List<TournamentDivision> divisions = lockTournamentDivisions(division.getTournamentId());
        if (divisions.isEmpty()) {
            return;
        }
        TournamentDivision currentDivision = divisions.stream()
                .filter(item -> StrUtil.equals(item.getId(), division.getId()))
                .findFirst()
                .orElse(division);

        if (Boolean.TRUE.equals(currentDivision.getThirdPlaceEnabled())
                && Integer.valueOf(STAGE_KNOCKOUT).equals(current.getStageType())
                && !allTerminalKnockoutMatchesFinished(currentDivision.getId())) {
            return;
        }

        if (!Integer.valueOf(2).equals(currentDivision.getStatus())) {
            TournamentDivision updateDivision = new TournamentDivision();
            updateDivision.setId(currentDivision.getId());
            updateDivision.setStatus(2);
            tournamentDivisionMapper.updateById(updateDivision);
            mirrorDivisionStateToTournamentIfSole(currentDivision, 2, null, null);
        }

        // 当前组别刚置 2（或本已是 2），其余组别以加锁后的最新快照为准
        boolean allDivisionsFinished = divisions.stream()
                .allMatch(item -> StrUtil.equals(item.getId(), currentDivision.getId())
                        || Integer.valueOf(2).equals(item.getStatus()));
        if (allDivisionsFinished) {
            Tournament updateTournament = new Tournament();
            updateTournament.setId(currentDivision.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
        }
    }

    /**
     * 对该赛事全部组别行加锁并返回加锁后的最新快照（按 id 升序，保证并发事务加锁顺序一致、不死锁）。
     * 组别创建后不可增删，先普通查询 id 列表再逐行 selectByIdForUpdate。
     */
    private List<TournamentDivision> lockTournamentDivisions(String tournamentId) {
        List<TournamentDivision> divisions = tournamentDivisionMapper.selectList(
                new QueryWrapper<TournamentDivision>().eq("tournament_id", tournamentId));
        if (CollUtil.isEmpty(divisions)) {
            return List.of();
        }
        List<String> divisionIds = divisions.stream()
                .map(TournamentDivision::getId)
                .filter(StrUtil::isNotBlank)
                .sorted()
                .toList();
        List<TournamentDivision> lockedDivisions = new ArrayList<>(divisionIds.size());
        for (String divisionId : divisionIds) {
            TournamentDivision locked = tournamentDivisionMapper.selectByIdForUpdate(divisionId);
            if (locked != null) {
                lockedDivisions.add(locked);
            }
        }
        return lockedDivisions;
    }

    private void finishParentTeamMatchIfSettled(TeamMatchItem finishedItem, Tournament tournament) {
        if (finishedItem == null || StrUtil.isBlank(finishedItem.getMatchId())) {
            return;
        }
        MatchRecord parent = matchRecordMapper.selectByIdForUpdate(finishedItem.getMatchId());
        settleParentTeamMatch(parent, tournament, false);
    }

    private void settleParentTeamMatch(MatchRecord parent, Tournament tournament, boolean directSettlement) {
        if (parent == null || Integer.valueOf(2).equals(parent.getStatus()) || Integer.valueOf(3).equals(parent.getStatus())) {
            return;
        }
        TeamMatchScore score = countTeamMatchScore(parent.getId());
        if (score.totalItems == 0) {
            throw new IllegalArgumentException("team match lineup not found");
        }

        boolean allFinished = score.finishedCount >= score.totalItems;
        boolean earlyKnockout = Integer.valueOf(STAGE_KNOCKOUT).equals(parent.getStageType())
                && !Integer.valueOf(2).equals(tournament.getTournamentType())
                && (score.leftWins >= 3 || score.rightWins >= 3);
        if (directSettlement) {
            if (!allFinished && !earlyKnockout) {
                throw new IllegalArgumentException("team match requires all items finished, unless knockout stage has one side with 3 wins");
            }
        } else if (!allFinished) {
            return;
        }

        String winnerSide = score.leftWins > score.rightWins ? "left" : score.rightWins > score.leftWins ? "right" : null;
        if (winnerSide == null) {
            throw new IllegalStateException("team match winner cannot be resolved");
        }
        String winnerId = "left".equals(winnerSide) ? parent.getLeftPlayerId() : parent.getRightPlayerId();
        if (StrUtil.isBlank(winnerId)) {
            throw new IllegalStateException("parent team match winner participant is missing");
        }

        MatchRecord updateParent = new MatchRecord();
        updateParent.setId(parent.getId());
        updateParent.setScoreDisplay(score.leftWins + ":" + score.rightWins);
        updateParent.setWinnerId(winnerId);
        updateParent.setLeftGameWins(score.leftWins);
        updateParent.setRightGameWins(score.rightWins);
        updateParent.setStatus(2);
        matchRecordMapper.updateById(updateParent);

        propagateFinishedMatch(parent, tournament, winnerId);
    }

    private TeamMatchScore countTeamMatchScore(String matchId) {
        List<TeamMatchItem> items = teamMatchItemMapper.selectList(new QueryWrapper<TeamMatchItem>()
                .eq("match_id", matchId));
        if (items == null || items.isEmpty()) {
            return new TeamMatchScore(0, 0, 0, 0);
        }
        int leftWins = 0;
        int rightWins = 0;
        int finishedCount = 0;
        for (TeamMatchItem item : items) {
            if ("left".equals(item.getWinnerSide())) {
                leftWins++;
                finishedCount++;
            } else if ("right".equals(item.getWinnerSide())) {
                rightWins++;
                finishedCount++;
            }
        }
        return new TeamMatchScore(leftWins, rightWins, finishedCount, items.size());
    }

    private static class TeamMatchScore {
        private final int leftWins;
        private final int rightWins;
        private final int finishedCount;
        private final int totalItems;

        private TeamMatchScore(int leftWins, int rightWins, int finishedCount, int totalItems) {
            this.leftWins = leftWins;
            this.rightWins = rightWins;
            this.finishedCount = finishedCount;
            this.totalItems = totalItems;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId) {
        restartMatchInternal(userId, matchId, null, false);
    }

    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId, String lockToken) {
        restartMatchInternal(userId, matchId, lockToken, true);
    }

    private void restartMatchInternal(String userId, String matchId, String lockToken, boolean requireLock) {
        MatchRecord match = requireMatchForUpdate(matchId);
        matchAccessGuard.requireMatchOperator(userId, match.getTournamentId());
        if (requireLock) {
            matchLockService.requireActiveMatchLock(match, userId, lockToken);
        }
        reportAssembler.ensureReportNotSealed(matchId);
        clearQualificationOverridesIfRankingMatch(match);

        clearDownstreamAfterRestart(match);
        clearMatchArtifacts(matchId);
        resetMatchResult(matchId);
        markTournamentRunning(match);
    }

    private void clearDownstreamAfterRestart(MatchRecord source) {
        if (source == null) {
            return;
        }
        clearDownstreamSlotAfterRestart(source.getNextMatchId(), source.getNextMatchSlot());
        clearDownstreamSlotAfterRestart(source.getLoserNextMatchId(), source.getLoserNextMatchSlot());
    }

    private void clearDownstreamSlotAfterRestart(String nextMatchId, String nextMatchSlot) {
        if (StrUtil.isBlank(nextMatchId) || StrUtil.isBlank(nextMatchSlot)) {
            return;
        }

        MatchRecord next = matchRecordMapper.selectByIdForUpdate(nextMatchId);
        if (next == null) {
            throw new IllegalStateException("next match not found: " + nextMatchId);
        }
        reportAssembler.ensureReportNotSealed(next.getId());

        boolean nextWinnerWasPropagated = StrUtil.isNotBlank(next.getWinnerId());
        clearMatchArtifacts(next.getId());
        resetMatchResult(next.getId());
        clearParticipantSlot(next.getId(), nextMatchSlot);

        if (nextWinnerWasPropagated) {
            clearDownstreamAfterRestart(next);
        }
    }

    private void clearMatchArtifacts(String matchId) {
        matchEventMapper.delete(new QueryWrapper<MatchEvent>()
                .eq("match_id", matchId));
        matchLineupConfigMapper.delete(new QueryWrapper<MatchLineupConfig>()
                .eq("match_id", matchId));
        matchReportMetaMapper.delete(new QueryWrapper<MatchReportMeta>()
                .eq("match_id", matchId));
    }

    private void resetMatchResult(String matchId) {
        matchRecordMapper.update(
                null,
                new LambdaUpdateWrapper<MatchRecord>()
                        .eq(MatchRecord::getId, matchId)
                        .set(MatchRecord::getScoreDisplay, null)
                        .set(MatchRecord::getWinnerId, null)
                        .set(MatchRecord::getLeftGameWins, 0)
                        .set(MatchRecord::getRightGameWins, 0)
                        .set(MatchRecord::getGameScores, null)
                        .set(MatchRecord::getStatus, 0)
                        .set(MatchRecord::getRetiredSide, null)
                        .set(MatchRecord::getLockedByUserId, null)
                        .set(MatchRecord::getLockToken, null)
                        .set(MatchRecord::getLockExpireTime, null)
        );
    }

    private void clearParticipantSlot(String matchId, String slot) {
        LambdaUpdateWrapper<MatchRecord> wrapper = new LambdaUpdateWrapper<MatchRecord>()
                .eq(MatchRecord::getId, matchId);
        if ("left".equals(slot)) {
            wrapper.set(MatchRecord::getLeftPlayerId, null);
        } else if ("right".equals(slot)) {
            wrapper.set(MatchRecord::getRightPlayerId, null);
        } else {
            throw new IllegalStateException("invalid nextMatchSlot: " + slot);
        }
        matchRecordMapper.update(null, wrapper);
    }

    /**
     * 开赛/重开状态置位（幂等）：重启已完结比赛时，
     * 所属组别 status 非 1 即置 1（0→1 首次开赛，2→1 重开已完结组别）；
     * tournament.status 无条件置 1（重复写无害）。锁顺序：tournament → division。
     */
    private void markTournamentRunning(MatchRecord match) {
        tournamentMapper.selectByIdForUpdate(match.getTournamentId());
        TournamentDivision division = lockDivision(match);
        if (division != null && !Integer.valueOf(1).equals(division.getStatus())) {
            TournamentDivision updateDivision = new TournamentDivision();
            updateDivision.setId(division.getId());
            updateDivision.setStatus(1);
            tournamentDivisionMapper.updateById(updateDivision);
            mirrorDivisionStateToTournamentIfSole(division, 1, null, null);
        }
        Tournament update = new Tournament();
        update.setId(match.getTournamentId());
        update.setStatus(1);
        tournamentMapper.updateById(update);
    }

    /** 按 match.divisionId 加载组别并加行锁；divisionId 缺失（异常数据）时返回 null，仅走赛事级置位 */
    private TournamentDivision lockDivision(MatchRecord match) {
        if (match == null || StrUtil.isBlank(match.getDivisionId())) {
            return null;
        }
        return tournamentDivisionMapper.selectByIdForUpdate(match.getDivisionId());
    }

    /** 按 match.divisionId 普通加载组别（不加锁，用于读取创建后不变的属性，如赛制） */
    private TournamentDivision loadDivision(MatchRecord match) {
        if (match == null || StrUtil.isBlank(match.getDivisionId())) {
            return null;
        }
        return tournamentDivisionMapper.selectById(match.getDivisionId());
    }

    /**
     * 单组别镜像写（回滚保险，方案 §1.4）：赛事只有一个组别时，把组别
     * status / current_stage / knockout_generated 的变更同步镜像到 tournament 同名列，
     * 保证回滚到旧代码后单组别赛事仍可完整运维；多组别不镜像。null 字段不写入。
     */
    private void mirrorDivisionStateToTournamentIfSole(TournamentDivision division, Integer status, Integer currentStage, Boolean knockoutGenerated) {
        if (division == null || StrUtil.isBlank(division.getTournamentId())) {
            return;
        }
        Long divisionCount = tournamentDivisionMapper.selectCount(new QueryWrapper<TournamentDivision>()
                .eq("tournament_id", division.getTournamentId()));
        if (divisionCount == null || divisionCount != 1) {
            return;
        }
        Tournament mirror = new Tournament();
        mirror.setId(division.getTournamentId());
        mirror.setStatus(status);
        mirror.setCurrentStage(currentStage);
        mirror.setKnockoutGenerated(knockoutGenerated);
        tournamentMapper.updateById(mirror);
    }

    private void clearQualificationOverridesIfRankingMatch(MatchRecord match) {
        if (match == null
                || (match.getStageType() != STAGE_GROUP && match.getStageType() != STAGE_TEAM_CHILD)) {
            return;
        }
        if (StrUtil.isBlank(match.getDivisionId())) {
            return;
        }
        tournamentQualificationOverrideMapper.delete(
                new QueryWrapper<TournamentQualificationOverride>()
                        .eq("division_id", match.getDivisionId())
        );
    }

    private String buildScoreDisplay(FinishMatchReq req) {
        if (req.getGameScores() == null || req.getGameScores().isEmpty()) {
            return req.getLeftScore() + ":" + req.getRightScore();
        }

        return req.getGameScores().stream()
                .map(score -> score.getLeftScore() + ":" + score.getRightScore())
                .reduce((a, b) -> a + ", " + b)
                .orElse(req.getLeftScore() + ":" + req.getRightScore());
    }

    private void validateFinishReq(FinishMatchReq req, MatchRuleConfig rule) {
        int leftWins = req.getLeftGameWins() == null ? 0 : req.getLeftGameWins();
        int rightWins = req.getRightGameWins() == null ? 0 : req.getRightGameWins();
        if (leftWins < 0 || rightWins < 0) {
            throw new IllegalArgumentException("game wins cannot be negative");
        }

        int gamesToWin = rule == null || rule.getGamesToWin() == null
                ? Math.max(leftWins, rightWins)
                : rule.getGamesToWin();
        if (gamesToWin <= 0) {
            throw new IllegalArgumentException("gamesToWin is invalid");
        }

        if ("left".equals(req.getWinnerSide())) {
            if (leftWins <= rightWins || leftWins != gamesToWin) {
                throw new IllegalArgumentException("left winner does not match game wins");
            }
        } else if ("right".equals(req.getWinnerSide())) {
            if (rightWins <= leftWins || rightWins != gamesToWin) {
                throw new IllegalArgumentException("right winner does not match game wins");
            }
        } else {
            throw new IllegalArgumentException("winnerSide must be left or right");
        }

        if (req.getGameScores() == null || req.getGameScores().isEmpty()) {
            if (StrUtil.isBlank(req.getRetiredSide())) {
                throw new IllegalArgumentException("gameScores cannot be empty");
            }
            return;
        }

        if (req.getGameScores().size() != leftWins + rightWins) {
            throw new IllegalArgumentException("gameScores size does not match game wins");
        }

        int countedLeftWins = 0;
        int countedRightWins = 0;
        for (FinishMatchReq.GameScore score : req.getGameScores()) {
            if (score == null || score.getLeftScore() == null || score.getRightScore() == null) {
                throw new IllegalArgumentException("game score cannot be empty");
            }
            if (score.getLeftScore() < 0 || score.getRightScore() < 0) {
                throw new IllegalArgumentException("game score cannot be negative");
            }
            if (score.getLeftScore().equals(score.getRightScore())) {
                throw new IllegalArgumentException("single game cannot end in a draw");
            }

            String expectedWinner = score.getLeftScore() > score.getRightScore() ? "left" : "right";
            if (!expectedWinner.equals(score.getWinnerSide())) {
                throw new IllegalArgumentException("game winner does not match score");
            }
            if ("left".equals(expectedWinner)) {
                countedLeftWins++;
            } else {
                countedRightWins++;
            }
        }

        if (countedLeftWins != leftWins || countedRightWins != rightWins) {
            throw new IllegalArgumentException("gameScores winners do not match game wins");
        }
    }

    private TeamMatchItem findTeamChildItem(String childMatchId) {
        if (StrUtil.isBlank(childMatchId)) {
            return null;
        }
        return teamMatchItemMapper.selectOne(new QueryWrapper<TeamMatchItem>()
                .eq("child_match_id", childMatchId));
    }

    /**
     * 单循环赛制完赛判定：该组别全部比赛（剔除团体赛子场）均已完赛。
     * 比赛计数按 division_id 维度；团体赛子场仍按 tournament_id 查询排除
     * （团体赛恒单组别，两维度等价；多组别个人赛无团体子场）。
     */
    private boolean allTournamentMatchesFinished(String divisionId, String tournamentId) {
        if (StrUtil.isBlank(divisionId)) {
            return false;
        }
        List<TeamMatchItem> childItems = teamMatchItemMapper.selectList(new QueryWrapper<TeamMatchItem>()
                .eq("tournament_id", tournamentId)
                .isNotNull("child_match_id"));
        if (childItems == null) {
            childItems = List.of();
        }
        List<String> childMatchIds = childItems.stream()
                .map(TeamMatchItem::getChildMatchId)
                .filter(StrUtil::isNotBlank)
                .toList();
        QueryWrapper<MatchRecord> totalQuery = new QueryWrapper<MatchRecord>().eq("division_id", divisionId);
        QueryWrapper<MatchRecord> finishedQuery = new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .in("status", List.of(2, 3));
        if (!childMatchIds.isEmpty()) {
            totalQuery.notIn("id", childMatchIds);
            finishedQuery.notIn("id", childMatchIds);
        }
        long total = matchRecordMapper.selectCount(totalQuery);
        long finished = matchRecordMapper.selectCount(finishedQuery);
        return finished >= total;
    }

    private boolean allTerminalKnockoutMatchesFinished(String divisionId) {
        if (StrUtil.isBlank(divisionId)) {
            return false;
        }
        QueryWrapper<MatchRecord> totalQuery = new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .eq("stage_type", STAGE_KNOCKOUT)
                .isNull("next_match_id");
        QueryWrapper<MatchRecord> finishedQuery = new QueryWrapper<MatchRecord>()
                .eq("division_id", divisionId)
                .eq("stage_type", STAGE_KNOCKOUT)
                .isNull("next_match_id")
                .in("status", List.of(2, 3));
        long total = matchRecordMapper.selectCount(totalQuery);
        long finished = matchRecordMapper.selectCount(finishedQuery);
        return total > 0 && finished >= total;
    }

    public void ensureMatchPlayableForResult(MatchRecord match) {
        if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("match already finished");
        }
        ensureMatchParticipantsReady(match);
    }

    public void ensureMatchParticipantsReady(MatchRecord match) {
        if (StrUtil.isBlank(match.getLeftPlayerId()) || StrUtil.isBlank(match.getRightPlayerId())) {
            throw new IllegalArgumentException("match participants are incomplete");
        }
    }

    public void ensureWinnerBelongsToMatch(MatchRecord match, String winnerId) {
        if (!StrUtil.equals(winnerId, match.getLeftPlayerId()) && !StrUtil.equals(winnerId, match.getRightPlayerId())) {
            throw new IllegalArgumentException("winnerId must belong to this match");
        }
    }

    private MatchRecord requireMatchForUpdate(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectByIdForUpdate(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
    }
}
