package com.scoring.backend.engine.ranking;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class GroupStandingEngine {

    private static final BigDecimal INFINITE_RATE = new BigDecimal("999999.0000");
    private static final BigDecimal ZERO_RATE = new BigDecimal("0.0000");

    public List<Standing> rank(List<Player> players,
                               List<MatchRecord> matches,
                               Integer qualifiersPerGroup,
                               RankingConfig config) {
        RankingConfig effectiveConfig = config == null ? RankingConfig.legacyDefault() : config;
        List<MatchRecord> effectiveMatches = applyWithdrawPolicy(matches, effectiveConfig);
        List<Player> effectivePlayers = applyWithdrawPolicy(players, matches, effectiveConfig);
        Map<String, Standing> standingMap = new LinkedHashMap<>();
        for (Player player : effectivePlayers) {
            if (player == null || player.getId() == null) {
                continue;
            }
            Standing standing = new Standing();
            standing.playerId = player.getId();
            standing.playerName = player.getName() == null ? "" : player.getName();
            standing.seedRank = player.getSeedRank();
            standingMap.put(player.getId(), standing);
        }

        Map<String, Integer> h2hBalance = new HashMap<>();
        for (MatchRecord match : effectiveMatches) {
            if (!isResultMatch(match)) {
                continue;
            }
            Standing left = standingMap.get(match.getLeftPlayerId());
            Standing right = standingMap.get(match.getRightPlayerId());
            if (left == null || right == null) {
                continue;
            }

            boolean leftWon = match.getWinnerId().equals(match.getLeftPlayerId());
            if (leftWon) {
                left.matchWins++;
                right.matchLosses++;
            } else {
                right.matchWins++;
                left.matchLosses++;
            }
            if (usesTeamItemStats(effectiveConfig)) {
                left.teamItemWins += safeInt(match.getLeftGameWins());
                left.teamItemLosses += safeInt(match.getRightGameWins());
                right.teamItemWins += safeInt(match.getRightGameWins());
                right.teamItemLosses += safeInt(match.getLeftGameWins());
                applyGameAndPointStatsFromScores(match, left, right);
            } else {
                left.gameWins += safeInt(match.getLeftGameWins());
                left.gameLosses += safeInt(match.getRightGameWins());
                right.gameWins += safeInt(match.getRightGameWins());
                right.gameLosses += safeInt(match.getLeftGameWins());
                applyPointStats(match, left, right);
            }
            applyMatchPoints(match, left, right, effectiveConfig.getPointsSystem());
            addHeadToHeadBalance(h2hBalance, match.getLeftPlayerId(), match.getRightPlayerId(), match.getWinnerId());
        }
        standingMap.values().forEach(this::calculateRates);

        Map<String, String> h2hWinner = resolveHeadToHeadWinners(h2hBalance);
        List<Standing> standings = orderByPriority(
                new ArrayList<>(standingMap.values()),
                effectiveMatches,
                effectiveConfig,
                h2hWinner
        );
        markRanksAndTies(standings, effectiveMatches, qualifiersPerGroup == null ? 0 : qualifiersPerGroup,
                h2hWinner, effectiveConfig);
        markDisplayRanks(standings, effectiveMatches, h2hWinner,
                effectiveConfig);
        return standings;
    }

    private List<Player> applyWithdrawPolicy(List<Player> players,
                                             List<MatchRecord> matches,
                                             RankingConfig config) {
        List<Player> source = players == null ? List.of() : players;
        if (config.getWithdrawPolicy() != RankingConfig.WithdrawPolicy.DELETE_ALL) {
            return source;
        }
        Set<String> withdrawnIds = withdrawnParticipantIds(matches);
        if (withdrawnIds.isEmpty()) {
            return source;
        }
        return source.stream()
                .filter(player -> player != null && !withdrawnIds.contains(player.getId()))
                .toList();
    }

    private List<MatchRecord> applyWithdrawPolicy(List<MatchRecord> matches,
                                                  RankingConfig config) {
        List<MatchRecord> source = matches == null ? List.of() : matches;
        if (config.getWithdrawPolicy() == RankingConfig.WithdrawPolicy.DELETE_ALL) {
            Set<String> withdrawnIds = withdrawnParticipantIds(source);
            if (withdrawnIds.isEmpty()) {
                return source;
            }
            return source.stream()
                    .filter(match -> match != null
                            && !withdrawnIds.contains(match.getLeftPlayerId())
                            && !withdrawnIds.contains(match.getRightPlayerId()))
                    .toList();
        }
        if (config.getWithdrawPolicy() == RankingConfig.WithdrawPolicy.FORFEIT_SINGLE) {
            return source.stream().map(this::normalizeForfeitMatch).toList();
        }
        return source;
    }

    public static Set<String> withdrawnParticipantIds(List<MatchRecord> matches) {
        Set<String> ids = new HashSet<>();
        for (MatchRecord match : matches == null ? List.<MatchRecord>of() : matches) {
            if (match == null) {
                continue;
            }
            String retiredSide = match.getRetiredSide();
            if ("left".equals(retiredSide) && !isBlank(match.getLeftPlayerId())) {
                ids.add(match.getLeftPlayerId());
            } else if ("right".equals(retiredSide) && !isBlank(match.getRightPlayerId())) {
                ids.add(match.getRightPlayerId());
            }
        }
        return ids;
    }

    private MatchRecord normalizeForfeitMatch(MatchRecord match) {
        if (match == null || isBlank(match.getRetiredSide()) || !isBlank(match.getGameScores())) {
            return match;
        }
        MatchRecord normalized = new MatchRecord();
        normalized.setId(match.getId());
        normalized.setTournamentId(match.getTournamentId());
        normalized.setRoundNum(match.getRoundNum());
        normalized.setMatchIndex(match.getMatchIndex());
        normalized.setStageType(match.getStageType());
        normalized.setMatchRole(match.getMatchRole());
        normalized.setGroupNo(match.getGroupNo());
        normalized.setLeftPlayerId(match.getLeftPlayerId());
        normalized.setRightPlayerId(match.getRightPlayerId());
        normalized.setScoreDisplay(match.getScoreDisplay());
        normalized.setWinnerId(match.getWinnerId());
        normalized.setLeftGameWins(match.getLeftGameWins());
        normalized.setRightGameWins(match.getRightGameWins());
        normalized.setStatus(match.getStatus());
        normalized.setNextMatchId(match.getNextMatchId());
        normalized.setNextMatchSlot(match.getNextMatchSlot());
        normalized.setLoserNextMatchId(match.getLoserNextMatchId());
        normalized.setLoserNextMatchSlot(match.getLoserNextMatchSlot());
        normalized.setRetiredSide(match.getRetiredSide());

        int leftGames = safeInt(match.getLeftGameWins());
        int rightGames = safeInt(match.getRightGameWins());
        int winnerGames = Math.max(leftGames, rightGames);
        if (winnerGames <= 0) {
            winnerGames = 3;
            if ("left".equals(match.getRetiredSide())) {
                normalized.setLeftGameWins(0);
                normalized.setRightGameWins(winnerGames);
            } else {
                normalized.setLeftGameWins(winnerGames);
                normalized.setRightGameWins(0);
            }
        }
        boolean leftRetired = "left".equals(match.getRetiredSide());
        JSONArray scores = new JSONArray();
        for (int i = 0; i < winnerGames; i++) {
            JSONObject score = new JSONObject();
            score.set("leftScore", leftRetired ? 0 : 25);
            score.set("rightScore", leftRetired ? 25 : 0);
            scores.add(score);
        }
        normalized.setGameScores(scores.toString());
        return normalized;
    }

    private List<Standing> orderByPriority(List<Standing> standings,
                                           List<MatchRecord> matches,
                                           RankingConfig config,
                                           Map<String, String> h2hWinner) {
        List<List<Standing>> blocks = new ArrayList<>();
        blocks.add(new ArrayList<>(standings));
        boolean twoWayH2HFirstApplied = false;

        for (RankingConfig.Criterion criterion : config.getPriorities()) {
            if (!twoWayH2HFirstApplied && shouldApplyTwoWayHeadToHeadFirstBefore(config, criterion)) {
                blocks = applyTwoWayHeadToHeadFirst(blocks, h2hWinner);
                twoWayH2HFirstApplied = true;
            }
            if (criterion == RankingConfig.Criterion.HEAD_TO_HEAD
                    || criterion == RankingConfig.Criterion.TWO_WAY_HEAD_TO_HEAD
                    || criterion == RankingConfig.Criterion.MULTI_HEAD_TO_HEAD) {
                List<List<Standing>> nextBlocks = new ArrayList<>();
                for (List<Standing> block : blocks) {
                    if (block.size() < 2) {
                        nextBlocks.add(block);
                        continue;
                    }
                    if (criterion == RankingConfig.Criterion.TWO_WAY_HEAD_TO_HEAD && block.size() != 2) {
                        nextBlocks.add(block);
                    } else if (criterion == RankingConfig.Criterion.MULTI_HEAD_TO_HEAD && block.size() < 3) {
                        nextBlocks.add(block);
                    } else if (block.size() == 2) {
                        nextBlocks.addAll(resolveTwoWayBlock(block, h2hWinner));
                    } else {
                        nextBlocks.addAll(resolveMultiHeadToHead(block, matches, config, h2hWinner));
                    }
                }
                blocks = nextBlocks;
                continue;
            }
            if (criterion == RankingConfig.Criterion.NAME) {
                blocks.forEach(block -> block.sort(Comparator
                        .comparing((Standing standing) -> standing.playerName)
                        .thenComparing(standing -> standing.playerId)));
                continue;
            }

            List<List<Standing>> nextBlocks = new ArrayList<>();
            for (List<Standing> block : blocks) {
                block.sort((left, right) -> compareScalar(right, left, criterion));
                for (int i = 0; i < block.size();) {
                    int j = i + 1;
                    while (j < block.size() && sameCriterionValue(block.get(i), block.get(j), criterion)) {
                        j++;
                    }
                    nextBlocks.add(new ArrayList<>(block.subList(i, j)));
                    i = j;
                }
            }
            blocks = nextBlocks;
        }

        return blocks.stream().flatMap(List::stream).collect(java.util.stream.Collectors.toList());
    }

    private boolean shouldApplyTwoWayHeadToHeadFirstBefore(RankingConfig config,
                                                           RankingConfig.Criterion criterion) {
        return config.isTwoWayTieH2HFirst()
                && criterion != RankingConfig.Criterion.MATCH_WINS
                && criterion != RankingConfig.Criterion.MATCH_WIN_DIFF
                && criterion != RankingConfig.Criterion.MATCH_WIN_RATE
                && criterion != RankingConfig.Criterion.HEAD_TO_HEAD
                && criterion != RankingConfig.Criterion.TWO_WAY_HEAD_TO_HEAD
                && criterion != RankingConfig.Criterion.MULTI_HEAD_TO_HEAD
                && criterion != RankingConfig.Criterion.NAME;
    }

    private List<List<Standing>> applyTwoWayHeadToHeadFirst(List<List<Standing>> blocks,
                                                            Map<String, String> h2hWinner) {
        List<List<Standing>> result = new ArrayList<>();
        for (List<Standing> block : blocks) {
            if (block.size() != 2) {
                result.add(block);
                continue;
            }
            result.addAll(resolveTwoWayBlock(block, h2hWinner));
        }
        return result;
    }

    private List<List<Standing>> resolveTwoWayBlock(List<Standing> block,
                                                    Map<String, String> h2hWinner) {
        Standing first = block.get(0);
        Standing second = block.get(1);
        String winner = h2hWinner.get(pairKey(first.playerId, second.playerId));
        if (first.playerId.equals(winner)) {
            return List.of(markResolved(new ArrayList<>(List.of(first))),
                    markResolved(new ArrayList<>(List.of(second))));
        }
        if (second.playerId.equals(winner)) {
            return List.of(markResolved(new ArrayList<>(List.of(second))),
                    markResolved(new ArrayList<>(List.of(first))));
        }
        return List.of(block);
    }

    private List<List<Standing>> resolveMultiHeadToHead(List<Standing> block,
                                                        List<MatchRecord> matches,
                                                        RankingConfig config,
                                                        Map<String, String> h2hWinner) {
        Set<String> memberIds = new HashSet<>();
        for (Standing standing : block) {
            memberIds.add(standing.playerId);
        }

        Map<String, MiniStanding> miniStandingMap = new LinkedHashMap<>();
        for (Standing standing : block) {
        miniStandingMap.put(standing.playerId, new MiniStanding());
        }
        for (MatchRecord match : matches == null ? List.<MatchRecord>of() : matches) {
            if (!isResultMatch(match)) {
                continue;
            }
            if (!memberIds.contains(match.getLeftPlayerId()) || !memberIds.contains(match.getRightPlayerId())) {
                continue;
            }

            MiniStanding left = miniStandingMap.get(match.getLeftPlayerId());
            MiniStanding right = miniStandingMap.get(match.getRightPlayerId());
            if (left == null || right == null) {
                continue;
            }

            boolean leftWon = match.getWinnerId().equals(match.getLeftPlayerId());
            if (leftWon) {
                left.matchWins++;
                right.matchLosses++;
            } else {
                right.matchWins++;
                left.matchLosses++;
            }
            if (usesTeamItemStats(config)) {
                left.teamItemWins += safeInt(match.getLeftGameWins());
                left.teamItemLosses += safeInt(match.getRightGameWins());
                right.teamItemWins += safeInt(match.getRightGameWins());
                right.teamItemLosses += safeInt(match.getLeftGameWins());
                applyGameAndPointStatsFromScores(match, left, right);
            } else {
                left.gameWins += safeInt(match.getLeftGameWins());
                left.gameLosses += safeInt(match.getRightGameWins());
                right.gameWins += safeInt(match.getRightGameWins());
                right.gameLosses += safeInt(match.getLeftGameWins());
                applyPointStats(match, left, right);
            }
        }
        miniStandingMap.values().forEach(this::calculateRates);

        List<RankingConfig.Criterion> miniCriteria = miniCriteria(config);
        List<List<Standing>> blocks = new ArrayList<>();
        blocks.add(new ArrayList<>(block));
        boolean splitOccurred = false;

        for (RankingConfig.Criterion criterion : miniCriteria) {
            List<List<Standing>> nextBlocks = new ArrayList<>();
            for (List<Standing> current : blocks) {
                current.sort((left, right) -> compareMini(miniStandingMap.get(right.playerId),
                        miniStandingMap.get(left.playerId), criterion));
                for (int i = 0; i < current.size();) {
                    int j = i + 1;
                    while (j < current.size()
                            && sameMiniCriterionValue(miniStandingMap.get(current.get(i).playerId),
                            miniStandingMap.get(current.get(j).playerId), criterion)) {
                        j++;
                    }
                    nextBlocks.add(new ArrayList<>(current.subList(i, j)));
                    i = j;
                }
            }
            if (nextBlocks.size() > 1) {
                splitOccurred = true;
            }
            blocks = nextBlocks;
        }

        if (!splitOccurred) {
            return List.of(block);
        }

        List<List<Standing>> result = new ArrayList<>();
        for (List<Standing> current : blocks) {
            if (current.size() == 1) {
                result.add(markResolved(current));
                continue;
            }
            if (current.size() == 2) {
                result.addAll(resolveTwoWayBlock(current, h2hWinner));
                continue;
            }
            if (current.size() < block.size()) {
                result.addAll(resolveMultiHeadToHead(current, matches, config, h2hWinner));
                continue;
            }
            result.add(current);
        }
        return result;
    }

    private List<RankingConfig.Criterion> miniCriteria(RankingConfig config) {
        if (usesTeamItemStats(config)) {
            return List.of(RankingConfig.Criterion.MATCH_WINS,
                    RankingConfig.Criterion.TEAM_ITEM_NET_WINS,
                    RankingConfig.Criterion.TEAM_CHILD_NET_GAMES,
                    RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE);
        }
        if (config != null && config.getMathType() == RankingConfig.MathType.RATIO) {
            return List.of(RankingConfig.Criterion.MATCH_WINS,
                    RankingConfig.Criterion.GAME_WIN_RATE,
                    RankingConfig.Criterion.POINT_WIN_RATE);
        }
        RankingConfig.Criterion first = config != null && config.contains(RankingConfig.Criterion.MATCH_WIN_DIFF)
                ? RankingConfig.Criterion.MATCH_WIN_DIFF
                : RankingConfig.Criterion.MATCH_WINS;
        return List.of(first,
                RankingConfig.Criterion.NET_GAMES,
                RankingConfig.Criterion.POINT_WIN_RATE);
    }

    private int compareMini(MiniStanding left,
                            MiniStanding right,
                            RankingConfig.Criterion criterion) {
        return switch (criterion) {
            case MATCH_WINS -> Integer.compare(left.matchWins, right.matchWins);
            case MATCH_WIN_DIFF -> Integer.compare(left.matchWinDiff(), right.matchWinDiff());
            case MATCH_WIN_RATE -> left.matchWinRate.compareTo(right.matchWinRate);
            case GAME_WINS -> Integer.compare(left.gameWins, right.gameWins);
            case NET_GAMES -> Integer.compare(left.netGames(), right.netGames());
            case NET_POINTS -> Integer.compare(left.netPoints(), right.netPoints());
            case TEAM_ITEM_WINS -> Integer.compare(left.teamItemWins, right.teamItemWins);
            case TEAM_ITEM_NET_WINS -> Integer.compare(left.teamItemNetWins(), right.teamItemNetWins());
            case TEAM_ITEM_WIN_RATE -> left.teamItemWinRate.compareTo(right.teamItemWinRate);
            case TEAM_CHILD_GAME_WINS -> Integer.compare(left.gameWins, right.gameWins);
            case TEAM_CHILD_NET_GAMES -> Integer.compare(left.netGames(), right.netGames());
            case TEAM_CHILD_GAME_WIN_RATE -> left.gameWinRate.compareTo(right.gameWinRate);
            case TEAM_CHILD_NET_POINTS -> Integer.compare(left.netPoints(), right.netPoints());
            case TEAM_CHILD_POINT_WIN_RATE -> left.pointWinRate.compareTo(right.pointWinRate);
            case GAME_WIN_RATE -> left.gameWinRate.compareTo(right.gameWinRate);
            case POINT_WIN_RATE -> left.pointWinRate.compareTo(right.pointWinRate);
            default -> 0;
        };
    }

    private boolean sameMiniCriterionValue(MiniStanding left,
                                           MiniStanding right,
                                           RankingConfig.Criterion criterion) {
        return compareMini(left, right, criterion) == 0;
    }

    private List<Standing> markResolved(List<Standing> block) {
        block.forEach(standing -> standing.tieBreakerResolved = true);
        return block;
    }

    private int compareScalar(Standing left,
                              Standing right,
                              RankingConfig.Criterion criterion) {
        return switch (criterion) {
            case MATCH_WINS -> Integer.compare(left.matchWins, right.matchWins);
            case MATCH_WIN_DIFF -> Integer.compare(left.matchWinDiff(), right.matchWinDiff());
            case MATCH_WIN_RATE -> left.matchWinRate.compareTo(right.matchWinRate);
            case MATCH_POINTS -> Integer.compare(left.matchPoints, right.matchPoints);
            case GAME_WINS -> Integer.compare(left.gameWins, right.gameWins);
            case NET_GAMES -> Integer.compare(left.netGames(), right.netGames());
            case NET_POINTS -> Integer.compare(left.netPoints(), right.netPoints());
            case TEAM_ITEM_WINS -> Integer.compare(left.teamItemWins, right.teamItemWins);
            case TEAM_ITEM_NET_WINS -> Integer.compare(left.teamItemNetWins(), right.teamItemNetWins());
            case TEAM_ITEM_WIN_RATE -> left.teamItemWinRate.compareTo(right.teamItemWinRate);
            case TEAM_CHILD_GAME_WINS -> Integer.compare(left.gameWins, right.gameWins);
            case TEAM_CHILD_NET_GAMES -> Integer.compare(left.netGames(), right.netGames());
            case TEAM_CHILD_GAME_WIN_RATE -> left.gameWinRate.compareTo(right.gameWinRate);
            case TEAM_CHILD_NET_POINTS -> Integer.compare(left.netPoints(), right.netPoints());
            case TEAM_CHILD_POINT_WIN_RATE -> left.pointWinRate.compareTo(right.pointWinRate);
            case GAME_WIN_RATE -> left.gameWinRate.compareTo(right.gameWinRate);
            case POINT_WIN_RATE -> left.pointWinRate.compareTo(right.pointWinRate);
            default -> 0;
        };
    }

    private boolean sameCriterionValue(Standing left,
                                       Standing right,
                                       RankingConfig.Criterion criterion) {
        return compareScalar(left, right, criterion) == 0;
    }

    private boolean sameDisplayStats(Standing left,
                                     Standing right,
                                     RankingConfig config) {
        for (RankingConfig.Criterion criterion : config.getPriorities()) {
            if (criterion == RankingConfig.Criterion.NAME
                    || criterion == RankingConfig.Criterion.HEAD_TO_HEAD
                    || criterion == RankingConfig.Criterion.TWO_WAY_HEAD_TO_HEAD
                    || criterion == RankingConfig.Criterion.MULTI_HEAD_TO_HEAD) {
                continue;
            }
            if (!sameCriterionValue(left, right, criterion)) {
                return false;
            }
        }
        return true;
    }

    private void markRanksAndTies(List<Standing> standings,
                                  List<MatchRecord> matches,
                                  int qualifiersPerGroup,
                                  Map<String, String> h2hWinner,
                                  RankingConfig config) {
        for (int i = 0; i < standings.size(); i++) {
            Standing standing = standings.get(i);
            standing.rank = i + 1;
            standing.qualified = false;
            standing.tieUnresolved = false;
        }

        if (qualifiersPerGroup <= 0 || finishedMatchCount(matches) == 0) {
            return;
        }

        for (int i = 0; i < standings.size();) {
            Standing current = standings.get(i);
            int j = i + 1;
            while (j < standings.size() && sameDisplayStats(current, standings.get(j), config)) {
                j++;
            }

            List<Standing> block = standings.subList(i, j);
            boolean unresolvedTieBlock = block.size() > 1
                    && !allTieBreakerResolved(block)
                    && !canResolveDisplayTie(block, h2hWinner, config.contains(RankingConfig.Criterion.HEAD_TO_HEAD));
            int startRank = i + 1;
            int endRank = j;
            boolean crossesQualificationLine = startRank <= qualifiersPerGroup && endRank > qualifiersPerGroup;

            if (!unresolvedTieBlock) {
                for (int k = i; k < j; k++) {
                    standings.get(k).qualified = k < qualifiersPerGroup;
                }
            } else if (crossesQualificationLine) {
                block.forEach(standing -> standing.tieUnresolved = true);
            } else if (endRank <= qualifiersPerGroup) {
                block.forEach(standing -> standing.qualified = true);
            }
            i = j;
        }
    }

    private void markDisplayRanks(List<Standing> standings,
                                  List<MatchRecord> matches,
                                  Map<String, String> h2hWinner,
                                  RankingConfig config) {
        if (finishedMatchCount(matches) == 0) {
            standings.forEach(standing -> standing.displayRankText = "-");
            return;
        }

        for (int i = 0; i < standings.size();) {
            List<Standing> tied = new ArrayList<>();
            Standing current = standings.get(i);
            tied.add(current);
            int j = i + 1;
            while (j < standings.size() && sameDisplayStats(current, standings.get(j), config)) {
                tied.add(standings.get(j));
                j++;
            }

            boolean displayTie = tied.size() > 1
                    && !allTieBreakerResolved(tied)
                    && !canResolveDisplayTie(tied, h2hWinner, config.contains(RankingConfig.Criterion.HEAD_TO_HEAD));
            String displayRankText = String.valueOf(i + 1);
            if (displayTie) {
                tied.forEach(standing -> standing.displayRankText = displayRankText);
            } else {
                for (int k = 0; k < tied.size(); k++) {
                    tied.get(k).displayRankText = String.valueOf(i + 1 + k);
                }
            }
            i = j;
        }
    }

    private boolean canResolveDisplayTie(List<Standing> tied,
                                         Map<String, String> h2hWinner,
                                         boolean headToHeadEnabled) {
        if (!headToHeadEnabled || tied.size() != 2) {
            return false;
        }
        String winner = h2hWinner.get(pairKey(tied.get(0).playerId, tied.get(1).playerId));
        return tied.get(0).playerId.equals(winner) || tied.get(1).playerId.equals(winner);
    }

    private boolean allTieBreakerResolved(List<Standing> standings) {
        return !standings.isEmpty() && standings.stream().allMatch(Standing::isTieBreakerResolved);
    }

    private long finishedMatchCount(List<MatchRecord> matches) {
        if (matches == null) {
            return 0;
        }
        return matches.stream()
                .filter(match -> Integer.valueOf(2).equals(match.getStatus())
                        || Integer.valueOf(3).equals(match.getStatus()))
                .count();
    }

    private boolean isResultMatch(MatchRecord match) {
        return match != null
                && !isBlank(match.getWinnerId())
                && (Integer.valueOf(2).equals(match.getStatus())
                || Integer.valueOf(3).equals(match.getStatus()));
    }

    private void addHeadToHeadBalance(Map<String, Integer> h2hBalance,
                                      String leftId,
                                      String rightId,
                                      String winnerId) {
        String key = pairKey(leftId, rightId);
        if (isBlank(key) || isBlank(winnerId)) {
            return;
        }
        String firstId = key.split(":", 2)[0];
        int delta = winnerId.equals(firstId) ? 1 : -1;
        h2hBalance.merge(key, delta, Integer::sum);
    }

    private Map<String, String> resolveHeadToHeadWinners(Map<String, Integer> h2hBalance) {
        Map<String, String> winners = new HashMap<>();
        for (Map.Entry<String, Integer> entry : h2hBalance.entrySet()) {
            int balance = entry.getValue() == null ? 0 : entry.getValue();
            if (balance == 0) {
                continue;
            }
            String[] ids = entry.getKey().split(":", 2);
            winners.put(entry.getKey(), balance > 0 ? ids[0] : ids[1]);
        }
        return winners;
    }

    private void applyPointStats(MatchRecord match, Standing left, Standing right) {
        if (isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private void applyGameAndPointStatsFromScores(MatchRecord match, Standing left, Standing right) {
        if (isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            if (leftScore > rightScore) {
                left.gameWins++;
                right.gameLosses++;
            } else if (rightScore > leftScore) {
                right.gameWins++;
                left.gameLosses++;
            }
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private void applyGameAndPointStatsFromScores(MatchRecord match, MiniStanding left, MiniStanding right) {
        if (isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            if (leftScore > rightScore) {
                left.gameWins++;
                right.gameLosses++;
            } else if (rightScore > leftScore) {
                right.gameWins++;
                left.gameLosses++;
            }
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private void applyPointStats(MatchRecord match, MiniStanding left, MiniStanding right) {
        if (isBlank(match.getGameScores())) {
            return;
        }
        JSONArray scores = JSONUtil.parseArray(match.getGameScores());
        for (Object item : scores) {
            if (!(item instanceof JSONObject score)) {
                continue;
            }
            int leftScore = safeInt(score.getInt("leftScore"));
            int rightScore = safeInt(score.getInt("rightScore"));
            left.pointsFor += leftScore;
            left.pointsAgainst += rightScore;
            right.pointsFor += rightScore;
            right.pointsAgainst += leftScore;
        }
    }

    private void applyMatchPoints(MatchRecord match,
                                  Standing left,
                                  Standing right,
                                  RankingConfig.PointsSystem pointsSystem) {
        if (pointsSystem == null || !pointsSystem.enabled()) {
            return;
        }
        int leftGames = safeInt(match.getLeftGameWins());
        int rightGames = safeInt(match.getRightGameWins());
        boolean leftWon = match.getWinnerId().equals(match.getLeftPlayerId());
        int loserGames = leftWon ? rightGames : leftGames;
        int winnerPoints = loserGames >= 2
                ? pointsSystem.fullSetWinPoints()
                : pointsSystem.straightWinPoints();
        int loserPoints = loserGames >= 2 ? pointsSystem.fullSetLossPoints() : 0;
        if (leftWon) {
            left.matchPoints += winnerPoints;
            right.matchPoints += loserPoints;
        } else {
            right.matchPoints += winnerPoints;
            left.matchPoints += loserPoints;
        }
    }

    private void calculateRates(Standing standing) {
        standing.matchWinRate = rate(standing.matchWins, standing.matchLosses);
        standing.teamItemWinRate = rate(standing.teamItemWins, standing.teamItemLosses);
        standing.gameWinRate = rate(standing.gameWins, standing.gameLosses);
        standing.pointWinRate = rate(standing.pointsFor, standing.pointsAgainst);
    }

    private void calculateRates(MiniStanding standing) {
        standing.matchWinRate = rate(standing.matchWins, standing.matchLosses);
        standing.teamItemWinRate = rate(standing.teamItemWins, standing.teamItemLosses);
        standing.gameWinRate = rate(standing.gameWins, standing.gameLosses);
        standing.pointWinRate = rate(standing.pointsFor, standing.pointsAgainst);
    }

    private BigDecimal rate(int numerator, int denominator) {
        if (denominator == 0) {
            return numerator > 0 ? INFINITE_RATE : ZERO_RATE;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private String pairKey(String leftId, String rightId) {
        if (leftId == null || rightId == null) {
            return "";
        }
        return leftId.compareTo(rightId) < 0 ? leftId + ":" + rightId : rightId + ":" + leftId;
    }

    private int safeInt(Integer value) {
        return value == null ? 0 : value;
    }

    private boolean usesTeamItemStats(RankingConfig config) {
        return config != null && (config.contains(RankingConfig.Criterion.TEAM_ITEM_NET_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_ITEM_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_ITEM_WIN_RATE)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WINS)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_NET_GAMES)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_GAME_WIN_RATE)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_NET_POINTS)
                || config.contains(RankingConfig.Criterion.TEAM_CHILD_POINT_WIN_RATE));
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public static class Standing {
        private String playerId;
        private String playerName;
        private Integer seedRank;
        private int rank;
        private String displayRankText;
        private boolean qualified;
        private boolean tieUnresolved;
        private int matchWins;
        private int matchLosses;
        private int matchPoints;
        private int teamItemWins;
        private int teamItemLosses;
        private int gameWins;
        private int gameLosses;
        private int pointsFor;
        private int pointsAgainst;
        private BigDecimal matchWinRate = ZERO_RATE;
        private BigDecimal teamItemWinRate = ZERO_RATE;
        private BigDecimal gameWinRate = ZERO_RATE;
        private BigDecimal pointWinRate = ZERO_RATE;
        private boolean tieBreakerResolved;

        public String getPlayerId() { return playerId; }
        public String getPlayerName() { return playerName; }
        public Integer getSeedRank() { return seedRank; }
        public int getRank() { return rank; }
        public String getDisplayRankText() { return displayRankText; }
        public boolean isQualified() { return qualified; }
        public boolean isTieUnresolved() { return tieUnresolved; }
        public int getMatchWins() { return matchWins; }
        public int getMatchLosses() { return matchLosses; }
        public BigDecimal getMatchWinRate() { return matchWinRate; }
        public int getMatchPoints() { return matchPoints; }
        public int getTeamItemWins() { return teamItemWins; }
        public int getTeamItemLosses() { return teamItemLosses; }
        public int getTeamItemNetWins() { return teamItemNetWins(); }
        public BigDecimal getTeamItemWinRate() { return teamItemWinRate; }
        public int getGameWins() { return gameWins; }
        public int getGameLosses() { return gameLosses; }
        public int getNetGames() { return netGames(); }
        public int getPointsFor() { return pointsFor; }
        public int getPointsAgainst() { return pointsAgainst; }
        public int getNetPoints() { return netPoints(); }
        public BigDecimal getGameWinRate() { return gameWinRate; }
        public BigDecimal getPointWinRate() { return pointWinRate; }
        public boolean isTieBreakerResolved() { return tieBreakerResolved; }

        private int netGames() { return gameWins - gameLosses; }
        private int netPoints() { return pointsFor - pointsAgainst; }
        private int matchWinDiff() { return matchWins - matchLosses; }
        private int teamItemNetWins() { return teamItemWins - teamItemLosses; }
    }

    private static class MiniStanding {
        private int matchWins;
        private int matchLosses;
        private int teamItemWins;
        private int teamItemLosses;
        private int gameWins;
        private int gameLosses;
        private int pointsFor;
        private int pointsAgainst;
        private BigDecimal matchWinRate = ZERO_RATE;
        private BigDecimal teamItemWinRate = ZERO_RATE;
        private BigDecimal gameWinRate = ZERO_RATE;
        private BigDecimal pointWinRate = ZERO_RATE;

        private int matchWinDiff() { return matchWins - matchLosses; }
        private int teamItemNetWins() { return teamItemWins - teamItemLosses; }
        private int netGames() { return gameWins - gameLosses; }
        private int netPoints() { return pointsFor - pointsAgainst; }
    }
}
