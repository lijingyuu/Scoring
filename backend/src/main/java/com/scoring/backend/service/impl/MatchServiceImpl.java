package com.scoring.backend.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.domain.dto.FinishMatchReq;
import com.scoring.backend.domain.dto.SaveMatchEventsReq;
import com.scoring.backend.domain.dto.SaveMatchLineupConfigReq;
import com.scoring.backend.domain.dto.UpdateScoreReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchLineupConfigVO;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.TournamentMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import com.scoring.backend.service.MatchService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MatchServiceImpl implements MatchService {

    private static final Map<Integer, Integer> OPPOSITE_SLOT_MAP = Map.of(
            0, 5,
            1, 4,
            2, 3,
            3, 2,
            4, 1,
            5, 0
    );

    private final MatchRecordMapper matchRecordMapper;
    private final TournamentMapper tournamentMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final MatchLineupConfigMapper matchLineupConfigMapper;
    private final MatchEventMapper matchEventMapper;

    public MatchServiceImpl(MatchRecordMapper matchRecordMapper,
                            TournamentMapper tournamentMapper,
                            TournamentTeamMemberMapper tournamentTeamMemberMapper,
                            MatchLineupConfigMapper matchLineupConfigMapper,
                            MatchEventMapper matchEventMapper) {
        this.matchRecordMapper = matchRecordMapper;
        this.tournamentMapper = tournamentMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.matchEventMapper = matchEventMapper;
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

        MatchRecord current = matchRecordMapper.selectById(matchId);
        if (current == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }

        Tournament tournament = requireCreatorTournament(userId, current.getTournamentId());

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
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
            return;
        }

        MatchRecord next = matchRecordMapper.selectById(current.getNextMatchId());
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

        MatchRecord current = matchRecordMapper.selectById(matchId);
        if (current == null) {
            throw new IllegalArgumentException("match record not found: " + matchId);
        }

        Tournament tournament = requireCreatorTournament(userId, current.getTournamentId());

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
        if (req.getGameScores() != null) {
            updateCurrent.setGameScores(JSONUtil.toJsonStr(req.getGameScores()));
        }
        updateCurrent.setStatus(2);
        if (StrUtil.isNotBlank(req.getRetiredSide())) {
            updateCurrent.setRetiredSide(req.getRetiredSide());
        }
        matchRecordMapper.updateById(updateCurrent);

        if (StrUtil.isBlank(current.getNextMatchId())) {
            if (Integer.valueOf(0).equals(current.getStageType())
                    && Integer.valueOf(1).equals(tournament.getTournamentType())) {
                return;
            }
            Tournament updateTournament = new Tournament();
            updateTournament.setId(current.getTournamentId());
            updateTournament.setStatus(2);
            tournamentMapper.updateById(updateTournament);
            return;
        }

        MatchRecord next = matchRecordMapper.selectById(current.getNextMatchId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveLineupConfig(String userId, String matchId, SaveMatchLineupConfigReq req) {
        MatchRecord match = requireMatch(matchId);
        requireCreatorTournament(userId, match.getTournamentId());
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveMatchEvents(String userId, String matchId, SaveMatchEventsReq req) {
        MatchRecord match = requireMatch(matchId);
        requireCreatorTournament(userId, match.getTournamentId());
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
    public MatchLineupConfigVO getEffectiveLineupConfig(String matchId, Integer gameNo) {
        requireMatch(matchId);
        int targetGameNo = validateGameNo(gameNo);

        MatchLineupConfig exact = findLineupConfig(matchId, targetGameNo);
        if (exact != null) {
            return buildLineupConfigResponse(
                    targetGameNo,
                    true,
                    exact.getGameNo(),
                    toLineupConfigVO(exact, 0)
            );
        }

        MatchLineupConfig previous = findLatestLineupConfigBefore(matchId, targetGameNo);
        if (previous == null) {
            return buildLineupConfigResponse(targetGameNo, false, null, emptyLineupConfig());
        }

        int shiftCount = Math.max(0, targetGameNo - previous.getGameNo());
        return buildLineupConfigResponse(
                targetGameNo,
                false,
                previous.getGameNo(),
                toLineupConfigVO(previous, shiftCount)
        );
    }

    private Tournament requireCreatorTournament(String userId, String tournamentId) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("tournament not found: " + tournamentId);
        }
        if (!StrUtil.equals(userId, tournament.getCreatorUserId())) {
            throw new IllegalArgumentException("only creator can modify this match");
        }
        return tournament;
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

    private int validateGameNo(Integer gameNo) {
        int normalized = gameNo == null ? 0 : gameNo;
        if (normalized <= 0) {
            throw new IllegalArgumentException("gameNo must be greater than 0");
        }
        return normalized;
    }

    private void ensureLineupConfigEditable(MatchRecord match, int gameNo) {
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
        if (!Set.of("roster_snapshot", "lineup_snapshot", "timeout", "substitution", "captain_change").contains(eventType)) {
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

    private MatchLineupConfigVO buildLineupConfigResponse(int gameNo,
                                                          boolean exists,
                                                          Integer effectiveFromGameNo,
                                                          MatchLineupConfigVO.LineupConfig config) {
        MatchLineupConfigVO vo = new MatchLineupConfigVO();
        vo.setGameNo(gameNo);
        vo.setExists(exists);
        vo.setEffectiveFromGameNo(effectiveFromGameNo);
        vo.setConfig(config);
        return vo;
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

    private static class TeamMemberScope {

        private final Set<String> memberIds = new HashSet<>();
    }
}
