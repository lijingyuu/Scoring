package com.scoring.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scoring.backend.domain.entity.MatchRecord;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MatchRecordMapper extends BaseMapper<MatchRecord> {

    @Select("SELECT * FROM match_record WHERE id = #{id} FOR UPDATE")
    MatchRecord selectByIdForUpdate(@Param("id") String id);
}
