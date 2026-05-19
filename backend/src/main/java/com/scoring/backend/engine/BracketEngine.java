package com.scoring.backend.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
public class BracketEngine {

    public List<MatchRecord> generateKnockoutBracket(String tournamentId, List<Player> players) {
        Assert.notBlank(tournamentId, "tournamentId不能为空");
        Assert.isTrue(CollUtil.isNotEmpty(players), "players不能为空");

        int n = players.size();
        int p = calcPowerOfTwoCapacity(n);
        int roundCount = Integer.numberOfTrailingZeros(p);

        List<Integer> seeds = buildSeedOrder(p);
        Assert.isTrue(seeds.size() == p, "种子数组长度不正确");

        List<Player> shuffledPlayers = new ArrayList<>(players);
        Collections.shuffle(shuffledPlayers);

        String[] slots = new String[p];
        int playerCursor = 0;
        for (int i = 0; i < p; i++) {
            int seedNo = seeds.get(i);
            boolean isBye = seedNo > n;
            if (!isBye) {
                slots[i] = shuffledPlayers.get(playerCursor).getId();
                playerCursor++;
            }
        }

        List<List<MatchRecord>> rounds = new ArrayList<>();
        for (int round = 1; round <= roundCount; round++) {
            int matchCount = p >> round;
            List<MatchRecord> currentRound = new ArrayList<>(matchCount);
            for (int idx = 0; idx < matchCount; idx++) {
                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
                match.setRoundNum(round);
                match.setStatus(0);
                currentRound.add(match);
            }
            rounds.add(currentRound);
        }

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

        List<MatchRecord> firstRound = rounds.get(0);
        for (int i = 0; i < firstRound.size(); i++) {
            MatchRecord match = firstRound.get(i);
            match.setLeftPlayerId(slots[i * 2]);
            match.setRightPlayerId(slots[i * 2 + 1]);
        }

        // 第一轮轮空自动坍缩
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
