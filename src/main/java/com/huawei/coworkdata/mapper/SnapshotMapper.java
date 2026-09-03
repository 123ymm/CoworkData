package com.huawei.coworkdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huawei.coworkdata.entity.SnapshotEntity;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SnapshotMapper extends BaseMapper<SnapshotEntity> {

    @Delete("DELETE FROM snapshots "
            + "WHERE session_id = #{sessionId} "
            + "  AND id NOT IN ( "
            + "      SELECT id FROM snapshots "
            + "      WHERE session_id = #{sessionId} "
            + "      ORDER BY id DESC "
            + "      LIMIT #{keep} "
            + "  )")
    int pruneOldSnapshots(@Param("sessionId") String sessionId, @Param("keep") int keep);
}
