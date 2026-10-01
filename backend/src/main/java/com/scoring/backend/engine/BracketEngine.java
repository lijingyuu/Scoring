package com.scoring.backend.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import com.scoring.backend.domain.vo.GroupStandingsVO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class BracketEngine {

    public KnockoutPlan buildGroupedKnockoutPlan(GroupStandingsVO standingsVO) {
        if (standingsVO == null) {
            throw new IllegalArgumentException("standingsVO cannot be null");
        }
        List<GroupRank> qualifiers = collectQualifiers(standingsVO);
        List<String> slots = buildKnockoutSlots(qualifiers, standingsVO.getQualifiersPerGroup() == null ? 0 : standingsVO.getQualifiersPerGroup());
        return new KnockoutPlan(qualifiers, slots);
    }

    public List<MatchRecord> generateKnockoutBracket(String tournamentId, List<Player> players) {
        return generateKnockoutBracket(tournamentId, null, players);
    }

    /**
     * 组别版：生成的 match_record 同写 tournament_id + division_id。
     * divisionId 允许为 null（旧调用方过渡，见组别层级改造工作包 #3）。
     */
    public List<MatchRecord> generateKnockoutBracket(String tournamentId, String divisionId, List<Player> players) {
        Assert.notBlank(tournamentId, "tournamentId不能为空");
        Assert.isTrue(CollUtil.isNotEmpty(players), "players不能为空");

        int n = players.size();
        Assert.isTrue(n >= 2, "淘汰赛至少需要2个参赛单位");
        int p = calcPowerOfTwoCapacity(n);
        int roundCount = Integer.numberOfTrailingZeros(p);

        List<Integer> seedOrder = buildSeedOrder(p);
        Assert.isTrue(seedOrder.size() == p, "种子数组长度不正确");

        // ---- 分离种子选手与普通选手 ----
        List<Player> seededPlayers = new ArrayList<>();
        List<Player> unseededPlayers = new ArrayList<>();
        Set<Integer> usedSeeds = new HashSet<>();

        for (Player player : players) {
            Integer rank = player.getSeedRank();
            if (rank != null) {
                if (rank < 1 || rank > n) {
                    throw new IllegalArgumentException(
                            "种子序号 " + rank + " 超出范围 [1, " + n + "]，选手: " + player.getName());
                }
                if (!usedSeeds.add(rank)) {
                    throw new IllegalArgumentException("种子序号 " + rank + " 重复，选手: " + player.getName());
                }
                seededPlayers.add(player);
            } else {
                unseededPlayers.add(player);
            }
        }

        // ---- 按坑位填表 ----
        // slots[i] = 0 表示轮空(bye), 非空为 playerId
        String[] slots = new String[p];

        // 1. 安置真实种子选手到精确槽位
        for (Player sp : seededPlayers) {
            int rank = sp.getSeedRank();
            int slotIndex = seedOrder.indexOf(rank);
            if (slotIndex < 0) {
                throw new IllegalStateException("种子序号 " + rank + " 在种子序列中未找到");
            }
            slots[slotIndex] = sp.getId();
        }

        // 2. 轮空(Bye): seedOrder 中值 > n 的位置 — 保持 null (已默认)
        // 3. 随机填补剩余普通选手到非 Bye 且未被种子占用的槽位
        List<Player> shuffledUnseeded = new ArrayList<>(unseededPlayers);
        Collections.shuffle(shuffledUnseeded);
        int cursor = 0;
        for (int i = 0; i < p; i++) {
            if (slots[i] != null) continue;
            int seedNo = seedOrder.get(i);
            if (seedNo > n) continue; // bye 槽位
            if (cursor >= shuffledUnseeded.size()) {
                throw new IllegalStateException("普通选手不足，无法填满槽位");
            }
            slots[i] = shuffledUnseeded.get(cursor).getId();
            cursor++;
        }

        // ---- 创建比赛记录 ----
        List<List<MatchRecord>> rounds = new ArrayList<>();
        for (int round = 1; round <= roundCount; round++) {
            int matchCount = p >> round;
            List<MatchRecord> currentRound = new ArrayList<>(matchCount);
            for (int idx = 0; idx < matchCount; idx++) {
                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
                match.setDivisionId(divisionId);
                match.setStageType(1);
                match.setMatchRole(0);
                match.setRoundNum(round);
                match.setMatchIndex(idx);
                match.setStatus(0);
                currentRound.add(match);
            }
            rounds.add(currentRound);
        }

        // ---- 链接父子关系 ----
        for (int round = 1; round < roundCount; round++) {
            List<MatchRecord> currentRound = rounds.get(round - 1);
            List<MatchRecord> parentRound = rounds.get(round);
            for (int i = 0; i < currentRound.size(); i++) {
                MatchRecord child = currentRound.get(i);
                MatchRecord parent = parentRound.get(i / 2);
                child.setNextMatchId(parent.getId());
                child.setNextMatchSlot(i % 2 == 0 ? "left" : "right");
            }
        }

        MatchRecord finalMatch = rounds.get(roundCount - 1).get(0);
        finalMatch.setNextMatchId(null);
        finalMatch.setNextMatchSlot(null);

        // ---- 填充第一轮选手 ----
        List<MatchRecord> firstRound = rounds.get(0);
        for (int i = 0; i < firstRound.size(); i++) {
            MatchRecord match = firstRound.get(i);
            match.setLeftPlayerId(slots[i * 2]);
            match.setRightPlayerId(slots[i * 2 + 1]);
        }

        // ---- 第一轮轮空自动坍缩 ----
        for (MatchRecord match : firstRound) {
            String left = match.getLeftPlayerId();
            String right = match.getRightPlayerId();
            boolean leftExists = left != null;
            boolean rightExists = right != null;

            if (leftExists ^ rightExists) {
                String winner = leftExists ? left : right;
                match.setWinnerId(winner);
                match.setStatus(2);
                propagateWinnerToParent(rounds, match, winner);
            }
        }

        List<MatchRecord> all = new ArrayList<>();
        for (List<MatchRecord> roundMatches : rounds) {
            all.addAll(roundMatches);
        }

        Assert.isTrue(finalMatch.getNextMatchId() == null, "决赛next_match_id必须为空");
        Assert.isTrue(finalMatch.getNextMatchSlot() == null, "决赛next_match_slot必须为空");
        return all;
    }

    public List<MatchRecord> generateKnockoutBracketBySlots(String tournamentId, List<String> playerIds) {
        return generateKnockoutBracketBySlots(tournamentId, null, playerIds);
    }

    /**
     * 组别版：生成的 match_record 同写 tournament_id + division_id（divisionId 允许为 null，供旧调用方过渡）。
     * 槽位列表允许 null 元素 = 轮空（bye）：首轮单侧轮空的比赛直接判定胜者并向上传播。
     * 同一场比赛的两个槽位不允许同时为 null——双轮空会产生空胜者并沿晋级链级联，赛事永远无法完结。
     * 全量传入（无 null）时行为与历史版本完全一致。
     */
    public List<MatchRecord> generateKnockoutBracketBySlots(String tournamentId, String divisionId, List<String> playerIds) {
        Assert.notBlank(tournamentId, "tournamentId不能为空");
        Assert.isTrue(CollUtil.isNotEmpty(playerIds), "playerIds不能为空");

        int p = playerIds.size();
        Assert.isTrue(p >= 2, "playerIds size must be at least 2");
        Assert.isTrue((p & (p - 1)) == 0, "playerIds size must be power of two");
        int roundCount = Integer.numberOfTrailingZeros(p);

        List<List<MatchRecord>> rounds = createEmptyRounds(tournamentId, divisionId, p, roundCount);
        linkRounds(rounds, roundCount);

        List<MatchRecord> firstRound = rounds.get(0);
        for (int i = 0; i < firstRound.size(); i++) {
            String left = playerIds.get(i * 2);
            String right = playerIds.get(i * 2 + 1);
            if (left == null && right == null) {
                throw new IllegalArgumentException("淘汰赛第 " + (i + 1) + " 场（签位 " + (i * 2 + 1) + "、" + (i * 2 + 2)
                        + "）均为轮空，请调整签位摆放");
            }
            MatchRecord match = firstRound.get(i);
            match.setLeftPlayerId(left);
            match.setRightPlayerId(right);
        }

        // ---- 首轮轮空自动坍缩 ----
        // 容量为 ≥人数的最小 2 的幂且禁双轮空时，第二轮必然两侧齐备，坍缩不会级联。
        for (MatchRecord match : firstRound) {
            String left = match.getLeftPlayerId();
            String right = match.getRightPlayerId();
            boolean leftExists = left != null;
            boolean rightExists = right != null;
            if (leftExists ^ rightExists) {
                String winner = leftExists ? left : right;
                match.setWinnerId(winner);
                match.setStatus(2);
                propagateWinnerToParent(rounds, match, winner);
            }
        }

        List<MatchRecord> all = new ArrayList<>();
        for (List<MatchRecord> roundMatches : rounds) {
            all.addAll(roundMatches);
        }
        return all;
    }

    private List<List<MatchRecord>> createEmptyRounds(String tournamentId, String divisionId, int p, int roundCount) {
        List<List<MatchRecord>> rounds = new ArrayList<>();
        for (int round = 1; round <= roundCount; round++) {
            int matchCount = p >> round;
            List<MatchRecord> currentRound = new ArrayList<>(matchCount);
            for (int idx = 0; idx < matchCount; idx++) {
                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
                match.setDivisionId(divisionId);
                match.setStageType(1);
                match.setMatchRole(0);
                match.setRoundNum(round);
                match.setMatchIndex(idx);
                match.setStatus(0);
                currentRound.add(match);
            }
            rounds.add(currentRound);
        }
        return rounds;
    }

    private void linkRounds(List<List<MatchRecord>> rounds, int roundCount) {
        for (int round = 1; round < roundCount; round++) {
            List<MatchRecord> currentRound = rounds.get(round - 1);
            List<MatchRecord> parentRound = rounds.get(round);
            for (int i = 0; i < currentRound.size(); i++) {
                MatchRecord child = currentRound.get(i);
                MatchRecord parent = parentRound.get(i / 2);
                child.setNextMatchId(parent.getId());
                child.setNextMatchSlot(i % 2 == 0 ? "left" : "right");
            }
        }

        MatchRecord finalMatch = rounds.get(roundCount - 1).get(0);
        finalMatch.setNextMatchId(null);
        finalMatch.setNextMatchSlot(null);
    }

    private void propagateWinnerToParent(List<List<MatchRecord>> rounds, MatchRecord current, String winnerId) {
        if (current.getNextMatchId() == null) {
            return;
        }

        int parentRoundNum = current.getRoundNum() + 1;
        if (parentRoundNum > rounds.size()) {
            return;
        }

        List<MatchRecord> parentRound = rounds.get(parentRoundNum - 1);
        for (MatchRecord parent : parentRound) {
            if (!parent.getId().equals(current.getNextMatchId())) {
                continue;
            }
            if ("left".equals(current.getNextMatchSlot())) {
                parent.setLeftPlayerId(winnerId);
            } else if ("right".equals(current.getNextMatchSlot())) {
                parent.setRightPlayerId(winnerId);
            }
            break;
        }
    }

    private List<GroupRank> collectQualifiers(GroupStandingsVO standingsVO) {
        List<GroupRank> qualifiers = new ArrayList<>();
        if (standingsVO.getGroups() == null) {
            return qualifiers;
        }
        for (GroupStandingsVO.GroupVO group : standingsVO.getGroups()) {
            if (group == null || group.getStandings() == null) {
                continue;
            }
            for (GroupStandingsVO.StandingVO standing : group.getStandings()) {
                if (standing == null) {
                    continue;
                }
                if (Boolean.TRUE.equals(standing.getQualified()) && !Boolean.TRUE.equals(standing.getTieUnresolved())) {
                    qualifiers.add(new GroupRank(group.getGroupNo(), standing.getRank(), standing.getPlayerId()));
                }
            }
        }
        return qualifiers;
    }

    /**
     * 小组晋级淘汰赛的签位编排：各组第 1 名按组号正序、第 2 名按组号倒序交错配对，
     * 保证同组回避（同组两名出线者不会在首轮相遇）。
     *
     * 前置条件：每组恰好贡献 1 名第 1 名与 1 名第 2 名（qualifiersPerGroup=2）。
     * 若某组的第 1 名找不到"其他小组"的第 2 名可配对（出线名单缺失或不平衡），按贡献组数分派：
     * - 仅 1 个组贡献出线者（单组赛事，出线即同组前二）：同组配对是唯一可能，保留历史回退
     *   （TournamentControllerIntegrationTest 钉死该产品行为：generate-knockout 返回 200）；
     * - 多组贡献出线者却无法满足同组回避（如某组第 2 名因并列未破平被排除）：显式抛出
     *   IllegalArgumentException，而不是静默配成同组内战（生产侧 loadGroupedKnockoutContext
     *   通常已被 hasUnresolvedTie / 签位数守卫提前拦截，这里是引擎层的最后一道防线）。
     */
    private List<String> buildKnockoutSlots(List<GroupRank> qualifiers, int qualifiersPerGroup) {
        Map<Integer, List<GroupRank>> byRank = qualifiers.stream().collect(Collectors.groupingBy(GroupRank::rank));
        List<GroupRank> firsts = byRank.getOrDefault(1, List.of()).stream()
                .sorted(java.util.Comparator.comparingInt(GroupRank::groupNo))
                .collect(Collectors.toList());
        if (qualifiersPerGroup == 1) {
            return firsts.stream().map(GroupRank::playerId).collect(Collectors.toList());
        }

        List<GroupRank> seconds = new ArrayList<>(byRank.getOrDefault(2, List.of()).stream()
                .sorted(java.util.Comparator.comparingInt(GroupRank::groupNo).reversed())
                .collect(Collectors.toList()));
        List<String> slots = new ArrayList<>();
        for (GroupRank first : firsts) {
            int secondIndex = findOpponentIndex(seconds, first.groupNo());
            if (secondIndex < 0) {
                long contributingGroups = qualifiers.stream().map(GroupRank::groupNo).distinct().count();
                if (contributingGroups <= 1) {
                    secondIndex = 0; // 单组赛事：同组前二内战是唯一合法对阵
                } else {
                    throw new IllegalArgumentException("无法生成满足同组回避的小组晋级淘汰赛对阵：小组 "
                            + first.groupNo() + " 的第 1 名找不到其他小组的第 2 名对手（出线名单缺失或不平衡）");
                }
            }
            GroupRank second = seconds.remove(secondIndex);
            slots.add(first.playerId());
            slots.add(second.playerId());
        }
        return slots;
    }

    private int findOpponentIndex(List<GroupRank> seconds, Integer forbiddenGroupNo) {
        for (int i = 0; i < seconds.size(); i++) {
            if (!seconds.get(i).groupNo().equals(forbiddenGroupNo)) {
                return i;
            }
        }
        return -1;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private int calcPowerOfTwoCapacity(int n) {
        int p = 1;
        while (p < n) {
            p <<= 1;
        }
        return p;
    }

    /**
     * 标准淘汰赛种子序列生成（递归分治）。
     * 例: p=8 → [1,8,4,5,2,7,3,6]
     * 保证: 1号与2号分在上下半区, 1号与4号分在不同四分之一区, 相邻两两配对和为 p+1。
     */
    private List<Integer> buildSeedOrder(int p) {
        if (p == 1) {
            List<Integer> base = new ArrayList<>();
            base.add(1);
            return base;
        }
        List<Integer> prev = buildSeedOrder(p / 2);
        List<Integer> result = new ArrayList<>(p);
        for (Integer seed : prev) {
            result.add(seed);
            result.add(p + 1 - seed);
        }

        for (int i = 0; i < result.size(); i += 2) {
            int left = result.get(i);
            int right = result.get(i + 1);
            Assert.isTrue(left + right == p + 1, "相邻种子和必须等于P+1");
        }
        return result;
    }

    public record GroupRank(Integer groupNo, Integer rank, String playerId) {
    }

    public record KnockoutPlan(List<GroupRank> qualifiers, List<String> slots) {
    }
}
