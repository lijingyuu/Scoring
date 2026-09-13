package com.scoring.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scoring.backend.domain.entity.TournamentDivision;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface TournamentDivisionMapper extends BaseMapper<TournamentDivision> {

    @Select("SELECT * FROM tournament_division WHERE id = #{id} FOR UPDATE")
    TournamentDivision selectByIdForUpdate(@Param("id") String id);
}
