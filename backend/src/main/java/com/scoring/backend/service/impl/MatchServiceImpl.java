package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.SaveMatchReportMetaReq;
import com.scoring.backend.domain.dto.SaveMatchThemeConfigReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.MatchReportMeta;
import com.scoring.backend.domain.entity.MatchThemeConfig;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentRefereeGrant;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.domain.vo.MatchThemeConfigVO;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.MatchReportMetaMapper;
import com.scoring.backend.mapper.MatchThemeConfigMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentRefereeGrantMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.MatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    private static final List<String> MATCH_THEME_KEYS = List.of(
            "themeBase",
            "themeBaseDeep",
            "themeAccent",
            "themeAccentInk",
            "captain",
            "courtSurface",
            "rightScoreAccent",
            "dangerAccent",
            "textStrong",
            "surfaceGlass",
            "shadowColor",
            "overlayMask",
            "courtSlotAccent",
            "rotationPanelSurface"
    );
    private static final String THEME_DEVICE_PHONE = "phone";
    private static final String THEME_DEVICE_PAD = "pad";
    private static final String THEME_MODE_DARK = "dark";
    private static final String THEME_MODE_LIGHT = "light";
    private static final String THEME_LEGACY_KEY = "theme";

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
    // ==== 已废弃：配色改为前端硬编码直选 ====
    // private final MatchThemeConfigMapper matchThemeConfigMapper;
    private final MatchEventMapper matchEventMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentRefereeGrantMapper tournamentRefereeGrantMapper;

    public MatchServiceImpl(MatchRecordMapper matchRecordMapper,
                            PlayerMapper playerMapper,
                            TournamentMapper tournamentMapper,
                            TournamentTeamMemberMapper tournamentTeamMemberMapper,
                            MatchLineupConfigMapper matchLineupConfigMapper,
                            MatchReportMetaMapper matchReportMetaMapper,
                            // ==== 已废弃：配色改为前端硬编码直选 ====
                            // MatchThemeConfigMapper matchThemeConfigMapper,
                            MatchEventMapper matchEventMapper,
                            TeamMatchItemMapper teamMatchItemMapper,
                            TournamentRefereeGrantMapper tournamentRefereeGrantMapper) {
        this.matchRecordMapper = matchRecordMapper;
        this.playerMapper = playerMapper;
        this.tournamentMapper = tournamentMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.matchReportMetaMapper = matchReportMetaMapper;
        // ==== 已废弃：配色改为前端硬编码直选 ====
        // this.matchThemeConfigMapper = matchThemeConfigMapper;
        this.matchEventMapper = matchEventMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.tournamentRefereeGrantMapper = tournamentRefereeGrantMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateMatchResult(String userId, String matchId, UpdateScoreReq req) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerId())) {
            throw new IllegalArgumentException("winnerId cannot be blank");
        }

        MatchRecord current = requireMatchForUpdate(matchId);

        Tournament tournament = requireMatchOperator(userId, current.getTournamentId());
        ensureMatchPlayableForResult(current);
        ensureWinnerBelongsToMatch(current, req.getWinnerId());

        MatchRecord updateCurrent = new MatchRecord();
        updateCurrent.setId(matchId);
        updateCurrent.setScoreDisplay(req.getScoreDisplay());
        updateCurrent.setWinnerId(req.getWinnerId());
        updateCurrent.setStatus(2);
        matchRecordMapper.updateById(updateCurrent);

        if (StrUtil.isBlank(current.getNextMatchId())) {
            if (Integer.valueOf(0).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            // Round robin: only end tournament when ALL matches have finished
            if (Integer.valueOf(2).equals(tournament.getTournamentType())) {
                if (!allTournamentMatchesFinished(current.getTournamentId())) {
                    return;
                }
            }
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
            return;
        }

        MatchRecord next = matchRecordMapper.selectByIdForUpdate(current.getNextMatchId());
        if (next == null) {
            throw new IllegalStateException("next match not found: " + current.getNextMatchId());
        }

        MatchRecord updateNext = new MatchRecord();
        updateNext.setId(next.getId());
        if ("left".equals(current.getNextMatchSlot())) {
            updateNext.setLeftPlayerId(req.getWinnerId());
        } else if ("right".equals(current.getNextMatchSlot())) {
            updateNext.setRightPlayerId(req.getWinnerId());
        } else {
            throw new IllegalStateException("invalid nextMatchSlot: " + current.getNextMatchSlot());
        }

        matchRecordMapper.updateById(updateNext);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishMatch(String userId, String matchId, FinishMatchReq req) {
        if (StrUtil.isBlank(matchId)) {
            throw new IllegalArgumentException("matchId cannot be blank");
        }
        if (req == null || StrUtil.isBlank(req.getWinnerSide())) {
            throw new IllegalArgumentException("winnerSide cannot be blank");
        }

        MatchRecord current = requireMatchForUpdate(matchId);

        Tournament tournament = requireMatchOperator(userId, current.getTournamentId());
        ensureMatchPlayableForResult(current);

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

        validateFinishReq(req, tournament);
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
        MatchRecord parent = requireMatchForUpdate(matchId);
        Tournament tournament = requireMatchOperator(userId, parent.getTournamentId());
        settleParentTeamMatch(parent, tournament, true);
    }

    private void propagateFinishedMatch(MatchRecord current, Tournament tournament, String winnerId) {
        if (StrUtil.isBlank(current.getNextMatchId())) {
            if (Integer.valueOf(0).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            // Round robin: only end tournament when ALL matches have finished
            if (Integer.valueOf(2).equals(tournament.getTournamentType())) {
                if (!allTournamentMatchesFinished(current.getTournamentId())) {
                    return;
                }
            }
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
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
        boolean earlyKnockout = Integer.valueOf(1).equals(parent.getStageType())
                && (score.leftWins >= 3 || score.rightWins >= 3);
        if (directSettlement) {
            if (!allFinished && !earlyKnockout) {
                throw new IllegalStateException("knockout team match requires one side to win 3 items before early settlement");
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
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());

        clearDownstreamAfterRestart(match);
        clearMatchArtifacts(matchId);
        resetMatchResult(matchId);
        markTournamentRunning(match.getTournamentId());
    }

    private void clearDownstreamAfterRestart(MatchRecord source) {
        if (source == null || StrUtil.isBlank(source.getNextMatchId()) || StrUtil.isBlank(source.getNextMatchSlot())) {
            return;
        }

        MatchRecord next = matchRecordMapper.selectByIdForUpdate(source.getNextMatchId());
        if (next == null) {
            throw new IllegalStateException("next match not found: " + source.getNextMatchId());
        }

        boolean nextWinnerWasPropagated = StrUtil.isNotBlank(next.getWinnerId());
        clearMatchArtifacts(next.getId());
        resetMatchResult(next.getId());
        clearParticipantSlot(next.getId(), source.getNextMatchSlot());

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
        int gameNo = validateAndNormalizeSaveLineupReq(match, req);

        MatchLineupConfig current = findLineupConfig(matchId, gameNo);
        MatchLineupConfig entity = current == null ? new MatchLineupConfig() : current;
        entity.setMatchId(matchId);
        entity.setGameNo(gameNo);
        entity.setLeftCourtJson(JSONUtil.toJsonStr(normalizeCourt(req.getLeft().getCourt())));
        entity.setRightCourtJson(JSONUtil.toJsonStr(normalizeCourt(req.getRight().getCourt())));
        entity.setLeftMiddlePairIndexesJson(JSONUtil.toJsonStr(normalizeMiddlePairIndexes(req.getLeft().getMiddlePairIndexes())));
        entity.setRightMiddlePairIndexesJson(JSONUtil.toJsonStr(normalizeMiddlePairIndexes(req.getRight().getMiddlePairIndexes())));
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

    // ==== 已废弃：配色改为前端硬编码直选，不再从后端存取 ====
    // @Override
    // @Transactional(rollbackFor = Exception.class)
    // public void saveMatchThemeConfig(String userId, String matchId, SaveMatchThemeConfigReq req) {
    //     MatchRecord match = requireMatch(matchId);
    //     requireMatchOperator(userId, match.getTournamentId());
    //
    //     Map<String, String> normalizedTheme = normalizeThemeConfig(req == null ? null : req.getTheme(), true);
    //     String themeDevice = normalizeThemeDevice(req == null ? null : req.getDevice());
    //     String themeMode = normalizeThemeMode(req == null ? null : req.getMode());
    //     MatchThemeConfig current = findMatchThemeConfig(matchId);
    //     MatchThemeConfig entity = current == null ? new MatchThemeConfig() : current;
    //     entity.setMatchId(matchId);
    //     JSONObject themeConfig = parseThemeConfigObject(current == null ? null : current.getThemeJson());
    //     JSONObject deviceThemeConfig = normalizeThemeDeviceConfig(themeConfig.get(themeDevice));
    //     deviceThemeConfig.set(themeMode, normalizedTheme);
    //     themeConfig.set(themeDevice, deviceThemeConfig);
    //     entity.setThemeJson(JSONUtil.toJsonStr(themeConfig));
    //
    //     if (current == null) {
    //         matchThemeConfigMapper.insert(entity);
    //     } else {
    //         matchThemeConfigMapper.updateById(entity);
    //     }
    // }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchReportMeta(String userId, String matchId, SaveMatchReportMetaReq req) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
        ensureMatchPlayableForResult(match);

        MatchReportMeta current = findMatchReportMeta(matchId);
        MatchReportMeta entity = current == null ? new MatchReportMeta() : current;
        entity.setMatchId(matchId);
        entity.setMetaJson(buildReportMetaJson(req));

        if (current == null) {
            matchReportMetaMapper.insert(entity);
        } else {
            matchReportMetaMapper.updateById(entity);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req) {
        MatchRecord match = requireMatchForUpdate(matchId);
        requireMatchOperator(userId, match.getTournamentId());
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

    // ==== 已废弃：配色改为前端硬编码直选，不再从后端存取 ====
    // @Override
    // public MatchThemeConfigVO getMatchThemeConfig(String matchId) {
    //     requireMatch(matchId);
    //     MatchThemeConfig current = findMatchThemeConfig(matchId);
    //     if (current == null) {
    //         return null;
    //     }
    //
    //     JSONObject themeConfig = parseThemeConfigObject(current.getThemeJson());
    //     Map<String, String> legacyTheme = parseThemeConfigMap(themeConfig.getJSONObject(THEME_LEGACY_KEY));
    //     Map<String, String> phoneTheme = extractThemeDraft(themeConfig, THEME_DEVICE_PHONE, THEME_MODE_DARK);
    //     Map<String, String> phoneLightTheme = extractThemeDraft(themeConfig, THEME_DEVICE_PHONE, THEME_MODE_LIGHT);
    //     Map<String, String> padTheme = extractThemeDraft(themeConfig, THEME_DEVICE_PAD, THEME_MODE_DARK);
    //     Map<String, String> padLightTheme = extractThemeDraft(themeConfig, THEME_DEVICE_PAD, THEME_MODE_LIGHT);
    //
    //     MatchThemeConfigVO vo = new MatchThemeConfigVO();
    //     vo.setMatchId(matchId);
    //     vo.setTheme(legacyTheme);
    //     vo.setPhoneTheme(phoneTheme.isEmpty() ? null : phoneTheme);
    //     vo.setPhoneLightTheme(phoneLightTheme.isEmpty() ? null : phoneLightTheme);
    //     vo.setPadTheme(padTheme.isEmpty() ? null : padTheme);
    //     vo.setPadLightTheme(padLightTheme.isEmpty() ? null : padLightTheme);
    //     return vo;
    // }

    @Override
    public MatchRecordDetailVO getMatchRecordDetail(String currentUserId, String matchId) {
        MatchRecord match = requireMatch(matchId);
        Tournament tournament = requireMatchReadable(currentUserId, match);

        List<String> participantIds = List.of(
                StrUtil.blankToDefault(StrUtil.trim(match.getLeftPlayerId()), ""),
                StrUtil.blankToDefault(StrUtil.trim(match.getRightPlayerId()), "")
        ).stream().filter(StrUtil::isNotBlank).toList();

        Map<String, Player> participantMap = loadParticipants(participantIds);
        List<TournamentTeamMember> members = loadTeamMembers(tournament.getId(), participantIds);
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
        vo.setStatus(match.getStatus());
        vo.setBestOf(tournament.getBestOf());
        vo.setGamesToWin(tournament.getGamesToWin());
        vo.setPointsToWin(tournament.getPointsToWin());
        vo.setEnableDeuce(tournament.getEnableDeuce());
        vo.setCapPoint(tournament.getCapPoint());
        vo.setScoreDisplay(match.getScoreDisplay());
        vo.setLeftGameWins(match.getLeftGameWins());
        vo.setRightGameWins(match.getRightGameWins());
        vo.setWinnerSide(resolveWinnerSide(match));
        vo.setRetiredSide(match.getRetiredSide());
        vo.setLeft(buildParticipantRecord(participantMap.get(match.getLeftPlayerId()), membersByParticipant.get(match.getLeftPlayerId())));
        vo.setRight(buildParticipantRecord(participantMap.get(match.getRightPlayerId()), membersByParticipant.get(match.getRightPlayerId())));
        vo.setGameScores(parseGameScores(match.getGameScores()));
        vo.setRosterSnapshot(buildRosterSnapshot(events, vo.getLeft(), vo.getRight()));
        vo.setLineupSnapshots(buildLineupSnapshots(events, lineupConfigs, memberMap));
        vo.setEvents(buildEventRecords(events, match, participantMap, memberMap));
        vo.setReportMeta(buildReportMetaRecord(reportMeta));
        vo.setReportRender(buildReportRender(vo, match, events, memberMap));
        return vo;
    }

    private Tournament requireMatchOperator(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        if (Boolean.TRUE.equals(tournament.getArchived())) {
            throw new IllegalStateException("archived tournament is read-only");
        }
        if (StrUtil.equals(userId, tournament.getCreatorUserId())) {
            return tournament;
        }
        Long refereeCount = tournamentRefereeGrantMapper.selectCount(
                new QueryWrapper<TournamentRefereeGrant>()
                        .eq("tournament_id", tournamentId)
                        .eq("user_id", userId)
        );
        if (refereeCount > 0) {
            return tournament;
        }
        throw new IllegalArgumentException("only creator or referee can modify this match");
    }

    private Tournament requireMatchReadable(String userId, MatchRecord match) {
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
        throw new IllegalArgumentException("archived tournament is only visible to creator");
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

    private void ensureMatchPlayableForResult(MatchRecord match) {
        if (Integer.valueOf(2).equals(match.getStatus()) || Integer.valueOf(3).equals(match.getStatus())) {
            throw new IllegalArgumentException("match already finished");
        }
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

    // ==== 已废弃：配色改为前端硬编码直选 ====
    // private MatchThemeConfig findMatchThemeConfig(String matchId) {
    //     return matchThemeConfigMapper.selectOne(
    //             new QueryWrapper<MatchThemeConfig>().eq("match_id", matchId)
    //     );
    // }

    private MatchReportMeta findMatchReportMeta(String matchId) {
        return matchReportMetaMapper.selectOne(
                new QueryWrapper<MatchReportMeta>().eq("match_id", matchId)
        );
    }

    private String buildReportMetaJson(SaveMatchReportMetaReq req) {
        JSONObject root = new JSONObject();
        root.set("matchTypeLabel", StrUtil.trimToEmpty(req == null ? null : req.getMatchTypeLabel()));
        root.set("matchTimeText", StrUtil.trimToEmpty(req == null ? null : req.getMatchTimeText()));
        root.set("chiefRefereeName", StrUtil.trimToEmpty(req == null ? null : req.getChiefRefereeName()));
        root.set("assistantRefereeName", StrUtil.trimToEmpty(req == null ? null : req.getAssistantRefereeName()));
        root.set("notes", StrUtil.trimToEmpty(req == null ? null : req.getNotes()));

        JSONObject initialCoinToss = new JSONObject();
        initialCoinToss.set("enabled", true);
        initialCoinToss.set("serveTeam", normalizeReportTeamLabel(req == null ? null : req.getInitialCoinTossServeTeam()));
        initialCoinToss.set("chooseSideTeam", normalizeReportTeamLabel(req == null ? null : req.getInitialCoinTossChooseSideTeam()));
        root.set("initialCoinToss", initialCoinToss);

        JSONObject decidingSetCoinToss = new JSONObject();
        decidingSetCoinToss.set("enabled", Boolean.TRUE.equals(req == null ? null : req.getDecidingSetCoinTossEnabled()));
        decidingSetCoinToss.set("serveTeam", normalizeReportTeamLabel(req == null ? null : req.getDecidingSetCoinTossServeTeam()));
        decidingSetCoinToss.set("chooseSideTeam", normalizeReportTeamLabel(req == null ? null : req.getDecidingSetCoinTossChooseSideTeam()));
        root.set("decidingSetCoinToss", decidingSetCoinToss);

        JSONObject signatures = new JSONObject();
        signatures.set("aCaptainLabel", StrUtil.blankToDefault(StrUtil.trim(req == null ? null : req.getACaptainLabel()), "A队队长"));
        signatures.set("bCaptainLabel", StrUtil.blankToDefault(StrUtil.trim(req == null ? null : req.getBCaptainLabel()), "B队队长"));
        signatures.set("chiefRefereeLabel", StrUtil.blankToDefault(StrUtil.trim(req == null ? null : req.getChiefRefereeLabel()), "主裁"));
        signatures.set("assistantRefereeLabel", StrUtil.blankToDefault(StrUtil.trim(req == null ? null : req.getAssistantRefereeLabel()), "副裁"));
        signatures.set("chiefRefereeName", StrUtil.trimToEmpty(req == null ? null : req.getChiefRefereeName()));
        signatures.set("assistantRefereeName", StrUtil.trimToEmpty(req == null ? null : req.getAssistantRefereeName()));
        root.set("signatures", signatures);
        return JSONUtil.toJsonStr(root);
    }

    private MatchRecordDetailVO.ReportMetaRecord buildReportMetaRecord(MatchReportMeta entity) {
        JSONObject object = parseObject(entity == null ? null : entity.getMetaJson());
        MatchRecordDetailVO.ReportMetaRecord record = new MatchRecordDetailVO.ReportMetaRecord();
        record.setMatchTypeLabel(StrUtil.trimToEmpty(object.getStr("matchTypeLabel")));
        record.setMatchTimeText(StrUtil.trimToEmpty(object.getStr("matchTimeText")));
        record.setChiefRefereeName(StrUtil.trimToEmpty(object.getStr("chiefRefereeName")));
        record.setAssistantRefereeName(StrUtil.trimToEmpty(object.getStr("assistantRefereeName")));
        record.setNotes(StrUtil.trimToEmpty(object.getStr("notes")));
        record.setInitialCoinToss(buildCoinTossRecord(object.getJSONObject("initialCoinToss"), true));
        record.setDecidingSetCoinToss(buildCoinTossRecord(object.getJSONObject("decidingSetCoinToss"), false));
        record.setSignatures(buildSignatureRecord(object.getJSONObject("signatures")));
        return record;
    }

    private MatchRecordDetailVO.CoinTossRecord buildCoinTossRecord(JSONObject object, boolean defaultEnabled) {
        MatchRecordDetailVO.CoinTossRecord record = new MatchRecordDetailVO.CoinTossRecord();
        record.setEnabled(object == null ? defaultEnabled : Boolean.TRUE.equals(object.getBool("enabled", defaultEnabled)));
        record.setServeTeam(normalizeReportTeamLabel(object == null ? null : object.getStr("serveTeam")));
        record.setChooseSideTeam(normalizeReportTeamLabel(object == null ? null : object.getStr("chooseSideTeam")));
        return record;
    }

    private MatchRecordDetailVO.SignatureRecord buildSignatureRecord(JSONObject object) {
        MatchRecordDetailVO.SignatureRecord record = new MatchRecordDetailVO.SignatureRecord();
        record.setACaptainLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("aCaptainLabel")), "A队队长"));
        record.setBCaptainLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("bCaptainLabel")), "B队队长"));
        record.setChiefRefereeLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("chiefRefereeLabel")), "主裁"));
        record.setAssistantRefereeLabel(StrUtil.blankToDefault(StrUtil.trim(object == null ? null : object.getStr("assistantRefereeLabel")), "副裁"));
        record.setChiefRefereeName(StrUtil.trimToEmpty(object == null ? null : object.getStr("chiefRefereeName")));
        record.setAssistantRefereeName(StrUtil.trimToEmpty(object == null ? null : object.getStr("assistantRefereeName")));
        return record;
    }

    private MatchRecordDetailVO.ReportRenderRecord buildReportRender(MatchRecordDetailVO source,
                                                                     MatchRecord match,
                                                                     List<MatchEvent> events,
                                                                     Map<String, TournamentTeamMember> memberMap) {
        MatchRecordDetailVO.ReportRenderRecord render = new MatchRecordDetailVO.ReportRenderRecord();
        render.setHeader(buildReportHeader(source));
        render.setRoster(buildRosterRender(source));
        render.setCoinTossBlocks(buildCoinTossBlocks(source));
        render.setGames(buildGameRenderRecords(source, match, events, memberMap));
        render.setSignatures(buildRenderSignatureRecord(source.getReportMeta()));
        render.setNotes(source.getReportMeta() == null ? "" : StrUtil.trimToEmpty(source.getReportMeta().getNotes()));
        return render;
    }

    private MatchRecordDetailVO.SignatureRecord buildRenderSignatureRecord(MatchRecordDetailVO.ReportMetaRecord meta) {
        MatchRecordDetailVO.SignatureRecord record = meta == null ? buildSignatureRecord(null) : buildSignatureRecord(meta.getSignatures() == null ? null : toSignatureJson(meta.getSignatures()));
        if (meta != null) {
            record.setChiefRefereeName(StrUtil.trimToEmpty(meta.getChiefRefereeName()));
            record.setAssistantRefereeName(StrUtil.trimToEmpty(meta.getAssistantRefereeName()));
        }
        return record;
    }

    private JSONObject toSignatureJson(MatchRecordDetailVO.SignatureRecord signature) {
        JSONObject object = new JSONObject();
        object.set("aCaptainLabel", signature.getACaptainLabel());
        object.set("bCaptainLabel", signature.getBCaptainLabel());
        object.set("chiefRefereeLabel", signature.getChiefRefereeLabel());
        object.set("assistantRefereeLabel", signature.getAssistantRefereeLabel());
        object.set("chiefRefereeName", signature.getChiefRefereeName());
        object.set("assistantRefereeName", signature.getAssistantRefereeName());
        return object;
    }

    private MatchRecordDetailVO.HeaderRecord buildReportHeader(MatchRecordDetailVO source) {
        MatchRecordDetailVO.HeaderRecord header = new MatchRecordDetailVO.HeaderRecord();
        header.setTournamentName(StrUtil.trimToEmpty(source.getTournamentName()));
        header.setMatchTypeLabel(source.getReportMeta() == null ? "" : StrUtil.trimToEmpty(source.getReportMeta().getMatchTypeLabel()));
        header.setMatchTimeText(source.getReportMeta() == null ? "" : StrUtil.trimToEmpty(source.getReportMeta().getMatchTimeText()));
        header.setLeftTeamName(source.getLeft() == null ? "A队" : StrUtil.blankToDefault(StrUtil.trim(source.getLeft().getName()), "A队"));
        header.setRightTeamName(source.getRight() == null ? "B队" : StrUtil.blankToDefault(StrUtil.trim(source.getRight().getName()), "B队"));
        header.setLeftGameWins(safeNonNegativeInt(source.getLeftGameWins()));
        header.setRightGameWins(safeNonNegativeInt(source.getRightGameWins()));
        header.setTeamSummaryText(buildTeamSummaryText(header));
        header.setScoreWinnerText(buildScoreWinnerText(source.getWinnerSide()));
        header.setScoreSummaryText(buildScoreSummaryText(header));
        header.setGameScores(source.getGameScores() == null ? List.of() : source.getGameScores());
        return header;
    }

    private String buildTeamSummaryText(MatchRecordDetailVO.HeaderRecord header) {
        return "A队：" + StrUtil.blankToDefault(header.getLeftTeamName(), "A队")
                + " / B队：" + StrUtil.blankToDefault(header.getRightTeamName(), "B队");
    }

    private String buildScoreWinnerText(String winnerSide) {
        return switch (StrUtil.trimToEmpty(winnerSide)) {
            case "left" -> "A队获胜";
            case "right" -> "B队获胜";
            default -> "胜方待确认";
        };
    }

    private String buildScoreSummaryText(MatchRecordDetailVO.HeaderRecord header) {
        return "A队 " + safeNonNegativeInt(header.getLeftGameWins())
                + ":" + safeNonNegativeInt(header.getRightGameWins())
                + " B队，" + StrUtil.blankToDefault(header.getScoreWinnerText(), "胜方待确认");
    }

    private MatchRecordDetailVO.RosterRenderRecord buildRosterRender(MatchRecordDetailVO source) {
        MatchRecordDetailVO.RosterRenderRecord render = new MatchRecordDetailVO.RosterRenderRecord();
        List<MatchRecordDetailVO.MemberRecord> leftMembers = source.getRosterSnapshot() == null ? List.of() : source.getRosterSnapshot().getLeftMembers();
        List<MatchRecordDetailVO.MemberRecord> rightMembers = source.getRosterSnapshot() == null ? List.of() : source.getRosterSnapshot().getRightMembers();
        render.setLeftRows(chunkMembers(leftMembers, 6));
        render.setRightRows(chunkMembers(rightMembers, 6));
        return render;
    }

    private List<List<MatchRecordDetailVO.MemberRecord>> chunkMembers(List<MatchRecordDetailVO.MemberRecord> members, int size) {
        List<List<MatchRecordDetailVO.MemberRecord>> rows = new ArrayList<>();
        List<MatchRecordDetailVO.MemberRecord> safeMembers = members == null ? List.of() : members;
        for (int i = 0; i < safeMembers.size(); i += size) {
            rows.add(new ArrayList<>(safeMembers.subList(i, Math.min(i + size, safeMembers.size()))));
        }
        if (rows.isEmpty()) {
            rows.add(List.of());
        }
        return rows;
    }

    private List<MatchRecordDetailVO.CoinTossBlockRecord> buildCoinTossBlocks(MatchRecordDetailVO source) {
        List<MatchRecordDetailVO.CoinTossBlockRecord> blocks = new ArrayList<>();

        int bestOf = source.getBestOf() == null ? 3 : source.getBestOf();
        int decidingGameNo = bestOf;
        int completedGames = source.getGameScores() == null ? 0 : source.getGameScores().size();

        // Build serve-side lookup from lineup snapshots: gameNo -> serveSide ("left"/"right")
        Map<Integer, String> serveSideByGame = new LinkedHashMap<>();
        if (source.getLineupSnapshots() != null) {
            for (MatchRecordDetailVO.LineupSnapshotRecord snapshot : source.getLineupSnapshots()) {
                if (snapshot.getGameNo() != null) {
                    serveSideByGame.put(snapshot.getGameNo(), StrUtil.trimToEmpty(snapshot.getServeSide()));
                }
            }
        }

        MatchRecordDetailVO.ReportMetaRecord meta = source.getReportMeta();
        MatchRecordDetailVO.CoinTossRecord initial = meta == null ? null : meta.getInitialCoinToss();
        MatchRecordDetailVO.CoinTossRecord deciding = meta == null ? null : meta.getDecidingSetCoinToss();

        // Game 1 coin toss: always show (match exists implies at least game 1 data)
        String initialServeTeam = resolveCoinTossTeam(initial == null ? null : initial.getServeTeam(),
                serveSideByGame.getOrDefault(1, ""));
        String initialChooseTeam = resolveOpponentTeam(initialServeTeam);
        blocks.add(buildCoinTossBlock(1, initialServeTeam, initialChooseTeam));

        // Deciding game coin toss: only if the deciding game was actually played
        if (decidingGameNo > 1 && completedGames >= decidingGameNo) {
            String decidingServeTeam = resolveCoinTossTeam(
                    deciding == null || !Boolean.TRUE.equals(deciding.getEnabled()) ? null : deciding.getServeTeam(),
                    serveSideByGame.getOrDefault(decidingGameNo, ""));
            String decidingChooseTeam = resolveOpponentTeam(decidingServeTeam);
            blocks.add(buildCoinTossBlock(decidingGameNo, decidingServeTeam, decidingChooseTeam));
        }

        return blocks;
    }

    /**
     * Resolve the serve team label. Prefer explicit value from report-meta,
     * then fall back to lineup serveSide ("left"→"A队", "right"→"B队").
     */
    private String resolveCoinTossTeam(String explicitTeam, String serveSide) {
        if (StrUtil.isNotBlank(explicitTeam)) {
            String team = StrUtil.trim(explicitTeam).toUpperCase();
            if ("A".equals(team)) return "A队";
            if ("B".equals(team)) return "B队";
            return explicitTeam;
        }
        String trimmed = StrUtil.trimToEmpty(serveSide);
        if ("right".equals(trimmed)) {
            return "B队";
        }
        if ("left".equals(trimmed)) {
            return "A队";
        }
        return "";
    }

    private String resolveOpponentTeam(String team) {
        if ("A队".equals(team)) return "B队";
        if ("B队".equals(team)) return "A队";
        return "";
    }

    private MatchRecordDetailVO.CoinTossBlockRecord buildCoinTossBlock(Integer gameNo,
                                                                        String serveTeam,
                                                                        String chooseTeam) {
        MatchRecordDetailVO.CoinTossBlockRecord block = new MatchRecordDetailVO.CoinTossBlockRecord();
        block.setGameNo(gameNo);
        block.setLabel("猜边结果");
        if (StrUtil.isBlank(serveTeam) && StrUtil.isBlank(chooseTeam)) {
            block.setText("猜边结果：待补充");
            return block;
        }
        block.setText("猜边结果：" + serveTeam + "发球，" + chooseTeam + "选边");
        return block;
    }

    private List<MatchRecordDetailVO.GameRenderRecord> buildGameRenderRecords(MatchRecordDetailVO source,
                                                                              MatchRecord match,
                                                                              List<MatchEvent> events,
                                                                              Map<String, TournamentTeamMember> memberMap) {
        Map<Integer, MatchRecordDetailVO.LineupSnapshotRecord> lineupByGame = (source.getLineupSnapshots() == null ? List.<MatchRecordDetailVO.LineupSnapshotRecord>of() : source.getLineupSnapshots())
                .stream()
                .filter(item -> item.getGameNo() != null)
                .collect(Collectors.toMap(MatchRecordDetailVO.LineupSnapshotRecord::getGameNo, item -> item, (left, right) -> left));
        Map<Integer, List<MatchEvent>> substitutionsByGame = events.stream()
                .filter(item -> StrUtil.equals(item.getEventType(), "substitution") && item.getGameNo() != null)
                .collect(Collectors.groupingBy(MatchEvent::getGameNo, LinkedHashMap::new, Collectors.toList()));
        Map<Integer, List<MatchEvent>> timeoutsByGame = events.stream()
                .filter(item -> StrUtil.equals(item.getEventType(), "timeout") && item.getGameNo() != null)
                .collect(Collectors.groupingBy(MatchEvent::getGameNo, LinkedHashMap::new, Collectors.toList()));

        int completedGames = source.getGameScores() == null ? 0 : source.getGameScores().size();
        int maxGameNo = completedGames;
        // Include at most one in-progress game beyond completed ones (has lineup or events)
        int nextGameNo = completedGames + 1;
        if (nextGameNo <= 5 && (lineupByGame.containsKey(nextGameNo)
                || substitutionsByGame.containsKey(nextGameNo)
                || timeoutsByGame.containsKey(nextGameNo))) {
            maxGameNo = nextGameNo;
        }
        if (maxGameNo == 0) {
            return List.of();
        }

        List<MatchRecordDetailVO.GameRenderRecord> records = new ArrayList<>();
        String leftLabel = StrUtil.blankToDefault(StrUtil.trim(source.getLeft() == null ? null : source.getLeft().getName()), "A队");
        String rightLabel = StrUtil.blankToDefault(StrUtil.trim(source.getRight() == null ? null : source.getRight().getName()), "B队");
        for (int gameNo = 1; gameNo <= maxGameNo; gameNo++) {
            MatchRecordDetailVO.GameRenderRecord record = new MatchRecordDetailVO.GameRenderRecord();
            record.setGameNo(gameNo);
            record.setPlayed(true);
            record.setTitle("第" + gameNo + "局");
            record.setLeftTeamLabel("A队");
            record.setRightTeamLabel("B队");

            MatchRecordDetailVO.LineupSnapshotRecord lineup = lineupByGame.get(gameNo);
            List<MatchEvent> substitutionEvents = substitutionsByGame.getOrDefault(gameNo, List.of());
            record.setLeftRotationGrid(buildRotationGrid(lineup == null ? null : lineup.getLeft(), substitutionEvents, "left", memberMap));
            record.setRightRotationGrid(buildRotationGrid(lineup == null ? null : lineup.getRight(), substitutionEvents, "right", memberMap));
            record.setTimeoutLines(buildTimeoutLines(timeoutsByGame.getOrDefault(gameNo, List.of()), leftLabel, rightLabel));
            records.add(record);
        }
        return records;
    }

    private List<MatchRecordDetailVO.RotationCellRecord> buildRotationGrid(MatchRecordDetailVO.TeamLineupRecord lineup,
                                                                           List<MatchEvent> substitutionEvents,
                                                                           String side,
                                                                           Map<String, TournamentTeamMember> memberMap) {
        List<String> slotMemberIds = new ArrayList<>();
        if (lineup != null && lineup.getCourt() != null) {
            for (MatchRecordDetailVO.CourtSlotRecord slot : lineup.getCourt()) {
                slotMemberIds.add(slot == null ? "" : StrUtil.trimToEmpty(slot.getMemberId()));
            }
        }
        slotMemberIds = normalizeCourtForResponse(slotMemberIds);

        List<MatchRecordDetailVO.RotationCellRecord> cells = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            MatchRecordDetailVO.RotationCellRecord cell = new MatchRecordDetailVO.RotationCellRecord();
            cell.setSlotIndex(i);
            TournamentTeamMember member = memberMap.get(StrUtil.trimToEmpty(slotMemberIds.get(i)));
            cell.setPrimaryJerseyNumber(member == null ? null : member.getJerseyNumber());
            cell.setSecondaryJerseyNumber(null);
            cell.setSlashed(false);
            cells.add(cell);
        }

        // Defensive: if lineup snapshot still has a libero in a MB-libero slot
        // (e.g. old snapshots before the frontend filter was added), clear primary.
        if (lineup != null) {
            for (int i = 0; i < 6; i++) {
                if (isLiberoSlot(lineup, i)) {
                    String memberId = StrUtil.trimToEmpty(slotMemberIds.get(i));
                    if (isLiberoMember(lineup, memberId)) {
                        cells.get(i).setPrimaryJerseyNumber(null);
                    }
                }
            }
        }

        // Apply libero to secondary (MB always on top, libero on bottom)
        if (lineup != null && CollUtil.isNotEmpty(lineup.getMiddlePairIndexes())) {
            applyLiberoPriority(cells, lineup.getMiddlePairIndexes(), lineup.getLibero1Id(), lineup.getLibero2Id(), memberMap);
        }

        List<String> runtimeSlots = new ArrayList<>(slotMemberIds);
        for (MatchEvent event : substitutionEvents) {
            JSONObject payload = parseObject(event.getPayloadJson());
            if (!StrUtil.equals(side, StrUtil.trimToEmpty(payload.getStr("side")))) {
                continue;
            }
            String outMemberId = StrUtil.trimToEmpty(payload.getStr("outMemberId"));
            String inMemberId = StrUtil.trimToEmpty(payload.getStr("inMemberId"));
            int slotIndex = runtimeSlots.indexOf(outMemberId);
            // Fallback: old snapshots may have a libero in a MB-libero slot
            // instead of the actual non-libero player. If the out-member isn't
            // found, check MB-libero slots that currently hold a libero.
            if (slotIndex < 0 && lineup != null) {
                for (int i = 0; i < cells.size(); i++) {
                    String currentId = runtimeSlots.get(i);
                    if (isLiberoSlot(lineup, i) && isLiberoMember(lineup, currentId)) {
                        runtimeSlots.set(i, outMemberId);
                        slotIndex = i;
                        break;
                    }
                }
            }
            if (slotIndex < 0 || slotIndex >= cells.size()) {
                continue;
            }

            MatchRecordDetailVO.RotationCellRecord cell = cells.get(slotIndex);
            TournamentTeamMember inMember = memberMap.get(inMemberId);
            boolean liberoSlot = lineup != null && isLiberoSlot(lineup, slotIndex);

            if (liberoSlot) {
                // Locked: MB-libero pairs are fixed — no substitution touches this cell.
                // runtimeSlots is still updated below so subsequent slot matching works.
            } else {
                // Non-libero slot: original behavior — first substitute goes to secondary.
                if (cell.getSecondaryJerseyNumber() == null) {
                    cell.setSecondaryJerseyNumber(inMember == null ? null : inMember.getJerseyNumber());
                    cell.setSlashed(cell.getPrimaryJerseyNumber() != null && cell.getSecondaryJerseyNumber() != null);
                }
            }
            runtimeSlots.set(slotIndex, inMemberId);
        }

        for (MatchRecordDetailVO.RotationCellRecord cell : cells) {
            boolean hasSecondary = cell.getSecondaryJerseyNumber() != null;
            boolean hasPrimary = cell.getPrimaryJerseyNumber() != null;
            // For MB-libero slots where primary was cleared but no substitution
            // recovered it (old data), at least show the libero in secondary.
            if (!hasPrimary && hasSecondary) {
                cell.setSlashed(true);
            } else {
                cell.setSlashed(hasPrimary && hasSecondary);
            }
        }
        return cells;
    }

    private void applyLiberoPriority(List<MatchRecordDetailVO.RotationCellRecord> cells,
                                     List<Integer> pairIndexes,
                                     String libero1Id,
                                     String libero2Id,
                                     Map<String, TournamentTeamMember> memberMap) {
        List<Integer> normalizedPairs = normalizeMiddlePairIndexesForResponse(pairIndexes);
        if (normalizedPairs.size() > 0) {
            applyLiberoToCell(cells, normalizedPairs.get(0), libero1Id, memberMap);
        }
        if (normalizedPairs.size() > 1) {
            applyLiberoToCell(cells, normalizedPairs.get(1), libero2Id, memberMap);
        }
    }

    private void applyLiberoToCell(List<MatchRecordDetailVO.RotationCellRecord> cells,
                                   Integer slotIndex,
                                   String liberoId,
                                   Map<String, TournamentTeamMember> memberMap) {
        if (slotIndex == null || slotIndex < 0 || slotIndex >= cells.size() || StrUtil.isBlank(liberoId)) {
            return;
        }
        TournamentTeamMember libero = memberMap.get(StrUtil.trimToEmpty(liberoId));
        if (libero == null) {
            return;
        }
        MatchRecordDetailVO.RotationCellRecord cell = cells.get(slotIndex);
        cell.setSecondaryJerseyNumber(libero.getJerseyNumber());
        cell.setSlashed(cell.getPrimaryJerseyNumber() != null && cell.getSecondaryJerseyNumber() != null);
    }

    private boolean isLiberoSlot(MatchRecordDetailVO.TeamLineupRecord lineup, int slotIndex) {
        if (lineup == null || CollUtil.isEmpty(lineup.getMiddlePairIndexes())) {
            return false;
        }
        return normalizeMiddlePairIndexesForResponse(lineup.getMiddlePairIndexes()).contains(slotIndex);
    }

    private boolean isLiberoMember(MatchRecordDetailVO.TeamLineupRecord lineup, String memberId) {
        if (lineup == null || StrUtil.isBlank(memberId)) {
            return false;
        }
        String trimmed = StrUtil.trimToEmpty(memberId);
        return trimmed.equals(StrUtil.trimToEmpty(lineup.getLibero1Id()))
                || trimmed.equals(StrUtil.trimToEmpty(lineup.getLibero2Id()));
    }

    private List<String> buildTimeoutLines(List<MatchEvent> timeoutEvents, String leftLabel, String rightLabel) {
        return timeoutEvents.stream()
                .sorted((left, right) -> Integer.compare(left.getEventSeq(), right.getEventSeq()))
                .limit(4)
                .map(event -> {
                    JSONObject payload = parseObject(event.getPayloadJson());
                    String requestSide = StrUtil.trimToEmpty(payload.getStr("side"));
                    String requestLabel = "right".equals(requestSide) ? "B队" : "A队";
                    String serveLabel = "right".equals(event.getServeSide()) ? "B队" : "A队";
                    return requestLabel + "暂停 " + event.getLeftScore() + ":" + event.getRightScore() + " " + serveLabel + "发球";
                })
                .toList();
    }

    private List<MatchRecordDetailVO.GameScoreRecord> buildFixedGameScores(List<MatchRecordDetailVO.GameScoreRecord> scores) {
        List<MatchRecordDetailVO.GameScoreRecord> fixed = new ArrayList<>();
        List<MatchRecordDetailVO.GameScoreRecord> safeScores = scores == null ? List.of() : scores;
        for (int gameNo = 1; gameNo <= 5; gameNo++) {
            final int targetGameNo = gameNo;
            MatchRecordDetailVO.GameScoreRecord existing = safeScores.stream()
                    .filter(item -> item != null && safePositiveInt(item.getGameNo(), 0) == targetGameNo)
                    .findFirst()
                    .orElse(null);
            MatchRecordDetailVO.GameScoreRecord record = new MatchRecordDetailVO.GameScoreRecord();
            record.setGameNo(targetGameNo);
            record.setLeftScore(existing == null ? null : existing.getLeftScore());
            record.setRightScore(existing == null ? null : existing.getRightScore());
            record.setWinnerSide(existing == null ? "" : existing.getWinnerSide());
            fixed.add(record);
        }
        return fixed;
    }

    private String normalizeReportTeamLabel(String value) {
        String text = StrUtil.trimToEmpty(value).toUpperCase();
        if ("B".equals(text)) {
            return "B";
        }
        return "A".equals(text) ? "A" : "";
    }

    private String normalizeThemeDevice(String device) {
        return THEME_DEVICE_PAD.equalsIgnoreCase(StrUtil.trimToEmpty(device)) ? THEME_DEVICE_PAD : THEME_DEVICE_PHONE;
    }

    private String normalizeThemeMode(String mode) {
        return THEME_MODE_LIGHT.equalsIgnoreCase(StrUtil.trimToEmpty(mode)) ? THEME_MODE_LIGHT : THEME_MODE_DARK;
    }

    private Map<String, String> normalizeThemeConfig(Map<String, String> theme, boolean rejectEmpty) {
        if (theme == null || theme.isEmpty()) {
            if (rejectEmpty) {
                throw new IllegalArgumentException("theme cannot be empty");
            }
            return Map.of();
        }

        Map<String, String> normalized = new LinkedHashMap<>();
        for (String key : MATCH_THEME_KEYS) {
            if (!theme.containsKey(key)) {
                continue;
            }
            String value = normalizeThemeHexColor(theme.get(key));
            if (StrUtil.isBlank(value)) {
                throw new IllegalArgumentException("theme." + key + " must be valid hex color");
            }
            normalized.put(key, value);
        }

        if (normalized.isEmpty() && rejectEmpty) {
            throw new IllegalArgumentException("theme cannot be empty");
        }
        return normalized;
    }

    private Map<String, String> parseThemeConfigMap(JSONObject object) {
        if (object == null || object.isEmpty()) {
            return Map.of();
        }
        Map<String, String> raw = new LinkedHashMap<>();
        for (String key : MATCH_THEME_KEYS) {
            String value = object.getStr(key);
            if (StrUtil.isNotBlank(value)) {
                raw.put(key, value);
            }
        }
        return normalizeThemeConfig(raw, false);
    }

    private JSONObject normalizeThemeDeviceConfig(Object value) {
        JSONObject object = value instanceof JSONObject
                ? (JSONObject) value
                : parseObject(value == null ? null : JSONUtil.toJsonStr(value));
        if (object.isEmpty()) {
            return object;
        }

        Map<String, String> darkTheme = parseThemeConfigMap(object);
        if (!darkTheme.isEmpty()) {
            JSONObject wrapped = new JSONObject();
            wrapped.set(THEME_MODE_DARK, darkTheme);
            return wrapped;
        }

        JSONObject normalized = new JSONObject();
        Map<String, String> nestedDarkTheme = parseThemeConfigMap(object.getJSONObject(THEME_MODE_DARK));
        Map<String, String> nestedLightTheme = parseThemeConfigMap(object.getJSONObject(THEME_MODE_LIGHT));
        if (!nestedDarkTheme.isEmpty()) {
            normalized.set(THEME_MODE_DARK, nestedDarkTheme);
        }
        if (!nestedLightTheme.isEmpty()) {
            normalized.set(THEME_MODE_LIGHT, nestedLightTheme);
        }
        return normalized;
    }

    private Map<String, String> extractThemeDraft(JSONObject themeConfig, String device, String mode) {
        JSONObject deviceConfig = normalizeThemeDeviceConfig(themeConfig.get(device));
        if (deviceConfig.isEmpty()) {
            return Map.of();
        }
        return parseThemeConfigMap(deviceConfig.getJSONObject(mode));
    }

    private JSONObject parseThemeConfigObject(String themeJson) {
        JSONObject object = parseObject(themeJson);
        if (object.isEmpty()) {
            return object;
        }
        if (object.containsKey(THEME_DEVICE_PHONE) || object.containsKey(THEME_DEVICE_PAD)) {
            return object;
        }

        Map<String, String> legacyTheme = parseThemeConfigMap(object);
        JSONObject wrapped = new JSONObject();
        if (!legacyTheme.isEmpty()) {
            wrapped.set(THEME_LEGACY_KEY, legacyTheme);
        }
        return wrapped;
    }

    private String normalizeThemeHexColor(String value) {
        String text = StrUtil.trimToEmpty(value).replace("#", "");
        if (text.matches("^[0-9a-fA-F]{3}$")) {
            StringBuilder builder = new StringBuilder("#");
            for (char ch : text.toCharArray()) {
                builder.append(Character.toUpperCase(ch)).append(Character.toUpperCase(ch));
            }
            return builder.toString();
        }
        if (text.matches("^[0-9a-fA-F]{6}$")) {
            return "#" + text.toUpperCase();
        }
        return "";
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

        List<Integer> pairIndexes = normalizeMiddlePairIndexes(config.getMiddlePairIndexes());
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

    private List<Integer> normalizeMiddlePairIndexes(List<Integer> rawIndexes) {
        if (rawIndexes == null || rawIndexes.isEmpty()) {
            return List.of();
        }
        if (rawIndexes.size() != 2) {
            throw new IllegalArgumentException("middle pair must contain exactly 2 indexes");
        }

        Set<Integer> unique = new HashSet<>();
        List<Integer> indexes = new ArrayList<>(2);
        for (Integer rawIndex : rawIndexes) {
            int index = rawIndex == null ? -1 : rawIndex;
            if (!OPPOSITE_SLOT_MAP.containsKey(index)) {
                throw new IllegalArgumentException("middle pair index is invalid");
            }
            if (!unique.add(index)) {
                throw new IllegalArgumentException("middle pair indexes cannot repeat");
            }
            indexes.add(index);
        }
        indexes.sort(Integer::compareTo);
        return indexes;
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
        if (!Set.of("roster_snapshot", "lineup_snapshot", "timeout", "substitution", "captain_change", "side_switch").contains(eventType)) {
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

    private Map<String, Player> loadParticipants(List<String> participantIds) {
        if (CollUtil.isEmpty(participantIds)) {
            return Map.of();
        }
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .in("id", participantIds)
        ).stream().collect(Collectors.toMap(Player::getId, item -> item, (left, right) -> left));
    }

    private List<TournamentTeamMember> loadTeamMembers(String tournamentId, List<String> participantIds) {
        if (StrUtil.isBlank(tournamentId) || CollUtil.isEmpty(participantIds)) {
            return List.of();
        }
        return tournamentTeamMemberMapper.selectList(
                new QueryWrapper<TournamentTeamMember>()
                        .eq("tournament_id", tournamentId)
                        .in("participant_id", participantIds)
                        .orderByAsc("participant_id", "display_order", "id")
        );
    }

    private String resolveWinnerSide(MatchRecord match) {
        if (match == null || StrUtil.isBlank(match.getWinnerId())) {
            return "";
        }
        if (StrUtil.equals(match.getWinnerId(), match.getLeftPlayerId())) {
            return "left";
        }
        if (StrUtil.equals(match.getWinnerId(), match.getRightPlayerId())) {
            return "right";
        }
        return "";
    }

    private MatchRecordDetailVO.ParticipantRecord buildParticipantRecord(Player participant,
                                                                         List<TournamentTeamMember> members) {
        MatchRecordDetailVO.ParticipantRecord record = new MatchRecordDetailVO.ParticipantRecord();
        record.setId(participant == null ? "" : participant.getId());
        record.setName(participant == null ? "" : participant.getName());
        record.setMembers(sortTeamMembers(members).stream()
                .map(this::toMemberRecord)
                .toList());
        return record;
    }

    private MatchRecordDetailVO.MemberRecord toMemberRecord(TournamentTeamMember member) {
        MatchRecordDetailVO.MemberRecord record = new MatchRecordDetailVO.MemberRecord();
        record.setId(member.getId());
        record.setName(member.getName());
        record.setJerseyNumber(member.getJerseyNumber());
        record.setCaptain(Boolean.TRUE.equals(member.getCaptain()));
        record.setLibero(Boolean.TRUE.equals(member.getLibero()));
        return record;
    }

    private List<MatchRecordDetailVO.GameScoreRecord> parseGameScores(String gameScoresJson) {
        if (StrUtil.isBlank(gameScoresJson)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(gameScoresJson);
            List<MatchRecordDetailVO.GameScoreRecord> records = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                Object item = array.get(i);
                if (!(item instanceof JSONObject object)) {
                    continue;
                }
                MatchRecordDetailVO.GameScoreRecord record = new MatchRecordDetailVO.GameScoreRecord();
                record.setGameNo(safePositiveInt(object.getInt("gameNo"), i + 1));
                record.setLeftScore(safeNonNegativeInt(object.getInt("leftScore")));
                record.setRightScore(safeNonNegativeInt(object.getInt("rightScore")));
                record.setWinnerSide(StrUtil.trimToEmpty(object.getStr("winnerSide")));
                records.add(record);
            }
            return records;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private MatchRecordDetailVO.RosterSnapshot buildRosterSnapshot(List<MatchEvent> events,
                                                                   MatchRecordDetailVO.ParticipantRecord left,
                                                                   MatchRecordDetailVO.ParticipantRecord right) {
        MatchEvent snapshotEvent = events.stream()
                .filter(item -> StrUtil.equals(item.getEventType(), "roster_snapshot"))
                .findFirst()
                .orElse(null);

        MatchRecordDetailVO.RosterSnapshot snapshot = new MatchRecordDetailVO.RosterSnapshot();
        if (snapshotEvent == null) {
            snapshot.setLeftMembers(left == null ? List.of() : left.getMembers());
            snapshot.setRightMembers(right == null ? List.of() : right.getMembers());
            return snapshot;
        }

        JSONObject payload = parseObject(snapshotEvent.getPayloadJson());
        snapshot.setLeftMembers(parseRosterMembers(payload.getJSONArray("leftMembers")));
        snapshot.setRightMembers(parseRosterMembers(payload.getJSONArray("rightMembers")));
        return snapshot;
    }

    private List<MatchRecordDetailVO.MemberRecord> parseRosterMembers(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<MatchRecordDetailVO.MemberRecord> members = new ArrayList<>();
        for (Object item : array) {
            if (!(item instanceof JSONObject object)) {
                continue;
            }
            MatchRecordDetailVO.MemberRecord record = new MatchRecordDetailVO.MemberRecord();
            record.setId(StrUtil.trimToEmpty(object.getStr("id")));
            record.setName(StrUtil.trimToEmpty(object.getStr("name")));
            record.setJerseyNumber(safeNonNegativeInt(object.getInt("jerseyNumber")));
            record.setCaptain(Boolean.TRUE.equals(object.getBool("captain")));
            record.setLibero(Boolean.TRUE.equals(object.getBool("libero")));
            members.add(record);
        }
        return sortMemberRecords(members);
    }

    private List<TournamentTeamMember> sortTeamMembers(List<TournamentTeamMember> members) {
        return (members == null ? List.<TournamentTeamMember>of() : members).stream()
                .sorted(Comparator
                        .comparing((TournamentTeamMember item) -> !Boolean.TRUE.equals(item.getCaptain()))
                        .thenComparing(item -> item.getJerseyNumber() == null ? Integer.MAX_VALUE : item.getJerseyNumber())
                        .thenComparing(item -> StrUtil.blankToDefault(item.getName(), ""))
                        .thenComparing(item -> item.getDisplayOrder() == null ? Integer.MAX_VALUE : item.getDisplayOrder())
                        .thenComparing(item -> StrUtil.blankToDefault(item.getId(), "")))
                .toList();
    }

    private List<MatchRecordDetailVO.MemberRecord> sortMemberRecords(List<MatchRecordDetailVO.MemberRecord> members) {
        return (members == null ? List.<MatchRecordDetailVO.MemberRecord>of() : members).stream()
                .sorted(Comparator
                        .comparing((MatchRecordDetailVO.MemberRecord item) -> !Boolean.TRUE.equals(item.getCaptain()))
                        .thenComparing(item -> item.getJerseyNumber() == null ? Integer.MAX_VALUE : item.getJerseyNumber())
                        .thenComparing(item -> StrUtil.blankToDefault(item.getName(), ""))
                        .thenComparing(item -> StrUtil.blankToDefault(item.getId(), "")))
                .toList();
    }

    private List<MatchRecordDetailVO.LineupSnapshotRecord> buildLineupSnapshots(List<MatchEvent> events,
                                                                                List<MatchLineupConfig> lineupConfigs,
                                                                                Map<String, TournamentTeamMember> memberMap) {
        Map<Integer, MatchEvent> lineupEventsByGame = events.stream()
                .filter(item -> StrUtil.equals(item.getEventType(), "lineup_snapshot") && item.getGameNo() != null)
                .collect(Collectors.toMap(MatchEvent::getGameNo, item -> item, (left, right) -> left));
        Map<Integer, MatchLineupConfig> configByGame = lineupConfigs.stream()
                .filter(item -> item.getGameNo() != null)
                .collect(Collectors.toMap(MatchLineupConfig::getGameNo, item -> item, (left, right) -> right));
        Set<Integer> gameNos = new HashSet<>();
        gameNos.addAll(lineupEventsByGame.keySet());
        gameNos.addAll(configByGame.keySet());
        return gameNos.stream().sorted().map(gameNo -> {
            MatchEvent event = lineupEventsByGame.get(gameNo);
            MatchLineupConfig config = configByGame.get(gameNo);
            return buildLineupSnapshotRecord(gameNo, event, config, memberMap);
        }).toList();
    }

    private MatchRecordDetailVO.LineupSnapshotRecord buildLineupSnapshotRecord(Integer gameNo,
                                                                               MatchEvent event,
                                                                               MatchLineupConfig config,
                                                                               Map<String, TournamentTeamMember> memberMap) {
        MatchRecordDetailVO.LineupSnapshotRecord record = new MatchRecordDetailVO.LineupSnapshotRecord();
        record.setGameNo(gameNo);
        if (event != null) {
            JSONObject payload = parseObject(event.getPayloadJson());
            record.setServeSide(StrUtil.blankToDefault(StrUtil.trim(payload.getStr("serveSide")), event.getServeSide()));
            record.setLeft(buildTeamLineupRecord(payload.getJSONObject("left"), memberMap,
                    config == null ? null : config.getLeftLibero1Id(),
                    config == null ? null : config.getLeftLibero2Id()));
            record.setRight(buildTeamLineupRecord(payload.getJSONObject("right"), memberMap,
                    config == null ? null : config.getRightLibero1Id(),
                    config == null ? null : config.getRightLibero2Id()));
            // For MB-libero slots: the event's court may contain libero IDs (from settleTeamLibero).
            // Restore the actual non-libero players from the lineup config so every slot shows both numbers.
            if (config != null) {
                restoreNonLiberoPlayers(record.getLeft(), config, true, memberMap);
                restoreNonLiberoPlayers(record.getRight(), config, false, memberMap);
            }
            return record;
        }
        record.setServeSide(config == null ? "" : StrUtil.trimToEmpty(config.getServeSide()));
        record.setLeft(buildTeamLineupRecordFromConfig(config, "left", memberMap));
        record.setRight(buildTeamLineupRecordFromConfig(config, "right", memberMap));
        return record;
    }

    private void restoreNonLiberoPlayers(MatchRecordDetailVO.TeamLineupRecord eventRecord,
                                          MatchLineupConfig config,
                                          boolean isLeft,
                                          Map<String, TournamentTeamMember> memberMap) {
        if (eventRecord == null || CollUtil.isEmpty(eventRecord.getMiddlePairIndexes())) {
            return;
        }
        List<Integer> normalizedIndexes = normalizeMiddlePairIndexesForResponse(eventRecord.getMiddlePairIndexes());
        if (normalizedIndexes.isEmpty()) {
            return;
        }
        String configJson = isLeft ? config.getLeftCourtJson() : config.getRightCourtJson();
        List<String> configIds = normalizeCourtForResponse(parseStringList(configJson));
        List<MatchRecordDetailVO.CourtSlotRecord> eventCourt = eventRecord.getCourt();
        Set<String> liberoIds = new HashSet<>();
        if (StrUtil.isNotBlank(eventRecord.getLibero1Id())) {
            liberoIds.add(StrUtil.trimToEmpty(eventRecord.getLibero1Id()));
        }
        if (StrUtil.isNotBlank(eventRecord.getLibero2Id())) {
            liberoIds.add(StrUtil.trimToEmpty(eventRecord.getLibero2Id()));
        }
        for (int i = 0; i < 6 && i < eventCourt.size() && i < configIds.size(); i++) {
            if (!normalizedIndexes.contains(i)) {
                continue;
            }
            String eventMemberId = StrUtil.trimToEmpty(eventCourt.get(i).getMemberId());
            if (!liberoIds.contains(eventMemberId) || StrUtil.isBlank(eventMemberId)) {
                continue;
            }
            String configMemberId = StrUtil.trimToEmpty(configIds.get(i));
            if (StrUtil.isBlank(configMemberId) || liberoIds.contains(configMemberId)) {
                continue;
            }
            TournamentTeamMember member = memberMap.get(configMemberId);
            if (member != null) {
                MatchRecordDetailVO.CourtSlotRecord replacement = new MatchRecordDetailVO.CourtSlotRecord();
                replacement.setSlotIndex(i);
                replacement.setMemberId(configMemberId);
                replacement.setMemberName(member.getName());
                replacement.setJerseyNumber(member.getJerseyNumber());
                eventCourt.set(i, replacement);
            }
        }
    }

    private MatchRecordDetailVO.TeamLineupRecord buildTeamLineupRecord(JSONObject object,
                                                                       Map<String, TournamentTeamMember> memberMap,
                                                                       String fallbackLibero1Id,
                                                                       String fallbackLibero2Id) {
        MatchRecordDetailVO.TeamLineupRecord record = new MatchRecordDetailVO.TeamLineupRecord();
        if (object == null) {
            record.setCourt(List.of());
            record.setMiddlePairIndexes(List.of());
            record.setLibero1Id(StrUtil.blankToDefault(StrUtil.trim(fallbackLibero1Id), ""));
            record.setLibero2Id(StrUtil.blankToDefault(StrUtil.trim(fallbackLibero2Id), ""));
            fillLiberoNames(record, memberMap);
            return record;
        }
        record.setCourt(buildCourtSlotRecords(object.getJSONArray("court"), memberMap));
        record.setMiddlePairIndexes(parseIntegerArray(object.getJSONArray("middlePairIndexes")));
        record.setLibero1Id(StrUtil.blankToDefault(StrUtil.trim(object.getStr("libero1Id")), StrUtil.blankToDefault(StrUtil.trim(fallbackLibero1Id), "")));
        record.setLibero2Id(StrUtil.blankToDefault(StrUtil.trim(object.getStr("libero2Id")), StrUtil.blankToDefault(StrUtil.trim(fallbackLibero2Id), "")));
        fillLiberoNames(record, memberMap);
        return record;
    }

    private MatchRecordDetailVO.TeamLineupRecord buildTeamLineupRecordFromConfig(MatchLineupConfig config,
                                                                                 String side,
                                                                                 Map<String, TournamentTeamMember> memberMap) {
        MatchRecordDetailVO.TeamLineupRecord record = new MatchRecordDetailVO.TeamLineupRecord();
        if (config == null) {
            record.setCourt(List.of());
            record.setMiddlePairIndexes(List.of());
            record.setLibero1Id("");
            record.setLibero2Id("");
            return record;
        }
        boolean isLeft = "left".equals(side);
        record.setCourt(buildCourtSlotRecordsFromIds(
                parseStringList(isLeft ? config.getLeftCourtJson() : config.getRightCourtJson()),
                memberMap
        ));
        record.setMiddlePairIndexes(normalizeMiddlePairIndexesForResponse(
                parseIntegerList(isLeft ? config.getLeftMiddlePairIndexesJson() : config.getRightMiddlePairIndexesJson())
        ));
        record.setLibero1Id(StrUtil.blankToDefault(StrUtil.trim(isLeft ? config.getLeftLibero1Id() : config.getRightLibero1Id()), ""));
        record.setLibero2Id(StrUtil.blankToDefault(StrUtil.trim(isLeft ? config.getLeftLibero2Id() : config.getRightLibero2Id()), ""));
        fillLiberoNames(record, memberMap);
        return record;
    }

    private void fillLiberoNames(MatchRecordDetailVO.TeamLineupRecord record,
                                 Map<String, TournamentTeamMember> memberMap) {
        TournamentTeamMember libero1 = memberMap.get(record.getLibero1Id());
        TournamentTeamMember libero2 = memberMap.get(record.getLibero2Id());
        record.setLibero1Name(libero1 == null ? "" : libero1.getName());
        record.setLibero2Name(libero2 == null ? "" : libero2.getName());
    }

    private List<MatchRecordDetailVO.CourtSlotRecord> buildCourtSlotRecords(JSONArray array,
                                                                            Map<String, TournamentTeamMember> memberMap) {
        List<String> ids = new ArrayList<>();
        if (array != null) {
            for (Object item : array) {
                ids.add(item == null ? "" : String.valueOf(item));
            }
        }
        return buildCourtSlotRecordsFromIds(ids, memberMap);
    }

    private List<MatchRecordDetailVO.CourtSlotRecord> buildCourtSlotRecordsFromIds(List<String> ids,
                                                                                   Map<String, TournamentTeamMember> memberMap) {
        List<String> normalized = normalizeCourtForResponse(ids);
        List<MatchRecordDetailVO.CourtSlotRecord> records = new ArrayList<>();
        for (int i = 0; i < normalized.size(); i++) {
            String memberId = StrUtil.trimToEmpty(normalized.get(i));
            TournamentTeamMember member = memberMap.get(memberId);
            MatchRecordDetailVO.CourtSlotRecord slot = new MatchRecordDetailVO.CourtSlotRecord();
            slot.setSlotIndex(i);
            slot.setPositionLabel(COURT_POSITION_LABELS.get(i));
            slot.setMemberId(memberId);
            slot.setMemberName(member == null ? "" : member.getName());
            slot.setJerseyNumber(member == null ? null : member.getJerseyNumber());
            records.add(slot);
        }
        return records;
    }

    private List<MatchRecordDetailVO.EventRecord> buildEventRecords(List<MatchEvent> events,
                                                                    MatchRecord match,
                                                                    Map<String, Player> participantMap,
                                                                    Map<String, TournamentTeamMember> memberMap) {
        return events.stream().map(event -> {
            MatchRecordDetailVO.EventRecord record = new MatchRecordDetailVO.EventRecord();
            record.setEventSeq(event.getEventSeq());
            record.setEventType(event.getEventType());
            record.setEventTypeLabel(resolveEventTypeLabel(event.getEventType()));
            record.setGameNo(event.getGameNo());
            record.setLeftScore(event.getLeftScore());
            record.setRightScore(event.getRightScore());
            record.setServeSide(event.getServeSide());
            record.setCreateTime(event.getCreateTime() == null ? "" : event.getCreateTime().format(DATETIME_FORMATTER));
            fillEventText(record, event, match, participantMap, memberMap);
            return record;
        }).toList();
    }

    private void fillEventText(MatchRecordDetailVO.EventRecord record,
                               MatchEvent event,
                               MatchRecord match,
                               Map<String, Player> participantMap,
                               Map<String, TournamentTeamMember> memberMap) {
        JSONObject payload = parseObject(event.getPayloadJson());
        String leftName = resolveParticipantName(participantMap, match.getLeftPlayerId(), "左队");
        String rightName = resolveParticipantName(participantMap, match.getRightPlayerId(), "右队");
        switch (StrUtil.trimToEmpty(event.getEventType())) {
            case "roster_snapshot" -> {
                record.setSummary("记录双方队员名单快照");
                record.setDetailLines(List.of(
                        leftName + "名单 " + safeArraySize(payload.getJSONArray("leftMembers")) + " 人",
                        rightName + "名单 " + safeArraySize(payload.getJSONArray("rightMembers")) + " 人"
                ));
            }
            case "lineup_snapshot" -> {
                record.setSummary("第 " + event.getGameNo() + " 局开局轮次已确认");
                record.setDetailLines(buildLineupEventDetails(payload, memberMap));
            }
            case "timeout" -> {
                String side = StrUtil.trimToEmpty(payload.getStr("side"));
                record.setSummary(("left".equals(side) ? leftName : rightName) + " 叫暂停");
                record.setDetailLines(List.of("比分 " + event.getLeftScore() + ":" + event.getRightScore()));
            }
            case "substitution" -> {
                String side = StrUtil.trimToEmpty(payload.getStr("side"));
                TournamentTeamMember outMember = memberMap.get(StrUtil.trimToEmpty(payload.getStr("outMemberId")));
                TournamentTeamMember inMember = memberMap.get(StrUtil.trimToEmpty(payload.getStr("inMemberId")));
                record.setSummary(("left".equals(side) ? leftName : rightName) + " 手动换人");
                record.setDetailLines(List.of(
                        "下场 " + memberDisplay(outMember),
                        "上场 " + memberDisplay(inMember)
                ));
            }
            case "captain_change" -> {
                String side = StrUtil.trimToEmpty(payload.getStr("side"));
                TournamentTeamMember captain = memberMap.get(StrUtil.trimToEmpty(payload.getStr("captainMemberId")));
                String source = StrUtil.trimToEmpty(payload.getStr("source"));
                record.setSummary(("left".equals(side) ? leftName : rightName) + " 场上队长变更");
                record.setDetailLines(List.of(
                        "新队长 " + memberDisplay(captain),
                        "来源 " + ("auto".equals(source) ? "自动恢复/判定" : "手动确认")
                ));
            }
            case "side_switch" -> {
                String reason = StrUtil.trimToEmpty(payload.getStr("reason"));
                String summary = switch (reason) {
                    case "between_games" -> "局间整体换边";
                    case "deciding_game_mid_switch" -> "决胜局8分整体换边";
                    default -> "整体换边";
                };
                record.setSummary(summary);
                record.setDetailLines(List.of(
                        "比分 " + event.getLeftScore() + ":" + event.getRightScore(),
                        "发球方 " + ("left".equals(event.getServeSide()) ? leftName : rightName)
                ));
            }
            default -> {
                record.setSummary("记录比赛事件");
                record.setDetailLines(List.of());
            }
        }
    }

    private List<String> buildLineupEventDetails(JSONObject payload,
                                                 Map<String, TournamentTeamMember> memberMap) {
        List<String> lines = new ArrayList<>();
        String serveSide = StrUtil.trimToEmpty(payload.getStr("serveSide"));
        if (StrUtil.isNotBlank(serveSide)) {
            lines.add("发球方 " + ("left".equals(serveSide) ? "左队" : "右队"));
        }
        JSONObject left = payload.getJSONObject("left");
        JSONObject right = payload.getJSONObject("right");
        lines.add("左队场上 " + buildCourtSummary(left == null ? null : left.getJSONArray("court"), memberMap));
        lines.add("右队场上 " + buildCourtSummary(right == null ? null : right.getJSONArray("court"), memberMap));
        String leftLibero = buildLiberoSummary(left, memberMap);
        String rightLibero = buildLiberoSummary(right, memberMap);
        if (StrUtil.isNotBlank(leftLibero)) {
            lines.add("左队自由人 " + leftLibero);
        }
        if (StrUtil.isNotBlank(rightLibero)) {
            lines.add("右队自由人 " + rightLibero);
        }
        return lines;
    }

    private String buildCourtSummary(JSONArray array, Map<String, TournamentTeamMember> memberMap) {
        if (array == null || array.isEmpty()) {
            return "未记录";
        }
        List<String> parts = new ArrayList<>();
        for (Object item : array) {
            TournamentTeamMember member = memberMap.get(item == null ? "" : String.valueOf(item));
            parts.add(memberDisplay(member));
        }
        return String.join(" / ", parts);
    }

    private String buildLiberoSummary(JSONObject object, Map<String, TournamentTeamMember> memberMap) {
        if (object == null) {
            return "";
        }
        List<String> names = new ArrayList<>();
        TournamentTeamMember libero1 = memberMap.get(StrUtil.trimToEmpty(object.getStr("libero1Id")));
        TournamentTeamMember libero2 = memberMap.get(StrUtil.trimToEmpty(object.getStr("libero2Id")));
        if (libero1 != null) {
            names.add(memberDisplay(libero1));
        }
        if (libero2 != null) {
            names.add(memberDisplay(libero2));
        }
        return String.join(" / ", names);
    }

    private String resolveEventTypeLabel(String eventType) {
        return switch (StrUtil.trimToEmpty(eventType)) {
            case "roster_snapshot" -> "名单快照";
            case "lineup_snapshot" -> "开局轮次";
            case "timeout" -> "暂停";
            case "substitution" -> "换人";
            case "captain_change" -> "场上队长";
            case "side_switch" -> "换边";
            default -> "事件";
        };
    }

    private String resolveParticipantName(Map<String, Player> participantMap, String participantId, String fallback) {
        Player player = participantMap.get(participantId);
        return player == null || StrUtil.isBlank(player.getName()) ? fallback : player.getName();
    }

    private JSONObject parseObject(String json) {
        if (StrUtil.isBlank(json)) {
            return new JSONObject();
        }
        try {
            return JSONUtil.parseObj(json);
        } catch (Exception ex) {
            return new JSONObject();
        }
    }

    private List<Integer> parseIntegerArray(JSONArray array) {
        if (array == null) {
            return List.of();
        }
        List<Integer> values = new ArrayList<>();
        for (Object item : array) {
            if (item == null) {
                continue;
            }
            try {
                values.add(Integer.parseInt(String.valueOf(item)));
            } catch (Exception ignored) {
                // ignore invalid item
            }
        }
        return normalizeMiddlePairIndexesForResponse(values);
    }

    private int safeArraySize(JSONArray array) {
        return array == null ? 0 : array.size();
    }

    private int safePositiveInt(Integer value, int fallback) {
        return value == null || value <= 0 ? fallback : value;
    }

    private Integer safeNonNegativeInt(Integer value) {
        return value == null || value < 0 ? 0 : value;
    }

    private String memberDisplay(TournamentTeamMember member) {
        if (member == null) {
            return "-";
        }
        String jersey = member.getJerseyNumber() == null ? "?" : String.valueOf(member.getJerseyNumber());
        return jersey + "号 " + StrUtil.blankToDefault(member.getName(), "");
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
                normalizeCourtForResponse(parseStringList(entity.getLeftCourtJson())),
                normalizeMiddlePairIndexesForResponse(parseIntegerList(entity.getLeftMiddlePairIndexesJson())),
                entity.getLeftLibero1Id(),
                entity.getLeftLibero2Id()
        ));
        config.setRight(buildTeamLineupConfig(
                normalizeCourtForResponse(parseStringList(entity.getRightCourtJson())),
                normalizeMiddlePairIndexesForResponse(parseIntegerList(entity.getRightMiddlePairIndexesJson())),
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

    private List<String> parseStringList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            List<String> values = new ArrayList<>(array.size());
            for (Object item : array) {
                values.add(StrUtil.trimToEmpty(item == null ? "" : String.valueOf(item)));
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<Integer> parseIntegerList(String json) {
        if (StrUtil.isBlank(json)) {
            return List.of();
        }
        try {
            JSONArray array = JSONUtil.parseArray(json);
            List<Integer> values = new ArrayList<>(array.size());
            for (Object item : array) {
                if (item == null) {
                    continue;
                }
                values.add(Integer.parseInt(String.valueOf(item)));
            }
            return values;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> normalizeCourtForResponse(List<String> court) {
        List<String> normalized = new ArrayList<>(court == null ? List.of() : court);
        if (normalized.size() > 6) {
            normalized = normalized.subList(0, 6);
        }
        while (normalized.size() < 6) {
            normalized.add("");
        }
        return normalized;
    }

    private List<Integer> normalizeMiddlePairIndexesForResponse(List<Integer> pairIndexes) {
        try {
            return normalizeMiddlePairIndexes(pairIndexes);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
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

    private void validateFinishReq(FinishMatchReq req, Tournament tournament) {
        int leftWins = req.getLeftGameWins() == null ? 0 : req.getLeftGameWins();
        int rightWins = req.getRightGameWins() == null ? 0 : req.getRightGameWins();
        if (leftWins < 0 || rightWins < 0) {
            throw new IllegalArgumentException("game wins cannot be negative");
        }

        int gamesToWin = tournament == null || tournament.getGamesToWin() == null
                ? Math.max(leftWins, rightWins)
                : tournament.getGamesToWin();
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

    private static class TeamMemberScope {

        private final Set<String> memberIds = new HashSet<>();
    }
}
