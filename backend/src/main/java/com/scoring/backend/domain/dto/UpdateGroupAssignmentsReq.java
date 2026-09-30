package com.scoring.backend.domain.dto;

import java.util.List;

/**
 * 手写分组编辑（type1 小组赛+淘汰赛）：全量替换分组结果。
 *
 * groups 外层长度 = knockoutSlots / qualifiersPerGroup；元素 = 本组别 player.id 字符串
 * （注意与创建接口的"名册下标"契约不同，沿 draw-slots 的编辑用 id 惯例）；
 * 组内顺序即 group_position（决定组内轮转座次，有比赛意义）。
 * 每组 ≥ max(2, qualifiersPerGroup)，允许组间不均；每人恰属一组。
 */
public class UpdateGroupAssignmentsReq {

    private List<List<String>> groups;

    public List<List<String>> getGroups() { return groups; }
    public void setGroups(List<List<String>> groups) { this.groups = groups; }
}
