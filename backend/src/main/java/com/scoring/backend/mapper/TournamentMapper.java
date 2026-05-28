package com.scoring.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.scoring.backend.domain.entity.Tournament;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface TournamentMapper extends BaseMapper<Tournament> {

    @Select("SELECT * FROM tournament WHERE id = #{id} FOR UPDATE")
    Tournament selectByIdForUpdate(@Param("id") String id);

    @Update("UPDATE tournament SET favorite_count = favorite_count + 1 WHERE id = #{id}")
    int increaseFavoriteCount(@Param("id") String id);

    @Update("UPDATE tournament SET favorite_count = CASE WHEN favorite_count > 0 THEN favorite_count - 1 ELSE 0 END WHERE id = #{id}")
    int decreaseFavoriteCount(@Param("id") String id);
}
