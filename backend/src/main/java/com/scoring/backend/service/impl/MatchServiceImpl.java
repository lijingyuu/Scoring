package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.JsonUtils;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.MatchLockReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.domain.vo.MatchLockVO;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.domain.vo.MatchRuleConfig;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.match.MatchAccessGuard;
import com.scoring.backend.service.match.MatchDetailAssembler;
import com.scoring.backend.service.match.MatchReportAssembler;
import com.scoring.backend.service.match.MatchLockService;
import com.scoring.backend.security.ForbiddenException;
import com.scoring.backend.service.MatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl implements MatchService {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final List<String> COURT_POSITION_LABELS = List.of("4号位", "3号位", "2号位", "5号位", "6号位", "1号位");

    private static final int STAGE_GROUP = 0;
    private static final int STAGE_KNOCKOUT = 1;
    private static final int STAGE_TEAM_CHILD = 2;
    private static final Map<Integer, Integer> OPPOSITE_SLOT_MAP = Map.of(
            0, 5,
            1, 4,
            2, 3,
            3, 2,
            4, 1,
            5, 0
    );

    private final MatchRecordMapper matchRecordMapper;
    private final PlayerMapper playerMapper;
    private final TournamentMapper tournamentMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final MatchLineupConfigMapper matchLineupConfigMapper;
    private final MatchReportMetaMapper matchReportMetaMapper;
    private final MatchEventMapper matchEventMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;
    private final TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper;
    private final MatchAccessGuard matchAccessGuard;
    private final MatchLockService matchLockService;
    private final MatchReportAssembler reportAssembler;
    private final MatchDetailAssembler detailAssembler;
    private final TournamentRuleResolver tournamentRuleResolver;

    public MatchServiceImpl(MatchRecordMapper matchRecordMapper,
                            PlayerMapper playerMapper,
                            TournamentMapper tournamentMapper,
                            TournamentTeamMemberMapper tournamentTeamMemberMapper,
                            MatchLineupConfigMapper matchLineupConfigMapper,
                            MatchReportMetaMapper matchReportMetaMapper,
                            MatchEventMapper matchEventMapper,
                            TeamMatchItemMapper teamMatchItemMapper,
                            TournamentRefereeGrantMapper tournamentRefereeGrantMapper,
                            TournamentQualificationOverrideMapper tournamentQualificationOverrideMapper,
                            TournamentRuleResolver tournamentRuleResolver,
                            MatchAccessGuard matchAccessGuard,
                            MatchLockService matchLockService,
                            MatchReportAssembler reportAssembler,
                            MatchDetailAssembler detailAssembler) {
        this.matchRecordMapper = matchRecordMapper;
        this.playerMapper = playerMapper;
        this.tournamentMapper = tournamentMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.matchReportMetaMapper = matchReportMetaMapper;
        this.matchEventMapper = matchEventMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
        this.tournamentQualificationOverrideMapper = tournamentQualificationOverrideMapper;
        this.matchAccessGuard = matchAccessGuard;
        this.matchLockService = matchLockService;
        this.reportAssembler = reportAssembler;
        this.detailAssembler = detailAssembler;
        this.tournamentRuleResolver = tournamentRuleResolver;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchLockVO acquireMatchLock(String userId, String matchId, MatchLockReq req) {
        return matchLockService.acquireMatchLock(userId, matchId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MatchLockVO heartbeatMatchLock(String userId, String matchId, MatchLockReq req) {
        return matchLockService.heartbeatMatchLock(userId, matchId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void releaseMatchLock(String userId, String matchId, MatchLockReq req) {
        matchLockService.releaseMatchLock(userId, matchId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req) {
        updateMatchResultInternal(userId, matchId, req, null, false);
    }

    @Override
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

        Tournament tournament = requireMatchOperator(userId, current.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(current, userId, lockToken);
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
        clearMatchLock(matchId);

        propagateFinishedMatch(current, tournament, req.getWinnerId());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req) {
        finishMatchInternal(userId, matchId, req, null, false);
    }

    @Override
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

        Tournament tournament = requireMatchOperator(userId, current.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(current, userId, lockToken);
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
        clearMatchLock(matchId);

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId) {
        settleTeamMatchInternal(userId, matchId, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId, String lockToken) {
        settleTeamMatchInternal(userId, matchId, lockToken, true);
    }

    private void settleTeamMatchInternal(String userId, String matchId, String lockToken, boolean requireLock) {
        MatchRecord parent = requireMatchForUpdate(matchId);
        Tournament tournament = requireMatchOperator(userId, parent.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(parent, userId, lockToken);
        }
        clearQualificationOverridesIfRankingMatch(parent);
        settleParentTeamMatch(parent, tournament, true);
        clearMatchLock(matchId);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId) {
        restartMatchInternal(userId, matchId, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId, String lockToken) {
        restartMatchInternal(userId, matchId, lockToken, true);
    }

    private void restartMatchInternal(String userId, String matchId, String lockToken, boolean requireLock) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(match, userId, lockToken);
        }
        ensureReportNotSealed(matchId);
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
        ensureReportNotSealed(next.getId());

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req) {
        saveLineupConfigInternal(userId, matchId, req, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req, String lockToken) {
        saveLineupConfigInternal(userId, matchId, req, lockToken, true);
    }

    private void saveLineupConfigInternal(String userId, String matchId, SaveMatchLineupConfigReq req, String lockToken, boolean requireLock) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(match, userId, lockToken);
        }
        int gameNo = validateAndNormalizeSaveLineupReq(match, req);

        MatchLineupConfig current = findLineupConfig(matchId, gameNo);
        MatchLineupConfig entity = current == null ? new MatchLineupConfig() : current;
        entity.setMatchId(matchId);
        entity.setGameNo(gameNo);
        entity.setLeftCourtJson(JSONUtil.toJsonStr(normalizeCourt(req.getLeft().getCourt())));
        entity.setRightCourtJson(JSONUtil.toJsonStr(normalizeCourt(req.getRight().getCourt())));
        entity.setLeftMiddlePairIndexesJson(JSONUtil.toJsonStr(detailAssembler.normalizeMiddlePairIndexes(req.getLeft().getMiddlePairIndexes())));
        entity.setRightMiddlePairIndexesJson(JSONUtil.toJsonStr(detailAssembler.normalizeMiddlePairIndexes(req.getRight().getMiddlePairIndexes())));
        entity.setLeftLibero1Id(normalizeOptionalId(req.getLeft().getLibero1Id()));
        entity.setLeftLibero2Id(normalizeOptionalId(req.getLeft().getLibero2Id()));
        entity.setRightLibero1Id(normalizeOptionalId(req.getRight().getLibero1Id()));
        entity.setRightLibero2Id(normalizeOptionalId(req.getRight().getLibero2Id()));
        entity.setServeSide(normalizeServeSide(req.getServeSide()));

        if (current == null) {
            matchLineupConfigMapper.insert(entity);
        } else {
            matchLineupConfigMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchReportMeta(String userId, String matchId, SaveMatchReportMetaReq req) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireReportOperator(userId, match.getTournamentId());
        ensureMatchParticipantsReady(match);

        MatchReportMeta current = findMatchReportMeta(matchId);
        JSONObject currentJson = parseObject(current == null ? null : current.getMetaJson());
        ensureReportDraft(currentJson);
        MatchReportMeta entity = current == null ? new MatchReportMeta() : current;
        entity.setMatchId(matchId);
        entity.setMetaJson(buildReportMetaJson(req, currentJson));

        if (current == null) {
            matchReportMetaMapper.insert(entity);
        } else {
            matchReportMetaMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sealMatchReport(String userId, String matchId) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireReportOperator(userId, match.getTournamentId());
        ensureMatchParticipantsReady(match);
        if (!Integer.valueOf(2).equals(match.getStatus()) && !Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("战报只能在比赛结束后封存");
        }

        MatchReportMeta current = findMatchReportMeta(matchId);
        JSONObject root = parseObject(current == null ? null : current.getMetaJson());
        JSONObject state = reportStateObject(root);
        if ("sealed".equals(state.getStr("status"))) {
            return;
        }
        ensureReportComplete(root);

        state.set("status", "sealed");
        state.set("sealedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        state.set("sealedBy", userId);
        root.set("reportState", state);

        MatchReportMeta entity = current == null ? new MatchReportMeta() : current;
        entity.setMatchId(matchId);
        entity.setMetaJson(JSONUtil.toJsonStr(root));
        if (current == null) {
            matchReportMetaMapper.insert(entity);
        } else {
            matchReportMetaMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req) {
        saveMatchEventsInternal(userId, matchId, req, null, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req, String lockToken) {
        saveMatchEventsInternal(userId, matchId, req, lockToken, true);
    }

    private void saveMatchEventsInternal(String userId, String matchId, SaveMatchEventsReq req, String lockToken, boolean requireLock) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
        if (requireLock) {
            requireActiveMatchLock(match, userId, lockToken);
        }
        ensureMatchPlayableForResult(match);
        if (req == null || CollUtil.isEmpty(req.getEvents())) {
            throw new IllegalArgumentException("events cannot be empty");
        }

        List<SaveMatchEventsReq.EventItem> normalizedEvents = req.getEvents().stream()
                .sorted((left, right) -> Integer.compare(left.getEventSeq(), right.getEventSeq()))
                .toList();

        Set<Integer> uniqueSeqs = new HashSet<>();
        for (SaveMatchEventsReq.EventItem item : normalizedEvents) {
            if (!uniqueSeqs.add(item.getEventSeq())) {
                throw new IllegalArgumentException("eventSeq cannot repeat in one request");
            }
            validateMatchEventItem(item);
        }

        List<Integer> eventSeqs = normalizedEvents.stream()
                .map(SaveMatchEventsReq.EventItem::getEventSeq)
                .toList();
        List<MatchEvent> existingEvents = matchEventMapper.selectList(
                new QueryWrapper<MatchEvent>()
                        .eq("match_id", matchId)
                        .in("event_seq", eventSeqs)
        );
        Set<Integer> existingSeqs = existingEvents.stream()
                .map(MatchEvent::getEventSeq)
                .collect(Collectors.toSet());

        for (SaveMatchEventsReq.EventItem item : normalizedEvents) {
            if (existingSeqs.contains(item.getEventSeq())) {
                continue;
            }
            MatchEvent entity = new MatchEvent();
            entity.setMatchId(matchId);
            entity.setEventSeq(item.getEventSeq());
            entity.setEventType(StrUtil.trim(item.getEventType()));
            entity.setGameNo(item.getGameNo());
            entity.setLeftScore(item.getLeftScore());
            entity.setRightScore(item.getRightScore());
            entity.setServeSide(normalizeServeSide(item.getServeSide()));
            entity.setPayloadJson(normalizePayloadJson(item.getPayloadJson()));
            matchEventMapper.insert(entity);
        }
    }

    @Override
    public MatchLineupConfigVO getEffectiveLineupConfig(String currentUserId, String matchId, Integer gameNo) {
        MatchRecord match = requireMatch(matchId);
        requireMatchReadable(currentUserId, match);
        int targetGameNo = validateGameNo(gameNo);
        MatchReportMeta reportMeta = findMatchReportMeta(matchId);

        MatchLineupConfig exact = findLineupConfig(matchId, targetGameNo);
        if (exact != null) {
            return buildLineupConfigResponse(
                    targetGameNo,
                    true,
                    exact.getGameNo(),
                    toLineupConfigVO(exact, 0),
                    buildLineupReportMetaRecord(reportMeta)
            );
        }

        MatchLineupConfig previous = findLatestLineupConfigBefore(matchId, targetGameNo);
        if (previous == null) {
            return buildLineupConfigResponse(targetGameNo, false, null, emptyLineupConfig(), buildLineupReportMetaRecord(reportMeta));
        }

        int shiftCount = Math.max(0, targetGameNo - previous.getGameNo());
        return buildLineupConfigResponse(
                targetGameNo,
                false,
                previous.getGameNo(),
                toLineupConfigVO(previous, shiftCount),
                buildLineupReportMetaRecord(reportMeta)
        );
    }

    @Override
    public MatchRecordDetailVO getMatchRecordDetail(String currentUserId, String matchId) {
        MatchRecord match = requireMatch(matchId);
        Tournament tournament = requireMatchReadable(currentUserId, match);

        String leftPlayerId = StrUtil.trim(match.getLeftPlayerId());
        String rightPlayerId = StrUtil.trim(match.getRightPlayerId());
        List<String> participantIds = List.of(
                StrUtil.blankToDefault(leftPlayerId, ""),
                StrUtil.blankToDefault(rightPlayerId, "")
        ).stream().filter(StrUtil::isNotBlank).toList();

        Map<String, Player> participantMap = detailAssembler.loadParticipants(participantIds);
        List<TournamentTeamMember> members = detailAssembler.loadTeamMembers(tournament.getId(), participantIds);
        Map<String, List<TournamentTeamMember>> membersByParticipant = members.stream()
                .collect(Collectors.groupingBy(TournamentTeamMember::getParticipantId));
        Map<String, TournamentTeamMember> memberMap = members.stream()
                .collect(Collectors.toMap(TournamentTeamMember::getId, item -> item, (left, right) -> left));
        List<MatchEvent> events = matchEventMapper.selectList(
                new QueryWrapper<MatchEvent>()
                        .eq("match_id", matchId)
                        .orderByAsc("event_seq")
        );
        List<MatchLineupConfig> lineupConfigs = matchLineupConfigMapper.selectList(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", matchId)
                        .orderByAsc("game_no")
        );
        MatchReportMeta reportMeta = findMatchReportMeta(matchId);

        MatchRecordDetailVO vo = new MatchRecordDetailVO();
        vo.setMatchId(match.getId());
        vo.setTournamentId(tournament.getId());
        vo.setTournamentName(tournament.getName());
        vo.setLocation(tournament.getLocation());
        vo.setRoundNum(match.getRoundNum());
        vo.setMatchIndex(match.getMatchIndex());
        vo.setMatchRole(match.getMatchRole());
        vo.setStatus(match.getStatus());
        MatchRuleConfig matchRule = tournamentRuleResolver.resolveForMatch(tournament, match);
        vo.setBestOf(matchRule.getBestOf());
        vo.setGamesToWin(matchRule.getGamesToWin());
        vo.setPointsToWin(matchRule.getPointsToWin());
        vo.setDecidingPointsToWin(matchRule.getDecidingPointsToWin());
        vo.setEnableDeuce(matchRule.getEnableDeuce());
        vo.setCapPoint(matchRule.getCapPoint());
        vo.setScoreDisplay(match.getScoreDisplay());
        vo.setLeftGameWins(match.getLeftGameWins());
        vo.setRightGameWins(match.getRightGameWins());
        vo.setWinnerSide(detailAssembler.resolveWinnerSide(match));
        vo.setRetiredSide(match.getRetiredSide());
        Player leftPlayer = StrUtil.isBlank(leftPlayerId) ? null : participantMap.get(leftPlayerId);
        Player rightPlayer = StrUtil.isBlank(rightPlayerId) ? null : participantMap.get(rightPlayerId);
        List<TournamentTeamMember> leftMembers = StrUtil.isBlank(leftPlayerId) ? null : membersByParticipant.get(leftPlayerId);
        List<TournamentTeamMember> rightMembers = StrUtil.isBlank(rightPlayerId) ? null : membersByParticipant.get(rightPlayerId);
        vo.setLeft(detailAssembler.buildParticipantRecord(leftPlayer, leftMembers));
        vo.setRight(detailAssembler.buildParticipantRecord(rightPlayer, rightMembers));
        vo.setGameScores(detailAssembler.parseGameScores(match.getGameScores()));
        vo.setRosterSnapshot(detailAssembler.buildRosterSnapshot(events, vo.getLeft(), vo.getRight()));
        vo.setLineupSnapshots(detailAssembler.buildLineupSnapshots(events, lineupConfigs, memberMap));
        vo.setEvents(detailAssembler.buildEventRecords(events, match, participantMap, memberMap));
        vo.setReportMeta(buildReportMetaRecord(reportMeta));
        vo.setReportRender(detailAssembler.buildReportRender(vo, match, events, memberMap));
        return vo;
    }

    @Override
    public boolean canOperateMatch(String userId, String matchId) {
        if (StrUtil.isBlank(userId) || StrUtil.isBlank(matchId)) {
            return false;
        }
        MatchRecord match = matchRecordMapper.selectById(matchId);
        if (match == null || StrUtil.isBlank(match.getTournamentId())) {
            return false;
        }
        Tournament tournament = tournamentMapper.selectById(match.getTournamentId());
        if (tournament == null || Boolean.TRUE.equals(tournament.getArchived())) {
            return false;
        }
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

    private Tournament requireMatchOperator(String userId, String tournamentId) {
        return matchAccessGuard.requireMatchOperator(userId, tournamentId);
    }

    private Tournament requireReportOperator(String userId, String tournamentId) {
        return matchAccessGuard.requireReportOperator(userId, tournamentId);
    }

    private Tournament requireMatchReadable(String userId, MatchRecord match) {
        return matchAccessGuard.requireMatchReadable(userId, match);
    }

    private MatchRecord requireMatch(String matchId) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        MatchRecord match = matchRecordMapper.selectById(matchId);
        if (match == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }
        return match;
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

    private void requireActiveMatchLock(MatchRecord match, String userId, String lockToken) {
        matchLockService.requireActiveMatchLock(match, userId, lockToken);
    }

    private void clearMatchLock(String matchId) {
        matchLockService.clearMatchLock(matchId);
    }

    private void ensureMatchPlayableForResult(MatchRecord match) {
        if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("match already finished");
        }
        ensureMatchParticipantsReady(match);
    }

    private void ensureMatchParticipantsReady(MatchRecord match) {
        if (StrUtil.isBlank(match.getLeftPlayerId()) || StrUtil.isBlank(match.getRightPlayerId())) {
            throw new IllegalArgumentException("match participants are incomplete");
        }
    }

    private void ensureWinnerBelongsToMatch(MatchRecord match, String winnerId) {
        if (!StrUtil.equals(winnerId, match.getLeftPlayerId()) && !StrUtil.equals(winnerId, match.getRightPlayerId())) {
            throw new IllegalArgumentException("winnerId must belong to this match");
        }
    }

    private int validateAndNormalizeSaveLineupReq(MatchRecord match, SaveMatchLineupConfigReq req) {
        if (req == null) {
            throw new IllegalArgumentException("lineup config cannot be null");
        }

        int gameNo = validateGameNo(req.getGameNo());
        ensureLineupConfigEditable(match, gameNo);

        if (req.getLeft() == null || req.getRight() == null) {
            throw new IllegalArgumentException("both team lineups are required");
        }

        String leftParticipantId = StrUtil.trimToEmpty(match.getLeftPlayerId());
        String rightParticipantId = StrUtil.trimToEmpty(match.getRightPlayerId());
        if (StrUtil.isBlank(leftParticipantId) || StrUtil.isBlank(rightParticipantId)) {
            throw new IllegalStateException("match participants are not ready");
        }

        normalizeServeSide(req.getServeSide());
        Map<String, TeamMemberScope> scopes = loadTeamMemberScopes(match.getTournamentId(), leftParticipantId, rightParticipantId);
        validateTeamLineupConfig(req.getLeft(), scopes.get(leftParticipantId), "left");
        validateTeamLineupConfig(req.getRight(), scopes.get(rightParticipantId), "right");
        return gameNo;
    }

    private MatchReportMeta findMatchReportMeta(String matchId) {
        return reportAssembler.findMatchReportMeta(matchId);
    }

    private void ensureReportNotSealed(String matchId) {
        reportAssembler.ensureReportNotSealed(matchId);
    }

    private void ensureReportDraft(JSONObject root) {
        reportAssembler.ensureReportDraft(root);
    }

    private void ensureReportComplete(JSONObject root) {
        reportAssembler.ensureReportComplete(root);
    }

    private JSONObject reportStateObject(JSONObject root) {
        return reportAssembler.reportStateObject(root);
    }

    private String buildReportMetaJson(SaveMatchReportMetaReq req, JSONObject current) {
        return reportAssembler.buildReportMetaJson(req, current);
    }

    private MatchRecordDetailVO.ReportMetaRecord buildReportMetaRecord(MatchReportMeta entity) {
        return reportAssembler.buildReportMetaRecord(entity);
    }

    private int validateGameNo(Integer gameNo) {
        int normalized = gameNo == null ? 0 : gameNo;
        if (normalized <= 0) {
            throw new IllegalArgumentException("gameNo must be greater than 0");
        }
        return normalized;
    }

    private void ensureLineupConfigEditable(MatchRecord match, int gameNo) {
        if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("match already finished");
        }
        if (StrUtil.isBlank(match.getLeftPlayerId()) || StrUtil.isBlank(match.getRightPlayerId())) {
            throw new IllegalArgumentException("match participants are incomplete");
        }

        int completedGameCount = countCompletedGames(match.getGameScores());
        if (gameNo <= completedGameCount) {
            throw new IllegalArgumentException("this game is already completed and locked");
        }

        int latestSavedGameNo = findLatestSavedGameNo(match.getId());
        if (latestSavedGameNo > gameNo) {
            throw new IllegalArgumentException("this game is already locked by later lineup config");
        }
    }

    private int countCompletedGames(String gameScoresJson) {
        if (StrUtil.isBlank(gameScoresJson)) {
            return 0;
        }
        try {
            return JSONUtil.parseArray(gameScoresJson).size();
        } catch (Exception ex) {
            return 0;
        }
    }

    private int findLatestSavedGameNo(String matchId) {
        List<MatchLineupConfig> configs = matchLineupConfigMapper.selectList(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", matchId)
                        .orderByDesc("game_no")
        );
        if (CollUtil.isEmpty(configs) || configs.get(0).getGameNo() == null) {
            return 0;
        }
        return configs.get(0).getGameNo();
    }

    private MatchLineupConfig findLineupConfig(String matchId, Integer gameNo) {
        return matchLineupConfigMapper.selectOne(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", matchId)
                        .eq("game_no", gameNo)
        );
    }

    private MatchLineupConfig findLatestLineupConfigBefore(String matchId, int targetGameNo) {
        List<MatchLineupConfig> configs = matchLineupConfigMapper.selectList(
                new QueryWrapper<MatchLineupConfig>()
                        .eq("match_id", matchId)
                        .lt("game_no", targetGameNo)
                        .orderByDesc("game_no")
        );
        return CollUtil.isEmpty(configs) ? null : configs.get(0);
    }

    private Map<String, TeamMemberScope> loadTeamMemberScopes(String tournamentId,
                                                              String leftParticipantId,
                                                              String rightParticipantId) {
        List<TournamentTeamMember> members = tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("tournament_id", tournamentId)
                        .in("participant_id", List.of(leftParticipantId, rightParticipantId))
        );

        Map<String, TeamMemberScope> scopes = new HashMap<>();
        scopes.put(leftParticipantId, new TeamMemberScope());
        scopes.put(rightParticipantId, new TeamMemberScope());
        for (TournamentTeamMember member : members) {
            TeamMemberScope scope = scopes.get(member.getParticipantId());
            if (scope != null) {
                scope.memberIds.add(member.getId());
            }
        }
        return scopes;
    }

    private void validateTeamLineupConfig(SaveMatchLineupConfigReq.TeamLineupConfig config,
                                          TeamMemberScope scope,
                                          String sideLabel) {
        if (scope == null) {
            throw new IllegalStateException(sideLabel + " team members not found");
        }

        List<String> court = normalizeCourt(config.getCourt());
        Set<String> onCourt = new HashSet<>(court);
        for (String memberId : court) {
            if (!scope.memberIds.contains(memberId)) {
                throw new IllegalArgumentException(sideLabel + " court has member outside team");
            }
        }

        String libero1Id = normalizeOptionalId(config.getLibero1Id());
        String libero2Id = normalizeOptionalId(config.getLibero2Id());
        boolean hasLiberoBinding = StrUtil.isNotBlank(libero1Id) || StrUtil.isNotBlank(libero2Id);

        List<Integer> pairIndexes = detailAssembler.normalizeMiddlePairIndexes(config.getMiddlePairIndexes());
        if (hasLiberoBinding) {
            if (pairIndexes.size() != 2 || !isOppositePair(pairIndexes)) {
                throw new IllegalArgumentException(sideLabel + " middle pair must be a valid opposite pair");
            }
        } else if (!pairIndexes.isEmpty() && !isOppositePair(pairIndexes)) {
            throw new IllegalArgumentException(sideLabel + " middle pair must be a valid opposite pair");
        }

        validateLiberoMember(sideLabel, "libero1", libero1Id, scope, onCourt);
        validateLiberoMember(sideLabel, "libero2", libero2Id, scope, onCourt);
    }

    private List<String> normalizeCourt(List<String> rawCourt) {
        if (rawCourt == null || rawCourt.size() != 6) {
            throw new IllegalArgumentException("each team court must have exactly 6 members");
        }

        List<String> court = new ArrayList<>(6);
        Set<String> uniqueIds = new HashSet<>();
        for (String memberId : rawCourt) {
            String normalized = StrUtil.trimToEmpty(memberId);
            if (StrUtil.isBlank(normalized)) {
                throw new IllegalArgumentException("court member cannot be blank");
            }
            if (!uniqueIds.add(normalized)) {
                throw new IllegalArgumentException("court members cannot repeat");
            }
            court.add(normalized);
        }
        return court;
    }

    private boolean isOppositePair(List<Integer> indexes) {
        return indexes != null
                && indexes.size() == 2
                && OPPOSITE_SLOT_MAP.get(indexes.get(0)) != null
                && OPPOSITE_SLOT_MAP.get(indexes.get(0)).equals(indexes.get(1));
    }

    private void validateLiberoMember(String sideLabel,
                                      String liberoLabel,
                                      String memberId,
                                      TeamMemberScope scope,
                                      Set<String> onCourt) {
        if (StrUtil.isBlank(memberId)) {
            return;
        }
        if (!scope.memberIds.contains(memberId)) {
            throw new IllegalArgumentException(sideLabel + " " + liberoLabel + " must belong to this team");
        }
        if (onCourt.contains(memberId)) {
            throw new IllegalArgumentException(sideLabel + " " + liberoLabel + " cannot be in starting six");
        }
    }

    private String normalizeServeSide(String serveSide) {
        String normalized = StrUtil.trimToEmpty(serveSide);
        if (!"left".equals(normalized) && !"right".equals(normalized)) {
            throw new IllegalArgumentException("serveSide must be left or right");
        }
        return normalized;
    }

    private String normalizeOptionalId(String rawId) {
        return StrUtil.blankToDefault(StrUtil.trim(rawId), null);
    }

    private void validateMatchEventItem(SaveMatchEventsReq.EventItem item) {
        if (item == null) {
            throw new IllegalArgumentException("event item cannot be null");
        }
        if (item.getLeftScore() == null || item.getLeftScore() < 0) {
            throw new IllegalArgumentException("leftScore cannot be negative");
        }
        if (item.getRightScore() == null || item.getRightScore() < 0) {
            throw new IllegalArgumentException("rightScore cannot be negative");
        }
        validateGameNo(item.getGameNo());
        normalizeServeSide(item.getServeSide());
        String eventType = StrUtil.trimToEmpty(item.getEventType());
        if (!Set.of("roster_snapshot", "lineup_snapshot", "score_snapshot", "timeout", "substitution", "captain_change", "side_switch").contains(eventType)) {
            throw new IllegalArgumentException("eventType is invalid");
        }
        normalizePayloadJson(item.getPayloadJson());
    }

    private String normalizePayloadJson(String payloadJson) {
        String normalized = StrUtil.trimToEmpty(payloadJson);
        if (StrUtil.isBlank(normalized)) {
            throw new IllegalArgumentException("payloadJson cannot be blank");
        }
        try {
            JSONUtil.parse(normalized);
            return normalized;
        } catch (Exception ex) {
            throw new IllegalArgumentException("payloadJson must be valid json");
        }
    }

    private JSONObject parseObject(String json) {
        return JsonUtils.parseObject(json);
    }

    private MatchLineupConfigVO buildLineupConfigResponse(int gameNo,
                                                          boolean exists,
                                                          Integer effectiveFromGameNo,
                                                          MatchLineupConfigVO.LineupConfig config,
                                                          MatchLineupConfigVO.ReportMetaRecord reportMeta) {
        MatchLineupConfigVO vo = new MatchLineupConfigVO();
        vo.setGameNo(gameNo);
        vo.setExists(exists);
        vo.setEffectiveFromGameNo(effectiveFromGameNo);
        vo.setConfig(config);
        vo.setReportMeta(reportMeta);
        return vo;
    }

    private MatchLineupConfigVO.ReportMetaRecord buildLineupReportMetaRecord(MatchReportMeta entity) {
        JSONObject object = parseObject(entity == null ? null : entity.getMetaJson());
        MatchLineupConfigVO.ReportMetaRecord record = new MatchLineupConfigVO.ReportMetaRecord();
        record.setMatchTimeText(StrUtil.trimToEmpty(object.getStr("matchTimeText")));
        record.setChiefRefereeName(StrUtil.trimToEmpty(object.getStr("chiefRefereeName")));
        record.setAssistantRefereeName(StrUtil.trimToEmpty(object.getStr("assistantRefereeName")));
        return record;
    }

    private MatchLineupConfigVO.LineupConfig emptyLineupConfig() {
        MatchLineupConfigVO.LineupConfig config = new MatchLineupConfigVO.LineupConfig();
        config.setServeSide("left");
        config.setLeft(buildTeamLineupConfig(List.of("", "", "", "", "", ""), List.of(), null, null));
        config.setRight(buildTeamLineupConfig(List.of("", "", "", "", "", ""), List.of(), null, null));
        return config;
    }

    private MatchLineupConfigVO.LineupConfig toLineupConfigVO(MatchLineupConfig entity, int serveShiftCount) {
        MatchLineupConfigVO.LineupConfig config = new MatchLineupConfigVO.LineupConfig();
        config.setServeSide(shiftServeSide(entity.getServeSide(), serveShiftCount));
        config.setLeft(buildTeamLineupConfig(
                detailAssembler.normalizeCourtForResponse(detailAssembler.parseStringList(entity.getLeftCourtJson())),
                detailAssembler.normalizeMiddlePairIndexesForResponse(detailAssembler.parseIntegerList(entity.getLeftMiddlePairIndexesJson())),
                entity.getLeftLibero1Id(),
                entity.getLeftLibero2Id()
        ));
        config.setRight(buildTeamLineupConfig(
                detailAssembler.normalizeCourtForResponse(detailAssembler.parseStringList(entity.getRightCourtJson())),
                detailAssembler.normalizeMiddlePairIndexesForResponse(detailAssembler.parseIntegerList(entity.getRightMiddlePairIndexesJson())),
                entity.getRightLibero1Id(),
                entity.getRightLibero2Id()
        ));
        return config;
    }

    private MatchLineupConfigVO.TeamLineupConfig buildTeamLineupConfig(List<String> court,
                                                                       List<Integer> middlePairIndexes,
                                                                       String libero1Id,
                                                                       String libero2Id) {
        MatchLineupConfigVO.TeamLineupConfig config = new MatchLineupConfigVO.TeamLineupConfig();
        config.setCourt(court);
        config.setMiddlePairIndexes(middlePairIndexes == null ? List.of() : middlePairIndexes);
        config.setLibero1Id(StrUtil.blankToDefault(StrUtil.trim(libero1Id), ""));
        config.setLibero2Id(StrUtil.blankToDefault(StrUtil.trim(libero2Id), ""));
        return config;
    }

    private String shiftServeSide(String serveSide, int shiftCount) {
        String current = normalizeServeSide(serveSide);
        if (shiftCount % 2 == 0) {
            return current;
        }
        return "left".equals(current) ? "right" : "left";
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

    private static class TeamMemberScope {

        private final Set<String> memberIds = new HashSet<>();
    }
}
