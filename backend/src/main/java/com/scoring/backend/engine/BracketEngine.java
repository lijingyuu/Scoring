package com.scoring.backend.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class BracketEngine {

    public List<MatchRecord> generateKnockoutBracket(String tournamentId, List<Player> players) {
        Assert.notBlank(tournamentId, "tournamentId不能为空");
        Assert.isTrue(CollUtil.isNotEmpty(players), "players不能为空");

        int n = players.size();
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
        Assert.notBlank(tournamentId, "tournamentId涓嶈兘涓虹┖");
        Assert.isTrue(CollUtil.isNotEmpty(playerIds), "playerIds涓嶈兘涓虹┖");

        int p = playerIds.size();
        Assert.isTrue((p & (p - 1)) == 0, "playerIds size must be power of two");
        int roundCount = Integer.numberOfTrailingZeros(p);

        List<List<MatchRecord>> rounds = createEmptyRounds(tournamentId, p, roundCount);
        linkRounds(rounds, roundCount);

        List<MatchRecord> firstRound = rounds.get(0);
        for (int i = 0; i < firstRound.size(); i++) {
            MatchRecord match = firstRound.get(i);
            match.setLeftPlayerId(playerIds.get(i * 2));
            match.setRightPlayerId(playerIds.get(i * 2 + 1));
        }

        List<MatchRecord> all = new ArrayList<>();
        for (List<MatchRecord> roundMatches : rounds) {
            all.addAll(roundMatches);
        }
        return all;
    }

    private List<List<MatchRecord>> createEmptyRounds(String tournamentId, int p, int roundCount) {
        List<List<MatchRecord>> rounds = new ArrayList<>();
        for (int round = 1; round <= roundCount; round++) {
            int matchCount = p >> round;
            List<MatchRecord> currentRound = new ArrayList<>(matchCount);
            for (int idx = 0; idx < matchCount; idx++) {
                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
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
}
