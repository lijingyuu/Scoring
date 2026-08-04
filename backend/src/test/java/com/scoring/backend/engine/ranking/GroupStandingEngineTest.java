package com.scoring.backend.engine.ranking;

import com.scoring.backend.domain.entity.MatchRecord;
import com.scoring.backend.domain.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupStandingEngineTest {

    private final GroupStandingEngine engine = new GroupStandingEngine();

    @Test
    void legacyDefault_shouldRankByWinsNetGamesNetPointsHeadToHeadAndName() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        String normalScores = "[{\"leftScore\":21,\"rightScore\":10},{\"leftScore\":21,\"rightScore\":11}]";
        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 2, 0, normalScores);
        MatchRecord charlieBeatsBravo = finished("c", "b", "c", 2, 0, normalScores);
        MatchRecord alphaBeatsCharlie = finished("a", "c", "a", 2, 0, normalScores);

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsBravo, charlieBeatsBravo, alphaBeatsCharlie),
                2,
                RankingConfig.legacyDefault()
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "c", "b");
        assertThat(standings.get(0).getMatchWins()).isEqualTo(2);
        assertThat(standings.get(0).isQualified()).isTrue();
        assertThat(standings.get(1).isQualified()).isTrue();
        assertThat(standings.get(2).isQualified()).isFalse();
    }

    @Test
    void customPriority_shouldUseConfiguredScalarOrder() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord alphaBeatsCharlie = finished("a", "c", "a", 2, 0,
                "[{\"leftScore\":21,\"rightScore\":0},{\"leftScore\":21,\"rightScore\":0}]");
        MatchRecord bravoBeatsAlpha = finished("b", "a", "b", 2, 0,
                "[{\"leftScore\":21,\"rightScore\":20},{\"leftScore\":21,\"rightScore\":20}]");
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 2, 0,
                "[{\"leftScore\":21,\"rightScore\":20},{\"leftScore\":21,\"rightScore\":20}]");

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsCharlie, bravoBeatsAlpha, bravoBeatsCharlie),
                0,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.NET_POINTS,
                        RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.NAME
                ))
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "b", "c");
    }

    @Test
    void matchWinDiff_shouldRankByWinsMinusLosses() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);
        Player delta = player("d", "Delta", 4);

        MatchRecord alphaBeatsCharlie = finished("a", "c", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord deltaBeatsBravo = finished("d", "b", "d", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(alphaBeatsCharlie, bravoBeatsCharlie, deltaBeatsBravo),
                0,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.MATCH_WIN_DIFF,
                        RankingConfig.Criterion.NAME
                ))
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "d", "b", "c");
    }

    @Test
    void teamCommonRanking_shouldUseTeamItemGamesBeforeChildGamesAndPoints() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord alphaBeatsCharlieThreeTwo = finished("a", "c", "a", 3, 2,
                scores(new int[][]{{21, 10}, {21, 10}, {10, 21}, {10, 21}, {21, 10}, {21, 10}, {10, 21}, {10, 21}, {21, 10}, {10, 21}, {21, 10}}));
        MatchRecord bravoBeatsCharlieFiveZero = finished("b", "c", "b", 5, 0,
                scores(new int[][]{{21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}, {21, 19}}));

        RankingConfig config = new RankingConfig(
                RankingConfig.Template.CUSTOM,
                List.of(RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.TEAM_ITEM_NET_WINS,
                        RankingConfig.Criterion.TEAM_CHILD_NET_GAMES,
                        RankingConfig.Criterion.TEAM_CHILD_NET_POINTS,
                        RankingConfig.Criterion.NAME),
                RankingConfig.MathType.DIFFERENCE,
                false,
                RankingConfig.WithdrawPolicy.NONE,
                RankingConfig.PointsSystem.disabled()
        );

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsCharlieThreeTwo, bravoBeatsCharlieFiveZero),
                0,
                config
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("b", "a", "c");
        assertThat(standings.get(0).getTeamItemNetWins()).isEqualTo(5);
        assertThat(standings.get(1).getTeamItemNetWins()).isEqualTo(1);
        assertThat(standings.get(0).getNetGames()).isEqualTo(10);
    }

    @Test
    void multiHeadToHead_shouldResolveThreeWayMiniTableAndSplitOutTopThree() {
        Player alpha = player("a", "Zulu", 1);
        Player bravo = player("b", "Yankee", 2);
        Player charlie = player("c", "Xray", 3);
        Player delta = player("d", "Whiskey", 4);

        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 2, 0,
                scores(new int[][]{{21, 11}, {21, 11}}));
        MatchRecord charlieBeatsAlpha = finished("c", "a", "c", 2, 0,
                scores(new int[][]{{21, 12}, {21, 12}}));
        MatchRecord alphaBeatsDelta = finished("a", "d", "a", 2, 0,
                scores(new int[][]{{21, 19}, {21, 19}}));
        MatchRecord bravoBeatsDelta = finished("b", "d", "b", 2, 0,
                scores(new int[][]{{21, 9}, {21, 9}}));
        MatchRecord charlieBeatsDelta = finished("c", "d", "c", 2, 0,
                scores(new int[][]{{21, 2}, {21, 2}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(alphaBeatsBravo, bravoBeatsCharlie, charlieBeatsAlpha,
                        alphaBeatsDelta, bravoBeatsDelta, charlieBeatsDelta),
                2,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.HEAD_TO_HEAD,
                        RankingConfig.Criterion.NAME
                ))
        );

        // Mini-table point-win-rate: a(66/62=1.0645) > c(64/66=0.9697) > b(62/64=0.9688)
        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "c", "b", "d");
        assertThat(standings.get(0).isTieBreakerResolved()).isTrue();
        assertThat(standings.get(1).isTieBreakerResolved()).isTrue();
        assertThat(standings.get(2).isTieBreakerResolved()).isTrue();
        assertThat(standings).extracting(GroupStandingEngine.Standing::getDisplayRankText)
                .containsExactly("1", "2", "3", "4");
        assertThat(standings.get(0).isQualified()).isTrue();
        assertThat(standings.get(1).isQualified()).isTrue();
        assertThat(standings.get(2).isQualified()).isFalse();
        assertThat(standings.get(0).isTieUnresolved()).isFalse();
    }

    @Test
    void multiHeadToHead_shouldFallbackToTwoWayH2HAfterMiniTableSplit() {
        Player alpha = player("a", "Zulu", 1);
        Player bravo = player("b", "Yankee", 2);
        Player charlie = player("c", "Xray", 3);
        Player delta = player("d", "Whiskey", 4);

        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 2, 0,
                scores(new int[][]{{21, 11}, {21, 11}}));
        MatchRecord charlieBeatsAlpha = finished("c", "a", "c", 2, 0,
                scores(new int[][]{{21, 19}, {21, 19}}));
        MatchRecord alphaBeatsDelta = finished("a", "d", "a", 2, 0,
                scores(new int[][]{{21, 19}, {21, 19}}));
        MatchRecord bravoBeatsDelta = finished("b", "d", "b", 2, 0,
                scores(new int[][]{{21, 16}, {21, 16}}));
        MatchRecord charlieBeatsDelta = finished("c", "d", "c", 2, 0,
                scores(new int[][]{{21, 16}, {21, 16}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(alphaBeatsBravo, bravoBeatsCharlie, charlieBeatsAlpha,
                        alphaBeatsDelta, bravoBeatsDelta, charlieBeatsDelta),
                0,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.HEAD_TO_HEAD,
                        RankingConfig.Criterion.NAME
                ))
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "b", "c", "d");
        assertThat(standings.get(0).isTieBreakerResolved()).isTrue();
        assertThat(standings.get(1).isTieBreakerResolved()).isTrue();
        assertThat(standings.get(2).isTieBreakerResolved()).isTrue();
    }

    @Test
    void multiHeadToHead_shouldKeepDeadlockedThreeWayTieUnresolved() {
        Player alpha = player("a", "Zulu", 1);
        Player bravo = player("b", "Yankee", 2);
        Player charlie = player("c", "Xray", 3);
        Player delta = player("d", "Whiskey", 4);

        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord charlieBeatsAlpha = finished("c", "a", "c", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord alphaBeatsDelta = finished("a", "d", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsDelta = finished("b", "d", "b", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord charlieBeatsDelta = finished("c", "d", "c", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(alphaBeatsBravo, bravoBeatsCharlie, charlieBeatsAlpha,
                        alphaBeatsDelta, bravoBeatsDelta, charlieBeatsDelta),
                2,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.HEAD_TO_HEAD,
                        RankingConfig.Criterion.NAME
                ))
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("c", "b", "a", "d");
        assertThat(standings.get(0).isTieUnresolved()).isTrue();
        assertThat(standings.get(1).isTieUnresolved()).isTrue();
        assertThat(standings.get(2).isTieUnresolved()).isTrue();
    }

    @Test
    void fivbTemplate_shouldCalculateMatchPointsAndRatios() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 3, 0,
                scores(new int[][]{{25, 10}, {25, 10}, {25, 10}}));
        MatchRecord alphaBeatsCharlie = finished("a", "c", "a", 3, 2,
                scores(new int[][]{{25, 20}, {20, 25}, {25, 20}, {20, 25}, {15, 10}}));
        MatchRecord bravoBeatsCharlie = finished("b", "c", "b", 3, 2,
                scores(new int[][]{{25, 20}, {20, 25}, {25, 20}, {20, 25}, {15, 10}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsBravo, alphaBeatsCharlie, bravoBeatsCharlie),
                0,
                RankingConfig.preset(RankingConfig.Template.FIVB_VOLLEYBALL)
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "b", "c");
        assertThat(standings.get(0).getMatchPoints()).isEqualTo(5);
        assertThat(standings.get(1).getMatchPoints()).isEqualTo(2);
        assertThat(standings.get(2).getMatchPoints()).isEqualTo(2);
        assertThat(standings.get(0).getGameWinRate().toPlainString()).isEqualTo("3.0000");
        assertThat(standings.get(2).getPointWinRate().toPlainString()).isEqualTo("0.9524");
    }

    @Test
    void rates_shouldProtectZeroDenominator() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 3, 0,
                scores(new int[][]{{25, 0}, {25, 0}, {25, 0}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsBravo),
                0,
                RankingConfig.preset(RankingConfig.Template.FIVB_VOLLEYBALL)
        );

        assertThat(standings.get(0).getPlayerId()).isEqualTo("a");
        assertThat(standings.get(0).getGameWinRate().toPlainString()).isEqualTo("999999.0000");
        assertThat(standings.get(0).getPointWinRate().toPlainString()).isEqualTo("999999.0000");
        GroupStandingEngine.Standing notPlayed = standings.stream()
                .filter(standing -> standing.getPlayerId().equals("c"))
                .findFirst()
                .orElseThrow();
        assertThat(notPlayed.getGameWinRate().toPlainString()).isEqualTo("0.0000");
        assertThat(notPlayed.getPointWinRate().toPlainString()).isEqualTo("0.0000");
    }

    @Test
    void bwfTemplate_shouldUseTwoWayHeadToHeadBeforeNetGames() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);
        Player delta = player("d", "Delta", 4);

        MatchRecord bravoBeatsAlphaNarrowly = finished("b", "a", "b", 2, 1,
                scores(new int[][]{{21, 19}, {19, 21}, {21, 19}}));
        MatchRecord alphaBeatsCharlieBig = finished("a", "c", "a", 2, 0,
                scores(new int[][]{{21, 1}, {21, 1}}));
        MatchRecord alphaBeatsDeltaBig = finished("a", "d", "a", 2, 0,
                scores(new int[][]{{21, 1}, {21, 1}}));
        MatchRecord bravoBeatsCharlieNarrowly = finished("b", "c", "b", 2, 1,
                scores(new int[][]{{21, 19}, {19, 21}, {21, 19}}));
        MatchRecord deltaBeatsBravoBig = finished("d", "b", "d", 2, 0,
                scores(new int[][]{{1, 21}, {1, 21}}));
        MatchRecord charlieBeatsDelta = finished("c", "d", "c", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(bravoBeatsAlphaNarrowly, alphaBeatsCharlieBig, alphaBeatsDeltaBig,
                        bravoBeatsCharlieNarrowly, deltaBeatsBravoBig, charlieBeatsDelta),
                0,
                RankingConfig.preset(RankingConfig.Template.BWF_BADMINTON)
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("b", "a", "c", "d");
    }

    @Test
    void twoWayHeadToHeadFirst_shouldNotDependOnNetGamesCriterion() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord bravoBeatsAlphaNarrowly = finished("b", "a", "b", 2, 1,
                scores(new int[][]{{21, 19}, {19, 21}, {21, 19}}));
        MatchRecord alphaBeatsCharlieBig = finished("a", "c", "a", 2, 0,
                scores(new int[][]{{21, 1}, {21, 1}}));
        MatchRecord bravoBeatsCharlieNarrowly = finished("b", "c", "b", 2, 1,
                scores(new int[][]{{21, 19}, {19, 21}, {21, 19}}));

        RankingConfig config = new RankingConfig(
                RankingConfig.Template.CUSTOM,
                List.of(RankingConfig.Criterion.MATCH_WINS,
                        RankingConfig.Criterion.NET_POINTS,
                        RankingConfig.Criterion.NAME),
                RankingConfig.MathType.DIFFERENCE,
                true,
                RankingConfig.WithdrawPolicy.NONE,
                RankingConfig.PointsSystem.disabled()
        );

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(bravoBeatsAlphaNarrowly, alphaBeatsCharlieBig, bravoBeatsCharlieNarrowly),
                0,
                config
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("b", "a", "c");
    }

    @Test
    void campusVolleyballTemplate_shouldUseDifferenceRankingWithoutMatchPoints() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);
        Player delta = player("d", "Delta", 4);

        MatchRecord bravoBeatsAlphaNarrowly = finished("b", "a", "b", 2, 1,
                scores(new int[][]{{25, 23}, {23, 25}, {15, 13}}));
        MatchRecord alphaBeatsCharlieBig = finished("a", "c", "a", 2, 0,
                scores(new int[][]{{25, 10}, {25, 10}}));
        MatchRecord alphaBeatsDeltaBig = finished("a", "d", "a", 2, 0,
                scores(new int[][]{{25, 10}, {25, 10}}));
        MatchRecord bravoBeatsCharlieNarrowly = finished("b", "c", "b", 2, 1,
                scores(new int[][]{{25, 23}, {23, 25}, {15, 13}}));
        MatchRecord deltaBeatsBravo = finished("d", "b", "d", 2, 0,
                scores(new int[][]{{25, 20}, {25, 20}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie, delta),
                List.of(bravoBeatsAlphaNarrowly, alphaBeatsCharlieBig, alphaBeatsDeltaBig,
                        bravoBeatsCharlieNarrowly, deltaBeatsBravo),
                0,
                RankingConfig.preset(RankingConfig.Template.CAMPUS_VOLLEYBALL)
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "b", "d", "c");
        assertThat(standings).extracting(GroupStandingEngine.Standing::getMatchPoints)
                .containsExactly(0, 0, 0, 0);
    }

    @Test
    void deleteAllWithdrawPolicy_shouldRemoveWithdrawnPlayerAndTheirHistory() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord charlieBeatsBravo = finished("c", "b", "c", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord alphaBeatsCharlieByWithdraw = retired("a", "c", "a", 2, 0, "right");
        MatchRecord alphaBeatsBravo = finished("a", "b", "a", 2, 0,
                scores(new int[][]{{21, 18}, {21, 18}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(charlieBeatsBravo, alphaBeatsCharlieByWithdraw, alphaBeatsBravo),
                1,
                RankingConfig.preset(RankingConfig.Template.BWF_BADMINTON)
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "b");
        assertThat(standings.get(0).getMatchWins()).isEqualTo(1);
        assertThat(standings.get(1).getMatchLosses()).isEqualTo(1);
    }

    @Test
    void forfeitSingleWithdrawPolicy_shouldKeepHistoryAndUseForfeitPoints() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord charlieBeatsBravo = finished("c", "b", "c", 2, 0,
                scores(new int[][]{{25, 14}, {25, 14}}));
        MatchRecord alphaBeatsCharlieByWithdraw = retired("a", "c", "a", 2, 0, "right");

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(charlieBeatsBravo, alphaBeatsCharlieByWithdraw),
                1,
                RankingConfig.preset(RankingConfig.Template.CAMPUS_VOLLEYBALL)
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("a", "c", "b");
        assertThat(standings.get(0).getGameWins()).isEqualTo(2);
        assertThat(standings.get(0).getPointsFor()).isEqualTo(50);
        assertThat(standings.get(0).getPointsAgainst()).isEqualTo(0);
        assertThat(standings.get(1).getMatchWins()).isEqualTo(1);
    }

    @Test
    void customCriteria_shouldRankByMatchWinRateAndGameWins() {
        Player alpha = player("a", "Alpha", 1);
        Player bravo = player("b", "Bravo", 2);
        Player charlie = player("c", "Charlie", 3);

        MatchRecord alphaBeatsCharlie = finished("a", "c", "a", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));
        MatchRecord bravoBeatsAlpha = finished("b", "a", "b", 2, 0,
                scores(new int[][]{{21, 10}, {21, 10}}));

        List<GroupStandingEngine.Standing> standings = engine.rank(
                List.of(alpha, bravo, charlie),
                List.of(alphaBeatsCharlie, bravoBeatsAlpha),
                0,
                new RankingConfig(List.of(
                        RankingConfig.Criterion.MATCH_WIN_RATE,
                        RankingConfig.Criterion.GAME_WINS,
                        RankingConfig.Criterion.NAME
                ))
        );

        assertThat(standings).extracting(GroupStandingEngine.Standing::getPlayerId)
                .containsExactly("b", "a", "c");
        assertThat(standings.get(0).getMatchWinRate().toPlainString()).isEqualTo("999999.0000");
        assertThat(standings.get(1).getGameWins()).isEqualTo(2);
    }

    private Player player(String id, String name, int seed) {
        Player player = new Player();
        player.setId(id);
        player.setName(name);
        player.setSeedRank(seed);
        return player;
    }

    private MatchRecord finished(String leftId,
                                 String rightId,
                                 String winnerId,
                                 int leftGames,
                                 int rightGames,
                                 String scores) {
        MatchRecord match = new MatchRecord();
        match.setLeftPlayerId(leftId);
        match.setRightPlayerId(rightId);
        match.setWinnerId(winnerId);
        match.setLeftGameWins(leftGames);
        match.setRightGameWins(rightGames);
        match.setGameScores(scores);
        match.setStatus(2);
        return match;
    }

    private MatchRecord retired(String leftId,
                                String rightId,
                                String winnerId,
                                int leftGames,
                                int rightGames,
                                String retiredSide) {
        MatchRecord match = finished(leftId, rightId, winnerId, leftGames, rightGames, null);
        match.setRetiredSide(retiredSide);
        return match;
    }

    private String scores(int[][] games) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < games.length; i++) {
            if (i > 0) {
                builder.append(",");
            }
            builder.append("{\"leftScore\":")
                    .append(games[i][0])
                    .append(",\"rightScore\":")
                    .append(games[i][1])
                    .append("}");
        }
        return builder.append("]").toString();
    }
}
