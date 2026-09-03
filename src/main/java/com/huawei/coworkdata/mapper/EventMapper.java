package com.huawei.coworkdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huawei.coworkdata.entity.EventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface EventMapper extends BaseMapper<EventEntity> {

    @Select("SELECT opened.session_id "
            + "FROM ( "
            + "    SELECT session_id, MAX(id) AS opened "
            + "    FROM events "
            + "    WHERE type IN ('SessionCreated', 'SessionResumed') "
            + "    GROUP BY session_id "
            + ") opened "
            + "INNER JOIN sessions s ON s.id = opened.session_id AND s.delete_at IS NULL "
            + "LEFT JOIN ( "
            + "    SELECT session_id, MAX(id) AS finished "
            + "    FROM events "
            + "    WHERE type = 'SessionFinished' "
            + "    GROUP BY session_id "
            + ") finished ON finished.session_id = opened.session_id "
            + "WHERE finished.finished IS NULL OR opened.opened > finished.finished")
    List<String> listActiveSessionIds();

    @Select("SELECT session_id, MAX(timestamp) AS last_ts "
            + "FROM events "
            + "WHERE type NOT IN (${excludeTypes}) "
            + "GROUP BY session_id")
    List<Map<String, Object>> lastActivityRows(@Param("excludeTypes") String excludeTypesSql);
}
