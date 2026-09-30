package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.scoring.backend.domain.dto.UpdateDrawSlotsReq;
import com.scoring.backend.domain.dto.UpdateGroupAssignmentsReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
import com.scoring.backend.engine.RoundRobinEngine;
import com.scoring.backend.mapper.MatchEventMapper;
import com.scoring.backend.mapper.MatchLineupConfigMapper;
import com.scoring.backend.mapper.MatchRecordMapper;
import com.scoring.backend.mapper.PlayerMapper;
import com.scoring.backend.mapper.TeamMatchItemMapper;
import com.scoring.backend.mapper.TournamentDivisionMapper;
import com.scoring.backend.mapper.TournamentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 手写签表/手写分组：开赛前的整体重提交（设计见 docs/手写签表改造方案.md）。
 *
 * 一、draw-slots（全量替换首轮签位，原地 UPDATE，match id 与晋级链不动）：
 *   - type0（纯淘汰赛，drawMode=manual）：零开赛（整组别）窗口；
 *   - type1（小组赛+淘汰赛）：淘汰赛已生成、且零场淘汰赛开赛的窗口（小组赛早已完赛，不阻塞）。
 *     type1 淘汰赛无轮空（签位数=出线数），签位集合必须与当前首轮参赛者（=出线者）完全一致。
 *
 * 二、group-assignments（type1 手写分组重提交，drawMode=manual-groups）：
 *   首场小组赛开赛前允许整体重分组——重写 player.group_no/group_position 后全删全建
 *   小组赛赛程（窗口内无事件/阵容/子项行，守卫已保证；match id 会变，与 draw-slots 的
 *   "原地不动"刻意不同，因为组间人数变化必然改变赛程结构）。内容未变化时直接返回（幂等，match id 稳定）。
 *
 * 事务纪律（与 type0 实施一致）：
 *   - 隔离级别显式 READ_COMMITTED：默认 RR 下首条普通 SELECT 建立的快照会让守卫读到旧数据；
 *   - 锁序：先 match_record 行、后 division 行——与记分/结算运行时路径一致，避免交叉死锁；
 *   - 开赛判定只认痕迹（game_scores/score_display/game_wins/retired_side/lock_* +
 *     match_event/match_lineup_config/team_match_item 行），winner_id/status 不算开赛信号。
 */
@Service
public class TournamentDrawService {

    private static final int TYPE_KNOCKOUT = 0;
    private static final int TYPE_GROUP = 1;
    private static final int DRAW_MODE_MANUAL = 1;
    private static final int DRAW_MODE_MANUAL_GROUPS = 2;
    private static final int STAGE_TYPE_GROUP = 0;
    private static final int STAGE_TYPE_KNOCKOUT = 1;
    private static final int MAX_MANUAL_DRAW_SLOTS = 64;
    private static final int MATCH_ROLE_THIRD_PLACE = 1;

    private final TournamentMapper tournamentMapper;
    private final TournamentDivisionMapper tournamentDivisionMapper;
    private final PlayerMapper playerMapper;
    private final MatchRecordMapper matchRecordMapper;
    private final MatchEventMapper matchEventMapper;
    private final MatchLineupConfigMapper matchLineupConfigMapper;
    private final TeamMatchItemMapper teamMatchItemMapper;
    private final TournamentAccessGuard accessGuard;
    private final RoundRobinEngine roundRobinEngine;

    public TournamentDrawService(TournamentMapper tournamentMapper,
                                 TournamentDivisionMapper tournamentDivisionMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 MatchEventMapper matchEventMapper,
                                 MatchLineupConfigMapper matchLineupConfigMapper,
                                 TeamMatchItemMapper teamMatchItemMapper,
                                 TournamentAccessGuard accessGuard,
                                 RoundRobinEngine roundRobinEngine) {
        this.tournamentMapper = tournamentMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.matchEventMapper = matchEventMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.accessGuard = accessGuard;
        this.roundRobinEngine = roundRobinEngine;
    }

    // ==================================================================================
    // draw-slots：签位整体重提交（type0 开赛前 / type1 淘汰赛生成后首场开赛前）
    // ==================================================================================

    /**
     * 隔离级别显式用 READ_COMMITTED：默认 RR 下首条普通 SELECT 建立的快照会让
     * 后续守卫读到"开赛前"的旧数据（FOR UPDATE 只影响被锁行自身），从而漏判已开赛。
     * 锁序：先 match_record 行、后 division 行——与记分/结算运行时路径
     * （先锁比赛行、再锁组别/赛事行）保持一致，避免交叉死锁。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void updateDrawSlots(String userId, String tournamentId, String divisionId, UpdateDrawSlotsReq req) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        accessGuard.requireNotArchived(tournament);
        accessGuard.requireCreator(userId, tournament);

        // 1) 先锁比赛行（最新已提交数据 + 与运行时路径一致的锁序）
        List<MatchRecord> matches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("division_id", divisionId)
                        .orderByAsc("round_num", "match_index")
                        .last("FOR UPDATE"));

        // 2) 再锁组别行，串行化并发改签
        TournamentDivision division = tournamentDivisionMapper.selectOne(
                new QueryWrapper<TournamentDivision>()
                        .eq("id", divisionId)
                        .eq("tournament_id", tournamentId)
                        .last("FOR UPDATE"));
        if (division == null) {
            throw new IllegalArgumentException("组别不存在: " + divisionId);
        }

        if (Integer.valueOf(TYPE_KNOCKOUT).equals(division.getTournamentType())) {
            updateKnockoutDrawSlots(division, matches, req);
            return;
        }
        if (Integer.valueOf(TYPE_GROUP).equals(division.getTournamentType())) {
            updateGroupTournamentDrawSlots(division, matches, req);
            return;
        }
        throw new IllegalArgumentException("仅纯淘汰赛与小组赛+淘汰赛支持调整签位");
    }

    /** type0 路径：仅手写签表（drawMode=manual）+ 整组别零开赛。 */
    private void updateKnockoutDrawSlots(TournamentDivision division, List<MatchRecord> matches,
                                         UpdateDrawSlotsReq req) {
        if (!Integer.valueOf(DRAW_MODE_MANUAL).equals(division.getDrawMode())) {
            throw new IllegalArgumentException("仅手写签表（drawMode=manual）的赛事支持调整签位");
        }
        List<Player> roster = loadRoster(division.getId());
        assertZeroStarted(matches);
        validateBracketStructure(division, matches);
        List<String> slotIds = validateAndMapSlotOrder(req, division, roster);
        applySlotOrder(matches, slotIds);
    }

    /**
     * type1 路径：淘汰赛已生成 + 零场淘汰赛开赛（只查 stage_type=1 的痕迹；小组赛必然已完赛）。
     * 生成方式（auto/manual-groups）不影响——generate-knockout 一次性生成，排错同样需要补救窗口。
     */
    private void updateGroupTournamentDrawSlots(TournamentDivision division, List<MatchRecord> matches,
                                                UpdateDrawSlotsReq req) {
        if (!Boolean.TRUE.equals(division.getKnockoutGenerated())) {
            throw new IllegalArgumentException("淘汰赛尚未生成，暂无可调整的签位");
        }
        List<MatchRecord> knockoutMatches = matches.stream()
                .filter(match -> Integer.valueOf(STAGE_TYPE_KNOCKOUT).equals(match.getStageType()))
                .toList();
        if (CollUtil.isEmpty(knockoutMatches)) {
            throw new IllegalArgumentException("淘汰赛尚未生成，暂无可调整的签位");
        }
        assertZeroStarted(knockoutMatches, "淘汰赛尚未生成，暂无可调整的签位");
        assertNoStartedTraces(knockoutMatches, "已有淘汰赛开始，签表不可再编辑");
        validateBracketStructure(division, knockoutMatches);
        List<String> slotIds = validateGroupTournamentSlotOrder(req, division, knockoutMatches);
        applySlotOrder(knockoutMatches, slotIds);
    }

    /** 结构不变式：首轮槽位场数 = 容量/2 且 matchIndex 连续从 0 开始，防 knockout_rounds 与实际签表漂移。 */
    private void validateBracketStructure(TournamentDivision division, List<MatchRecord> matches) {
        if (division.getKnockoutRounds() == null) {
            throw new IllegalArgumentException("组别缺少 knockoutRounds，签表结构异常");
        }
        int capacity = 1 << division.getKnockoutRounds();
        List<MatchRecord> firstRound = matches.stream().filter(this::isFirstRoundSlotMatch).toList();
        if (firstRound.size() != capacity / 2) {
            throw new IllegalArgumentException("签表结构与 knockoutRounds 不一致：需要 " + (capacity / 2)
                    + " 场首轮，实际 " + firstRound.size() + " 场");
        }
        List<Integer> indexes = firstRound.stream().map(MatchRecord::getMatchIndex).sorted().toList();
        for (int i = 0; i < indexes.size(); i++) {
            if (indexes.get(i) == null || indexes.get(i) != i) {
                throw new IllegalArgumentException("首轮 matchIndex 不连续: 期望 " + i + "，实际 " + indexes.get(i));
            }
        }
    }

    /**
     * 零开赛：无任何记分/事件/阵容/子项/执裁痕迹。
     * 注意 winner_id/status 不作为开赛信号——手写签表创建时的轮空坍缩就会写这两个字段；
     * 真实完赛（finishMatch）必然伴随 game_scores/score_display，退赛伴随 retired_side。
     */
    private void assertZeroStarted(List<MatchRecord> matches) {
        assertZeroStarted(matches, "签表尚未生成");
    }

    private void assertZeroStarted(List<MatchRecord> matches, String emptyMessage) {
        if (CollUtil.isEmpty(matches)) {
            throw new IllegalArgumentException(emptyMessage);
        }
        assertNoStartedTraces(matches, "已有比赛开始，签表不可再编辑");
    }

    /** 痕迹检查核心：空列表直接放行（无痕迹可查），供 group-assignments 复用。 */
    private void assertNoStartedTraces(List<MatchRecord> matches, String violationMessage) {
        if (CollUtil.isEmpty(matches)) {
            return;
        }
        List<String> matchIds = matches.stream().map(MatchRecord::getId).toList();
        for (MatchRecord match : matches) {
            if (match.getGameScores() != null
                    || match.getScoreDisplay() != null
                    || match.getLeftGameWins() != null
                    || match.getRightGameWins() != null
                    || match.getRetiredSide() != null
                    || match.getLockedByUserId() != null
                    || match.getLockToken() != null) {
                throw new IllegalArgumentException(violationMessage);
            }
        }
        if (matchEventMapper.selectCount(new QueryWrapper<MatchEvent>().in("match_id", matchIds)) > 0
                || matchLineupConfigMapper.selectCount(new QueryWrapper<MatchLineupConfig>().in("match_id", matchIds)) > 0
                || teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>().in("match_id", matchIds)) > 0) {
            throw new IllegalArgumentException(violationMessage);
        }
    }

    /** type0 签位校验：覆盖范围=组别全名册，允许 null 轮空（禁同场双轮空）。 */
    private List<String> validateAndMapSlotOrder(UpdateDrawSlotsReq req, TournamentDivision division, List<Player> roster) {
        List<String> order = req == null ? null : req.getKnockoutSlotOrder();
        if (CollUtil.isEmpty(order)) {
            throw new IllegalArgumentException("必须提供 knockoutSlotOrder（签位顺序）");
        }
        int capacity = 1 << division.getKnockoutRounds();
        if (capacity > MAX_MANUAL_DRAW_SLOTS) {
            throw new IllegalArgumentException("手写签表最多支持 " + MAX_MANUAL_DRAW_SLOTS + " 个签位，当前需要 " + capacity);
        }
        if (order.size() != capacity) {
            throw new IllegalArgumentException("签位数量不匹配：当前赛制需要 " + capacity + " 个签位，收到 " + order.size());
        }
        Set<String> rosterIds = roster.stream().map(Player::getId).collect(Collectors.toSet());
        Set<String> usedIds = new HashSet<>();
        List<String> slotIds = new ArrayList<>(capacity);
        for (int slot = 0; slot < capacity; slot++) {
            String playerId = order.get(slot);
            if (playerId == null) {
                slotIds.add(null);
                continue;
            }
            if (!rosterIds.contains(playerId)) {
                throw new IllegalArgumentException("签位 " + (slot + 1) + " 的参赛单位不在本组别名册中");
            }
            if (!usedIds.add(playerId)) {
                throw new IllegalArgumentException("参赛单位被重复放入签位 " + (slot + 1));
            }
            slotIds.add(playerId);
        }
        if (usedIds.size() != rosterIds.size()) {
            throw new IllegalArgumentException("还有 " + (rosterIds.size() - usedIds.size()) + " 个参赛单位未放入签位");
        }
        for (int i = 0; i < capacity; i += 2) {
            if (order.get(i) == null && order.get(i + 1) == null) {
                throw new IllegalArgumentException("淘汰赛第 " + (i / 2 + 1) + " 场（签位 " + (i + 1) + "、" + (i + 2)
                        + "）均为轮空，请调整签位摆放");
            }
        }
        return slotIds;
    }

    /** type1 签位校验：覆盖范围=当前首轮参赛者（=出线者集合），无轮空（签位数=出线数，禁 null）。 */
    private List<String> validateGroupTournamentSlotOrder(UpdateDrawSlotsReq req, TournamentDivision division,
                                                          List<MatchRecord> knockoutMatches) {
        List<String> order = req == null ? null : req.getKnockoutSlotOrder();
        if (CollUtil.isEmpty(order)) {
            throw new IllegalArgumentException("必须提供 knockoutSlotOrder（签位顺序）");
        }
        int capacity = 1 << division.getKnockoutRounds();
        if (capacity > MAX_MANUAL_DRAW_SLOTS) {
            throw new IllegalArgumentException("手写签表最多支持 " + MAX_MANUAL_DRAW_SLOTS + " 个签位，当前需要 " + capacity);
        }
        if (order.size() != capacity) {
            throw new IllegalArgumentException("签位数量不匹配：当前淘汰赛需要 " + capacity + " 个签位，收到 " + order.size());
        }
        Set<String> qualifierIds = firstRoundParticipantIds(knockoutMatches);
        if (qualifierIds.size() != capacity) {
            throw new IllegalArgumentException("淘汰赛首轮签位不完整（应有 " + capacity + " 名出线选手，实际 "
                    + qualifierIds.size() + " 名），签表结构异常");
        }
        Set<String> usedIds = new HashSet<>();
        for (int slot = 0; slot < capacity; slot++) {
            String playerId = order.get(slot);
            if (playerId == null) {
                throw new IllegalArgumentException("小组赛+淘汰赛的淘汰赛签位不存在轮空，签位 " + (slot + 1) + " 必须填入出线选手");
            }
            if (!qualifierIds.contains(playerId)) {
                throw new IllegalArgumentException("签位 " + (slot + 1) + " 的参赛单位不在出线名单中");
            }
            if (!usedIds.add(playerId)) {
                throw new IllegalArgumentException("参赛单位被重复放入签位 " + (slot + 1));
            }
        }
        return new ArrayList<>(order);
    }

    /** 当前首轮参赛者集合 = 出线者集合（生成时全满）。 */
    private Set<String> firstRoundParticipantIds(List<MatchRecord> knockoutMatches) {
        Set<String> ids = new LinkedHashSet<>();
        List<MatchRecord> firstRound = knockoutMatches.stream()
                .filter(this::isFirstRoundSlotMatch)
                .sorted((a, b) -> Integer.compare(safeInt(a.getMatchIndex()), safeInt(b.getMatchIndex())))
                .toList();
        for (MatchRecord match : firstRound) {
            if (match.getLeftPlayerId() != null) {
                ids.add(match.getLeftPlayerId());
            }
            if (match.getRightPlayerId() != null) {
                ids.add(match.getRightPlayerId());
            }
        }
        return ids;
    }

    private void applySlotOrder(List<MatchRecord> matches, List<String> slotIds) {
        Map<String, MatchRecord> byId = matches.stream()
                .collect(Collectors.toMap(MatchRecord::getId, Function.identity(), (a, b) -> a));

        // 1) 清空旧签表传播来的一切选手位/胜者/状态（第二轮及以后 + 季军赛）
        for (MatchRecord match : matches) {
            if (isFirstRoundSlotMatch(match)) {
                continue;
            }
            matchRecordMapper.update(null, new UpdateWrapper<MatchRecord>()
                    .eq("id", match.getId())
                    .set("left_player_id", null)
                    .set("right_player_id", null)
                    .set("winner_id", null)
                    .set("status", 0));
        }

        // 2) 首轮按新签位写入，并重放轮空坍缩（type1 无轮空：oneSided 恒为 false，仅重写两侧）
        List<MatchRecord> firstRound = matches.stream()
                .filter(this::isFirstRoundSlotMatch)
                .sorted((a, b) -> Integer.compare(safeInt(a.getMatchIndex()), safeInt(b.getMatchIndex())))
                .toList();
        for (MatchRecord match : firstRound) {
            int index = safeInt(match.getMatchIndex());
            String left = slotIds.get(index * 2);
            String right = slotIds.get(index * 2 + 1);
            boolean oneSided = (left == null) != (right == null);
            String winner = oneSided ? (left != null ? left : right) : null;
            matchRecordMapper.update(null, new UpdateWrapper<MatchRecord>()
                    .eq("id", match.getId())
                    .set("left_player_id", left)
                    .set("right_player_id", right)
                    .set("winner_id", winner)
                    .set("status", oneSided ? 2 : 0));
            if (oneSided && match.getNextMatchId() != null) {
                propagateToParent(byId, match, winner);
            }
        }
    }

    /** 轮空坍缩只可能发生在首轮（容量为最小 2^k 且禁双轮空），传播单层即可。 */
    private void propagateToParent(Map<String, MatchRecord> byId, MatchRecord child, String winnerId) {
        MatchRecord parent = byId.get(child.getNextMatchId());
        if (parent == null) {
            return;
        }
        boolean leftSlot = "left".equals(child.getNextMatchSlot());
        if (!leftSlot && !"right".equals(child.getNextMatchSlot())) {
            throw new IllegalStateException("晋级链 slot 值非法: " + child.getNextMatchSlot() + " (match=" + child.getId() + ")");
        }
        matchRecordMapper.update(null, new UpdateWrapper<MatchRecord>()
                .eq("id", parent.getId())
                .set(leftSlot ? "left_player_id" : "right_player_id", winnerId));
    }

    // ==================================================================================
    // group-assignments：type1 手写分组重提交（首场小组赛开赛前）
    // ==================================================================================

    /**
     * 全量替换分组：重写 player.group_no/group_position → 全删全建小组赛赛程。
     * 窗口 = 该组别任何小组赛开赛前（痕迹法，只查 stage_type=0）；淘汰赛一旦生成即锁定。
     * 幂等：分组内容与现状一致时直接返回，不重建赛程（match id 保持稳定）。
     */
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void updateGroupAssignments(String userId, String tournamentId, String divisionId,
                                       UpdateGroupAssignmentsReq req) {
        Tournament tournament = tournamentMapper.selectById(tournamentId);
        if (tournament == null) {
            throw new IllegalArgumentException("赛事不存在: " + tournamentId);
        }
        accessGuard.requireNotArchived(tournament);
        accessGuard.requireCreator(userId, tournament);

        // 1) 先锁小组赛比赛行（与记分运行时路径一致的锁序）
        List<MatchRecord> groupMatches = matchRecordMapper.selectList(
                new QueryWrapper<MatchRecord>()
                        .eq("division_id", divisionId)
                        .eq("stage_type", STAGE_TYPE_GROUP)
                        .orderByAsc("round_num", "match_index")
                        .last("FOR UPDATE"));

        // 2) 再锁组别行
        TournamentDivision division = tournamentDivisionMapper.selectOne(
                new QueryWrapper<TournamentDivision>()
                        .eq("id", divisionId)
                        .eq("tournament_id", tournamentId)
                        .last("FOR UPDATE"));
        if (division == null) {
            throw new IllegalArgumentException("组别不存在: " + divisionId);
        }
        if (!Integer.valueOf(TYPE_GROUP).equals(division.getTournamentType())) {
            throw new IllegalArgumentException("仅小组赛+淘汰赛支持调整分组");
        }
        if (!Integer.valueOf(DRAW_MODE_MANUAL_GROUPS).equals(division.getDrawMode())) {
            throw new IllegalArgumentException("仅手写分组（drawMode=manual-groups）的赛事支持调整分组");
        }
        if (Boolean.TRUE.equals(division.getKnockoutGenerated())) {
            throw new IllegalArgumentException("淘汰赛已生成，分组不可再调整");
        }
        if (division.getKnockoutSlots() == null || division.getQualifiersPerGroup() == null
                || division.getQualifiersPerGroup() == 0) {
            throw new IllegalArgumentException("组别缺少分组配置（knockoutSlots/qualifiersPerGroup），结构异常");
        }

        List<Player> roster = loadRoster(division.getId());
        assertNoStartedTraces(groupMatches, "已有小组赛开始，分组不可再调整");

        List<List<String>> groups = validateGroupAssignments(req, division, roster);
        if (sameAsCurrentAssignment(roster, groups)) {
            return;
        }

        // 3) 重写分组（内存对象同步更新：后续 generateGroupMatches 依赖内存 groupNo/groupPosition）
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<String> group = groups.get(groupIndex);
            for (int position = 0; position < group.size(); position++) {
                Player player = playerById(roster, group.get(position));
                player.setGroupNo(groupIndex + 1);
                player.setGroupPosition(position + 1);
                playerMapper.update(null, new UpdateWrapper<Player>()
                        .eq("id", player.getId())
                        .set("group_no", player.getGroupNo())
                        .set("group_position", player.getGroupPosition()));
            }
        }

        // 4) 全删全建小组赛赛程（窗口内无事件/阵容/子项行，守卫已保证无孤儿数据）
        matchRecordMapper.delete(new QueryWrapper<MatchRecord>()
                .eq("division_id", division.getId())
                .eq("stage_type", STAGE_TYPE_GROUP));
        List<MatchRecord> regenerated = roundRobinEngine.generateGroupMatches(
                tournamentId, division.getId(), roster);
        for (MatchRecord match : regenerated) {
            match.setDivisionId(division.getId());
            matchRecordMapper.insert(match);
        }
    }

    /** 分组校验：组数=knockoutSlots/qualifiers；每组 ≥ max(2, qualifiers)（允许组间不均）；每人恰属一组。 */
    private List<List<String>> validateGroupAssignments(UpdateGroupAssignmentsReq req, TournamentDivision division,
                                                        List<Player> roster) {
        List<List<String>> groups = req == null ? null : req.getGroups();
        if (CollUtil.isEmpty(groups)) {
            throw new IllegalArgumentException("必须提供 groups（分组结果）");
        }
        int groupCount = division.getKnockoutSlots() / division.getQualifiersPerGroup();
        if (groups.size() != groupCount) {
            throw new IllegalArgumentException("分组数量不匹配：当前赛制需要 " + groupCount + " 组，收到 " + groups.size() + " 组");
        }
        int minPerGroup = Math.max(2, division.getQualifiersPerGroup());
        Set<String> rosterIds = roster.stream().map(Player::getId).collect(Collectors.toSet());
        Set<String> usedIds = new HashSet<>();
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<String> group = groups.get(groupIndex);
            int groupSize = CollUtil.isEmpty(group) ? 0 : group.size();
            if (groupSize < minPerGroup) {
                throw new IllegalArgumentException("第 " + (groupIndex + 1) + " 组至少需要 " + minPerGroup
                        + " 个参赛单位（当前 " + groupSize + " 个）");
            }
            for (String playerId : group) {
                if (playerId == null || playerId.isBlank()) {
                    throw new IllegalArgumentException("第 " + (groupIndex + 1) + " 组存在空的参赛单位条目");
                }
                if (!rosterIds.contains(playerId)) {
                    throw new IllegalArgumentException("第 " + (groupIndex + 1) + " 组存在不属于本组别名册的参赛单位");
                }
                if (!usedIds.add(playerId)) {
                    throw new IllegalArgumentException("参赛单位被重复放入分组");
                }
            }
        }
        if (usedIds.size() != rosterIds.size()) {
            throw new IllegalArgumentException("还有 " + (rosterIds.size() - usedIds.size()) + " 个参赛单位未放入分组");
        }
        return groups;
    }

    /** 幂等判定：按 playerId 比对 (groupNo, groupPosition) 是否与现状完全一致。 */
    private boolean sameAsCurrentAssignment(List<Player> roster, List<List<String>> groups) {
        Map<String, Player> byId = roster.stream()
                .collect(Collectors.toMap(Player::getId, Function.identity(), (a, b) -> a));
        for (int groupIndex = 0; groupIndex < groups.size(); groupIndex++) {
            List<String> group = groups.get(groupIndex);
            for (int position = 0; position < group.size(); position++) {
                Player player = byId.get(group.get(position));
                if (player == null) {
                    return false;
                }
                Integer expectedNo = groupIndex + 1;
                Integer expectedPos = position + 1;
                if (!expectedNo.equals(player.getGroupNo()) || !expectedPos.equals(player.getGroupPosition())) {
                    return false;
                }
            }
        }
        long assignedCount = groups.stream().mapToLong(List::size).sum();
        return assignedCount == roster.size();
    }

    private Player playerById(List<Player> roster, String playerId) {
        return roster.stream()
                .filter(player -> playerId.equals(player.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("参赛单位不在本组别名册中: " + playerId));
    }

    private List<Player> loadRoster(String divisionId) {
        return playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("division_id", divisionId)
                        .orderByAsc("create_time", "id"));
    }

    private boolean isFirstRoundSlotMatch(MatchRecord match) {
        return Integer.valueOf(1).equals(match.getRoundNum())
                && !Integer.valueOf(MATCH_ROLE_THIRD_PLACE).equals(match.getMatchRole());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
