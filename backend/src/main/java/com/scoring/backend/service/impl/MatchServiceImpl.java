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
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentQualificationOverrideMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.match.MatchAccessGuard;
import com.scoring.backend.service.match.MatchDetailAssembler;
import com.scoring.backend.service.match.MatchReportAssembler;
import com.scoring.backend.service.match.MatchSettlementService;
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
    private final TournamentDivisionMapper tournamentDivisionMapper;
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
    private final MatchSettlementService settlementService;
    private final TournamentRuleResolver tournamentRuleResolver;

    public MatchServiceImpl(MatchRecordMapper matchRecordMapper,
                            PlayerMapper playerMapper,
                            TournamentMapper tournamentMapper,
                            TournamentDivisionMapper tournamentDivisionMapper,
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
                            MatchDetailAssembler detailAssembler,
                            MatchSettlementService settlementService) {
        this.matchRecordMapper = matchRecordMapper;
        this.playerMapper = playerMapper;
        this.tournamentMapper = tournamentMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
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
        this.settlementService = settlementService;
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
        settlementService.updateMatchResult(userId, matchId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req, String lockToken) {
        settlementService.updateMatchResult(userId, matchId, req, lockToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req) {
        settlementService.finishMatch(userId, matchId, req);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req, String lockToken) {
        settlementService.finishMatch(userId, matchId, req, lockToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId) {
        settlementService.settleTeamMatch(userId, matchId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void settleTeamMatch(String userId, String matchId, String lockToken) {
        settlementService.settleTeamMatch(userId, matchId, lockToken);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId) {
        settlementService.restartMatch(userId, matchId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void restartMatch(String userId, String matchId, String lockToken) {
        settlementService.restartMatch(userId, matchId, lockToken);
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
        vo.setDivisionId(match.getDivisionId());
        vo.setDivisionName(loadDivisionName(match.getDivisionId()));
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
        settlementService.ensureMatchPlayableForResult(match);
    }

    private void ensureMatchParticipantsReady(MatchRecord match) {
        settlementService.ensureMatchParticipantsReady(match);
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

    private static class TeamMemberScope {

        private final Set<String> memberIds = new HashSet<>();
    }
    /** 组别名（记录页返回赛程页时用于定位组别）；组别不存在时返回 null。 */
    private String loadDivisionName(String divisionId) {
        if (StrUtil.isBlank(divisionId)) {
            return null;
        }
        com.scoring.backend.domain.entity.TournamentDivision division =
                tournamentDivisionMapper.selectById(divisionId);
        return division == null ? null : division.getName();
    }
}
