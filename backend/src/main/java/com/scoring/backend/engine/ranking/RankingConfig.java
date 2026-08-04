package com.scoring.backend.engine.ranking;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;

import java.util.ArrayList;
import java.util.List;

public class RankingConfig {

    public enum Criterion {
        MATCH_WINS,
        MATCH_WIN_DIFF,
        MATCH_WIN_RATE,
        MATCH_POINTS,
        GAME_WINS,
        NET_GAMES,
        NET_POINTS,
        TWO_WAY_HEAD_TO_HEAD,
        MULTI_HEAD_TO_HEAD,
        TEAM_ITEM_WINS,
        TEAM_ITEM_NET_WINS,
        TEAM_ITEM_WIN_RATE,
        TEAM_CHILD_GAME_WINS,
        TEAM_CHILD_NET_GAMES,
        TEAM_CHILD_GAME_WIN_RATE,
        TEAM_CHILD_NET_POINTS,
        TEAM_CHILD_POINT_WIN_RATE,
        GAME_WIN_RATE,
        POINT_WIN_RATE,
        HEAD_TO_HEAD
    }

    public enum Template {
        CUSTOM,
        BWF_BADMINTON,
        BADMINTON_COMMON_1,
        BADMINTON_TEAM_COMMON_1,
        CAMPUS_VOLLEYBALL,
        VOLLEYBALL_COMMON_1,
        FIVB_VOLLEYBALL
    }

    public enum MathType {
        DIFFERENCE,
        RATIO
    }

    public enum WithdrawPolicy {
        NONE,
        DELETE_ALL,
        FORFEIT_SINGLE
    }

    private final List<Criterion> priorities;
    private final Template template;
    private final MathType mathType;
    private final boolean twoWayTieH2HFirst;
    private final WithdrawPolicy withdrawPolicy;
    private final PointsSystem pointsSystem;
    private final Criterion systemFallbackCriterion;

    public RankingConfig(List<Criterion> priorities) {
        this(Template.CUSTOM, priorities, MathType.DIFFERENCE, false, WithdrawPolicy.NONE, PointsSystem.disabled());
    }

    public RankingConfig(Template template,
                         List<Criterion> priorities,
                         MathType mathType,
                         boolean twoWayTieH2HFirst,
                         WithdrawPolicy withdrawPolicy,
                         PointsSystem pointsSystem) {
        this(template, priorities, mathType, twoWayTieH2HFirst, withdrawPolicy, pointsSystem, null);
    }

    public RankingConfig(Template template,
                         List<Criterion> priorities,
                         MathType mathType,
                         boolean twoWayTieH2HFirst,
                         WithdrawPolicy withdrawPolicy,
                         PointsSystem pointsSystem,
                         Criterion systemFallbackCriterion) {
        this.template = template == null ? Template.CUSTOM : template;
        this.priorities = priorities == null || priorities.isEmpty()
                ? legacyDefault().getPriorities()
                : List.copyOf(priorities);
        this.mathType = mathType == null ? MathType.DIFFERENCE : mathType;
        this.twoWayTieH2HFirst = twoWayTieH2HFirst;
        this.withdrawPolicy = withdrawPolicy == null ? WithdrawPolicy.NONE : withdrawPolicy;
        this.pointsSystem = pointsSystem == null ? PointsSystem.disabled() : pointsSystem;
        this.systemFallbackCriterion = systemFallbackCriterion;
    }

    public static RankingConfig legacyDefault() {
        return new RankingConfig(
                Template.CUSTOM,
                List.of(
                Criterion.MATCH_WINS,
                Criterion.NET_GAMES,
                Criterion.NET_POINTS,
                Criterion.HEAD_TO_HEAD
                ),
                MathType.DIFFERENCE,
                false,
                WithdrawPolicy.NONE,
                PointsSystem.disabled()
        );
    }

    public static RankingConfig preset(Template template) {
        return switch (template) {
            case BWF_BADMINTON -> new RankingConfig(
                    Template.BWF_BADMINTON,
                    List.of(Criterion.MATCH_WINS, Criterion.NET_GAMES, Criterion.NET_POINTS, Criterion.HEAD_TO_HEAD),
                    MathType.DIFFERENCE,
                    true,
                    WithdrawPolicy.DELETE_ALL,
                    PointsSystem.disabled()
            );
            case BADMINTON_COMMON_1 -> new RankingConfig(
                    Template.BADMINTON_COMMON_1,
                    List.of(Criterion.MATCH_WINS, Criterion.NET_GAMES, Criterion.NET_POINTS, Criterion.HEAD_TO_HEAD),
                    MathType.DIFFERENCE,
                    false,
                    WithdrawPolicy.DELETE_ALL,
                    PointsSystem.disabled()
            );
            case BADMINTON_TEAM_COMMON_1 -> new RankingConfig(
                    Template.BADMINTON_TEAM_COMMON_1,
                    List.of(Criterion.MATCH_WINS, Criterion.HEAD_TO_HEAD, Criterion.TEAM_ITEM_NET_WINS,
                            Criterion.TEAM_CHILD_NET_GAMES, Criterion.TEAM_CHILD_NET_POINTS),
                    MathType.DIFFERENCE,
                    false,
                    WithdrawPolicy.DELETE_ALL,
                    PointsSystem.disabled()
            );
            case CAMPUS_VOLLEYBALL -> new RankingConfig(
                    Template.CAMPUS_VOLLEYBALL,
                    List.of(Criterion.MATCH_WINS, Criterion.NET_GAMES, Criterion.NET_POINTS, Criterion.HEAD_TO_HEAD),
                    MathType.DIFFERENCE,
                    false,
                    WithdrawPolicy.FORFEIT_SINGLE,
                    PointsSystem.disabled()
            );
            case VOLLEYBALL_COMMON_1 -> new RankingConfig(
                    Template.VOLLEYBALL_COMMON_1,
                    List.of(Criterion.MATCH_WINS, Criterion.NET_GAMES, Criterion.NET_POINTS, Criterion.HEAD_TO_HEAD),
                    MathType.DIFFERENCE,
                    false,
                    WithdrawPolicy.FORFEIT_SINGLE,
                    PointsSystem.disabled()
            );
            case FIVB_VOLLEYBALL -> new RankingConfig(
                    Template.FIVB_VOLLEYBALL,
                    List.of(Criterion.MATCH_WINS, Criterion.MATCH_POINTS, Criterion.GAME_WIN_RATE,
                            Criterion.POINT_WIN_RATE, Criterion.HEAD_TO_HEAD),
                    MathType.RATIO,
                    false,
                    WithdrawPolicy.FORFEIT_SINGLE,
                    PointsSystem.fivb()
            );
            case CUSTOM -> legacyDefault();
        };
    }

    public List<Criterion> getPriorities() {
        return new ArrayList<>(priorities);
    }

    public Template getTemplate() {
        return template;
    }

    public MathType getMathType() {
        return mathType;
    }

    public boolean isTwoWayTieH2HFirst() {
        return twoWayTieH2HFirst;
    }

    public WithdrawPolicy getWithdrawPolicy() {
        return withdrawPolicy;
    }

    public PointsSystem getPointsSystem() {
        return pointsSystem;
    }

    public Criterion getSystemFallbackCriterion() {
        return systemFallbackCriterion;
    }

    public boolean contains(Criterion criterion) {
        return priorities.contains(criterion);
    }

    public String toJson() {
        JSONObject json = new JSONObject();
        json.set("template", template.name());
        json.set("priorities", priorities.stream().map(Enum::name).toList());
        json.set("mathType", mathType.name());
        json.set("twoWayTieH2HFirst", twoWayTieH2HFirst);
        json.set("withdrawPolicy", withdrawPolicy.name());
        if (systemFallbackCriterion != null) {
            json.set("systemFallbackCriterion", systemFallbackCriterion.name());
        }
        JSONObject points = new JSONObject();
        points.set("enabled", pointsSystem.enabled());
        points.set("straightWinPoints", pointsSystem.straightWinPoints());
        points.set("fullSetWinPoints", pointsSystem.fullSetWinPoints());
        points.set("fullSetLossPoints", pointsSystem.fullSetLossPoints());
        json.set("pointsSystem", points);
        return json.toString();
    }

    public static RankingConfig fromJson(String json) {
        if (json == null || json.isBlank()) {
            return legacyDefault();
        }
        Object parsed = JSONUtil.parse(json);
        if (parsed instanceof JSONArray array) {
            return new RankingConfig(Template.CUSTOM, ensureCustomFallback(parseCriteria(array), Template.CUSTOM),
                    MathType.DIFFERENCE, false, WithdrawPolicy.NONE, PointsSystem.disabled());
        }
        JSONObject object = JSONUtil.parseObj(json);
        Template template = parseEnum(Template.class, object.getStr("template"), Template.CUSTOM);
        JSONArray prioritiesArray = object.getJSONArray("priorities");
        List<Criterion> criteria = prioritiesArray == null ? legacyDefault().getPriorities() : parseCriteria(prioritiesArray);
        criteria = ensureCustomFallback(criteria, template);
        Criterion systemFallbackCriterion = parseEnum(Criterion.class,
                object.getStr("systemFallbackCriterion"), null);
        JSONObject pointsObject = object.getJSONObject("pointsSystem");
        PointsSystem pointsSystem = pointsObject == null
                ? PointsSystem.disabled()
                : new PointsSystem(
                pointsObject.getBool("enabled", false),
                pointsObject.getInt("straightWinPoints", 3),
                pointsObject.getInt("fullSetWinPoints", 2),
                pointsObject.getInt("fullSetLossPoints", 1)
        );
        return new RankingConfig(
                template,
                criteria,
                parseEnum(MathType.class, object.getStr("mathType"), MathType.DIFFERENCE),
                object.getBool("twoWayTieH2HFirst", false),
                parseEnum(WithdrawPolicy.class, object.getStr("withdrawPolicy"), WithdrawPolicy.NONE),
                pointsSystem,
                systemFallbackCriterion
        );
    }

    private static List<Criterion> ensureCustomFallback(List<Criterion> criteria, Template template) {
        if (template != Template.CUSTOM) {
            return criteria;
        }
        List<Criterion> result = new ArrayList<>(criteria);
        boolean teamRanking = result.stream().anyMatch(RankingConfig::isTeamCriterion);
        boolean hasPointResolutionCriterion = teamRanking
                ? result.contains(Criterion.TEAM_CHILD_NET_POINTS)
                || result.contains(Criterion.TEAM_CHILD_POINT_WIN_RATE)
                : result.contains(Criterion.NET_POINTS)
                || result.contains(Criterion.POINT_WIN_RATE);
        Criterion fallback = teamRanking ? Criterion.TEAM_CHILD_POINT_WIN_RATE : Criterion.POINT_WIN_RATE;
        if (!hasPointResolutionCriterion) {
            result.add(fallback);
        }
        return result;
    }

    private static boolean isTeamCriterion(Criterion criterion) {
        return criterion == Criterion.TEAM_ITEM_WINS
                || criterion == Criterion.TEAM_ITEM_NET_WINS
                || criterion == Criterion.TEAM_ITEM_WIN_RATE
                || criterion == Criterion.TEAM_CHILD_GAME_WINS
                || criterion == Criterion.TEAM_CHILD_NET_GAMES
                || criterion == Criterion.TEAM_CHILD_GAME_WIN_RATE
                || criterion == Criterion.TEAM_CHILD_NET_POINTS
                || criterion == Criterion.TEAM_CHILD_POINT_WIN_RATE;
    }

    private static List<Criterion> parseCriteria(JSONArray array) {
        List<Criterion> criteria = new ArrayList<>();
        for (Object value : array) {
            String name = String.valueOf(value);
            if ("NAME".equals(name)) {
                continue;
            }
            criteria.add(Criterion.valueOf(name));
        }
        return criteria;
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return Enum.valueOf(type, value);
    }

    public record PointsSystem(boolean enabled,
                               int straightWinPoints,
                               int fullSetWinPoints,
                               int fullSetLossPoints) {
        public static PointsSystem disabled() {
            return new PointsSystem(false, 3, 2, 1);
        }

        public static PointsSystem fivb() {
            return new PointsSystem(true, 3, 2, 1);
        }
    }
}
