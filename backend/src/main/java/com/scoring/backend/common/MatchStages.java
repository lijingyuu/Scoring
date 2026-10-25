package com.scoring.backend.common;

/**
 * match_record.stage_type 阶段类型常量（单一事实源）。
 *
 * 历史上各服务类各自手抄同值常量（TournamentRuleResolver / MatchSettlementService /
 * TeamMatchServiceImpl / MatchServiceImpl），新增使用方一律引用本类，
 * 既有手抄版本随触碰逐步迁移。
 */
public final class MatchStages {

    /** 小组赛阶段 */
    public static final int STAGE_GROUP = 0;

    /** 淘汰赛阶段 */
    public static final int STAGE_KNOCKOUT = 1;

    /** 羽毛球团体赛子场（由 team_match_item 派生，child_match_id 指向本场） */
    public static final int STAGE_TEAM_CHILD = 2;

    private MatchStages() {
    }
}
