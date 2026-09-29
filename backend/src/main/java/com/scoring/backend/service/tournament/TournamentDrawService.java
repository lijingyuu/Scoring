package com.scoring.backend.service.tournament;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.scoring.backend.domain.dto.UpdateDrawSlotsReq;
import com.scoring.backend.domain.entity.MatchEvent;
import com.scoring.backend.domain.entity.MatchLineupConfig;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.entity.TeamMatchItem;
import com.scoring.backend.domain.entity.Tournament;
import com.scoring.backend.domain.entity.TournamentDivision;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 手写签表：开赛前调整签位（设计见 docs/手写签表改造方案.md）。
 *
 * 语义：全量替换首轮签位。同容量下签表结构（轮次/场次/晋级链）不变，
 * 实现为原地 UPDATE 首轮各场 left/right 并重放轮空坍缩；第二轮及以后、季军赛
 * 清空由旧签表传播来的选手位后按需重填。match id 与晋级链不动。
 *
 * 前置条件（全部满足才允许编辑）：
 * 仅创建者（裁判不可改签）、未归档、组别为手写签表的纯淘汰赛、零开赛。
 */
@Service
public class TournamentDrawService {

    private static final int TYPE_KNOCKOUT = 0;
    private static final int DRAW_MODE_MANUAL = 1;
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

    public TournamentDrawService(TournamentMapper tournamentMapper,
                                 TournamentDivisionMapper tournamentDivisionMapper,
                                 PlayerMapper playerMapper,
                                 MatchRecordMapper matchRecordMapper,
                                 MatchEventMapper matchEventMapper,
                                 MatchLineupConfigMapper matchLineupConfigMapper,
                                 TeamMatchItemMapper teamMatchItemMapper,
                                 TournamentAccessGuard accessGuard) {
        this.tournamentMapper = tournamentMapper;
        this.tournamentDivisionMapper = tournamentDivisionMapper;
        this.playerMapper = playerMapper;
        this.matchRecordMapper = matchRecordMapper;
        this.matchEventMapper = matchEventMapper;
        this.matchLineupConfigMapper = matchLineupConfigMapper;
        this.teamMatchItemMapper = teamMatchItemMapper;
        this.accessGuard = accessGuard;
    }

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
        if (!Integer.valueOf(TYPE_KNOCKOUT).equals(division.getTournamentType())) {
            throw new IllegalArgumentException("仅纯淘汰赛支持调整签位");
        }
        if (!Integer.valueOf(DRAW_MODE_MANUAL).equals(division.getDrawMode())) {
            throw new IllegalArgumentException("仅手写签表（drawMode=manual）的赛事支持调整签位");
        }

        List<Player> roster = playerMapper.selectList(
                new QueryWrapper<Player>()
                        .eq("division_id", divisionId)
                        .orderByAsc("create_time", "id"));

        assertZeroStarted(matches);
        validateBracketStructure(division, matches);

        List<String> slotIds = validateAndMapSlotOrder(req, division, roster);
        applySlotOrder(matches, slotIds);
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
        if (CollUtil.isEmpty(matches)) {
            throw new IllegalArgumentException("签表尚未生成");
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
                throw new IllegalArgumentException("已有比赛开始，签表不可再编辑");
            }
        }
        if (matchEventMapper.selectCount(new QueryWrapper<MatchEvent>().in("match_id", matchIds)) > 0
                || matchLineupConfigMapper.selectCount(new QueryWrapper<MatchLineupConfig>().in("match_id", matchIds)) > 0
                || teamMatchItemMapper.selectCount(new QueryWrapper<TeamMatchItem>().in("match_id", matchIds)) > 0) {
            throw new IllegalArgumentException("已有比赛开始，签表不可再编辑");
        }
    }

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

        // 2) 首轮按新签位写入，并重放轮空坍缩
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

    private boolean isFirstRoundSlotMatch(MatchRecord match) {
        return Integer.valueOf(1).equals(match.getRoundNum())
                && !Integer.valueOf(MATCH_ROLE_THIRD_PLACE).equals(match.getMatchRole());
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }
}
