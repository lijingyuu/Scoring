package com.scoring.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scoring.backend.domain.entity.TeamMatchItem;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface TeamMatchItemMapper extends BaseMapper<TeamMatchItem> {

    /**
     * 团体赛子项锁定读。调用方必须已持有父场 match_record 行锁（全局锁序：父场 → items）。
     * REPEATABLE READ 下普通 selectList 走事务早期快照，并发完赛的子场互相看不到
     * 对方已提交的 winner_side，会导致父场结算漏判。
     */
    @Select("SELECT * FROM team_match_item WHERE match_id = #{matchId} FOR UPDATE")
    List<TeamMatchItem> selectListByMatchIdForUpdate(@Param("matchId") String matchId);
}
