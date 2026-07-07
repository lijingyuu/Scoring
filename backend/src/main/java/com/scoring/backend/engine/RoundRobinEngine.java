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

    /**
     * Generate matches for grouped round-robin (tournamentType=1, group stage).
     * Players must already have {@code groupNo} assigned.
     */
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

    /**
     * Generate league-style round-robin matches for all participants in a single pool.
     * Used by pure round-robin tournament type (tournamentType=2).
     *
     * @param tournamentId tournament id
     * @param players      all participants (no group needed)
     * @param rounds       1 = single round robin, 2 = double round robin
     */
    public List<MatchRecord> generateLeagueMatches(String tournamentId, List<Player> players, int rounds) {
        Assert.notBlank(tournamentId, "tournamentId must not be blank");
        Assert.isTrue(CollUtil.isNotEmpty(players), "players must not be empty");
        Assert.isTrue(players.size() >= 2, "at least 2 players required for round robin");
        Assert.isTrue(rounds >= 1 && rounds <= 2, "rounds must be 1 (single) or 2 (double)");

        List<Player> ordered = players.stream()
                .sorted(Comparator
                        .comparing(Player::getSeedRank, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Player::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Player::getId))
                .collect(Collectors.toCollection(ArrayList::new));

        int singleRoundRobinRounds = calcRoundCount(ordered.size());
        List<MatchRecord> all = new ArrayList<>();

        for (int r = 0; r < rounds; r++) {
            boolean swapHomeAway = (r == 1);
            List<MatchRecord> roundMatches = generateOneRound(
                    tournamentId, ordered,
                    r * singleRoundRobinRounds,  // base round number offset
                    swapHomeAway
            );
            all.addAll(roundMatches);
        }
        return all;
    }

    // ─── internal ───

    private List<MatchRecord> generateOneGroup(String tournamentId, Integer groupNo, List<Player> groupPlayers) {
        List<Player> ordered = groupPlayers.stream()
                .sorted(Comparator
                        .comparing(Player::getGroupPosition, Comparator.nullsLast(Integer::compareTo))
                        .thenComparing(Player::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(Player::getId))
                .collect(Collectors.toCollection(ArrayList::new));

        Assert.isTrue(ordered.size() >= 2, "each group must have at least 2 players");

        int singleRounds = calcRoundCount(ordered.size());
        List<MatchRecord> all = new ArrayList<>();
        List<MatchRecord> roundMatches = generateOneRound(tournamentId, ordered, 0, false);
        for (MatchRecord m : roundMatches) {
            m.setStageType(0);
            m.setGroupNo(groupNo);
        }
        all.addAll(roundMatches);
        return all;
    }

    /**
     * Generate one complete round-robin cycle using the circle method.
     *
     * @param tournamentId  tournament id
     * @param ordered       ordered participant list (sorted by seed / create time / id)
     * @param baseRoundNum  starting round number (0-based offset, actual round = base + cycleRound)
     * @param swapHomeAway  if true, swap left/right (used for double round robin second cycle)
     */
    private List<MatchRecord> generateOneRound(String tournamentId, List<Player> ordered,
                                               int baseRoundNum, boolean swapHomeAway) {
        List<Player> players = new ArrayList<>(ordered);
        boolean hasBye = players.size() % 2 == 1;
        if (hasBye) {
            players.add(null);  // null represents a bye
        }

        int size = players.size();
        int rounds = size - 1;
        int pairsPerRound = size / 2;
        List<Player> rotating = new ArrayList<>(players);
        List<MatchRecord> matches = new ArrayList<>();

        for (int round = 1; round <= rounds; round++) {
            int matchIndex = 0;
            for (int pair = 0; pair < pairsPerRound; pair++) {
                Player leftRaw = rotating.get(pair);
                Player rightRaw = rotating.get(size - 1 - pair);
                if (leftRaw == null || rightRaw == null) {
                    continue;
                }

                Player left = swapHomeAway ? rightRaw : leftRaw;
                Player right = swapHomeAway ? leftRaw : rightRaw;

                MatchRecord match = new MatchRecord();
                match.setId(IdUtil.simpleUUID());
                match.setTournamentId(tournamentId);
                match.setStageType(1);          // league matches use stageType=1 (non-group)
                match.setRoundNum(baseRoundNum + round);
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

    private int calcRoundCount(int playerCount) {
        return playerCount % 2 == 1 ? playerCount : playerCount - 1;
    }

    private void rotateKeepingFirst(List<Player> players) {
        if (players.size() <= 2) {
            return;
        }
        Player last = players.remove(players.size() - 1);
        players.add(1, last);
    }
}
