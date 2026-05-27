package com.scoring.backend.engine;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class RoundRobinEngine {

    public List<MatchRecord> generateGroupMatches(String tournamentId, List<Player> players) {
        Assert.notBlank(tournamentId, "tournamentId must not be blank");
        Assert.isTrue(CollUtil.isNotEmpty(players), "players must not be empty");

        Map<Integer, List<Player>> groups = players.stream()
                .filter(player -> player.getGroupNo() != null)
                .collect(Collectors.groupingBy(Player::getGroupNo));

        List<MatchRecord> all = new ArrayList<>();
        groups.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> all.addAll(generateOneGroup(tournamentId, entry.getKey(), entry.getValue())));
        return all;
    }

    private List<MatchRecord> generateOneGroup(String tournamentId, Integer groupNo, List<Player> groupPlayers) {
        List<Player> ordered = groupPlayers.stream()
                .sorted(Comparator
                        .comparing(Player::getGroupPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Player::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Player::getId))
                .collect(Collectors.toCollection(ArrayList::new));

        Assert.isTrue(ordered.size() >= 2, "each group must have at least 2 players");

        boolean hasBye = ordered.size() % 2 == 1;
        if (hasBye) {
            ordered.add(null);
        }

        int size = ordered.size();
        int rounds = size - 1;
        int pairsPerRound = size / 2;
        List<Player> rotating = new ArrayList<>(ordered);
        List<MatchRecord> matches = new ArrayList<>();

        for (int round = 1; round <= rounds; round++) {
            int matchIndex = 0;
            for (int pair = 0; pair < pairsPerRound; pair++) {
                Player left = rotating.get(pair);
                Player right = rotating.get(size - 1 - pair);
                if (left == null || right == null) {
                    continue;
                }

                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
                match.setStageType(0);
                match.setGroupNo(groupNo);
                match.setRoundNum(round);
                match.setMatchIndex(matchIndex++);
                match.setLeftPlayerId(left.getId());
                match.setRightPlayerId(right.getId());
                match.setStatus(0);
                matches.add(match);
            }
            rotateKeepingFirst(rotating);
        }
        return matches;
    }

    private void rotateKeepingFirst(List<Player> players) {
        if (players.size() <= 2) {
            return;
        }
        Player last = players.remove(players.size() - 1);
        players.add(1, last);
    }
}
