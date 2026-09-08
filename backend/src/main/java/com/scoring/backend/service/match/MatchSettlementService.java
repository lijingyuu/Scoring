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
import com.scoring.backend.domain.entity.TournamentQualificationOverride;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.service.impl.TournamentRuleResolver;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
 */
@Service
public class MatchSettlementService {

    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;

    private final MatchRecordMapper matchRecordMapper;
    private final TournamentMapper tournamentMapper;
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
            if (Integer.valueOf(STAGE_GROUP).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            // Round robin: only end tournament when ALL matches have finished
            if (Integer.valueOf(2).equals(tournament.getTournamentType())) {
                if (!allTournamentMatchesFinished(current.getTournamentId())) {
                    return;
                }
            }
            finishTournamentIfReady(current, tournament);
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

    private void finishTournamentIfReady(MatchRecord current, Tournament tournament) {
        if (Boolean.TRUE.equals(tournament.getThirdPlaceEnabled())
                && Integer.valueOf(STAGE_KNOCKOUT).equals(current.getStageType())
                && !allTerminalKnockoutMatchesFinished(current.getTournamentId())) {
            return;
        }
        Tournament updateTournament = new Tournament();
        updateTournament.setId(current.getTournamentId());
        updateTournament.setStatus(2);
        tournamentMapper.updateById(updateTournament);
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
        markTournamentRunning(match.getTournamentId());
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

    private void markTournamentRunning(String tournamentId) {
        Tournament update = new Tournament();
        update.setId(tournamentId);
        update.setStatus(1);
        tournamentMapper.updateById(update);
    }

    private void clearQualificationOverridesIfRankingMatch(MatchRecord match) {
        if (match == null
                || (match.getStageType() != STAGE_GROUP && match.getStageType() != STAGE_TEAM_CHILD)) {
            return;
        }
        tournamentQualificationOverrideMapper.delete(
                new QueryWrapper<com.scoring.backend.domain.entity.TournamentQualificationOverride>()
                        .eq("tournament_id", match.getTournamentId())
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

    private boolean allTournamentMatchesFinished(String tournamentId) {
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
        QueryWrapper<MatchRecord> totalQuery = new QueryWrapper<MatchRecord>().eq("tournament_id", tournamentId);
        QueryWrapper<MatchRecord> finishedQuery = new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .in("status", List.of(2, 3));
        if (!childMatchIds.isEmpty()) {
            totalQuery.notIn("id", childMatchIds);
            finishedQuery.notIn("id", childMatchIds);
        }
        long total = matchRecordMapper.selectCount(totalQuery);
        long finished = matchRecordMapper.selectCount(finishedQuery);
        return finished >= total;
    }

    private boolean allTerminalKnockoutMatchesFinished(String tournamentId) {
        QueryWrapper<MatchRecord> totalQuery = new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
                .eq("stage_type", STAGE_KNOCKOUT)
                .isNull("next_match_id");
        QueryWrapper<MatchRecord> finishedQuery = new QueryWrapper<MatchRecord>()
                .eq("tournament_id", tournamentId)
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
