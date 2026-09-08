package com.scoring.backend.service.match;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.scoring.backend.common.JsonUtils;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentTeamMember;
import com.scoring.backend.domain.vo.MatchRecordDetailVO;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TournamentTeamMemberMapper;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 比赛详情读模型装配器：把比赛实体、事件流、阵容配置、战报元数据
 * 组装成前端渲染用的 MatchRecordDetailVO（旋转轮次表/自由人/掷硬币块/阵容快照等）。
 * 纯读装配，不写库、不承担事务边界；实体查询只保留 participant/member 两个只读加载。
 */
@Service
public class MatchDetailAssembler {

    private static final DateTimeFormatter DATETIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final List<String> COURT_POSITION_LABELS = List.of("4号位", "3号位", "2号位", "5号位", "6号位", "1号位");

    private static final Map<Integer, Integer> OPPOSITE_SLOT_MAP = Map.of(
            0, 5,
            1, 4,
            2, 3,
            3, 2,
            4, 1,
            5, 0
    );

    private final PlayerMapper playerMapper;
    private final TournamentTeamMemberMapper tournamentTeamMemberMapper;
    private final MatchReportAssembler reportAssembler;

    public MatchDetailAssembler(PlayerMapper playerMapper,
                                TournamentTeamMemberMapper tournamentTeamMemberMapper,
                                MatchReportAssembler reportAssembler) {
        this.playerMapper = playerMapper;
        this.tournamentTeamMemberMapper = tournamentTeamMemberMapper;
        this.reportAssembler = reportAssembler;
    }

    public MatchRecordDetailVO.ReportRenderRecord buildReportRender(MatchRecordDetailVO source,
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
        MatchRecordDetailVO.SignatureRecord record = meta == null ? reportAssembler.buildSignatureRecord(null) : reportAssembler.buildSignatureRecord(meta.getSignatures() == null ? null : toSignatureJson(meta.getSignatures()));
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
            JSONObject payload = JsonUtils.parseObject(event.getPayloadJson());
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
                    JSONObject payload = JsonUtils.parseObject(event.getPayloadJson());
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

    public Map<String, Player> loadParticipants(List<String> participantIds) {
        if (CollUtil.isEmpty(participantIds)) {
            return Map.of();
        }
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .in("id", participantIds)
        ).stream().collect(Collectors.toMap(Player::getId, item -> item, (left, right) -> left));
    }

    public List<TournamentTeamMember> loadTeamMembers(String tournamentId, List<String> participantIds) {
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

    public String resolveWinnerSide(MatchRecord match) {
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

    public MatchRecordDetailVO.ParticipantRecord buildParticipantRecord(Player participant,
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

    public List<MatchRecordDetailVO.GameScoreRecord> parseGameScores(String gameScoresJson) {
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

    public MatchRecordDetailVO.RosterSnapshot buildRosterSnapshot(List<MatchEvent> events,
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

        JSONObject payload = JsonUtils.parseObject(snapshotEvent.getPayloadJson());
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

    public List<MatchRecordDetailVO.LineupSnapshotRecord> buildLineupSnapshots(List<MatchEvent> events,
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
            JSONObject payload = JsonUtils.parseObject(event.getPayloadJson());
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

    public List<MatchRecordDetailVO.EventRecord> buildEventRecords(List<MatchEvent> events,
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
            record.setPayloadJson(event.getPayloadJson());
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
        JSONObject payload = JsonUtils.parseObject(event.getPayloadJson());
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
            case "score_snapshot" -> {
                record.setSummary("第 " + event.getGameNo() + " 局比分更新");
                record.setDetailLines(List.of("比分 " + event.getLeftScore() + ":" + event.getRightScore()));
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
            case "score_snapshot" -> "比分更新";
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

    public List<String> parseStringList(String json) {
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

    public List<Integer> parseIntegerList(String json) {
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

    public List<String> normalizeCourtForResponse(List<String> court) {
        List<String> normalized = new ArrayList<>(court == null ? List.of() : court);
        if (normalized.size() > 6) {
            normalized = normalized.subList(0, 6);
        }
        while (normalized.size() < 6) {
            normalized.add("");
        }
        return normalized;
    }

    public List<Integer> normalizeMiddlePairIndexesForResponse(List<Integer> pairIndexes) {
        try {
            return normalizeMiddlePairIndexes(pairIndexes);
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
    }

    /**
     * 校验并归一化中间搭档对（2 个互为对角的场位下标）；写路径保存前与读路径渲染共用。
     */
    public List<Integer> normalizeMiddlePairIndexes(List<Integer> rawIndexes) {
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
        indexes.sort(Comparator.naturalOrder());
        return indexes;
    }
}
