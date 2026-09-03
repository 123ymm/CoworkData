package com.huawei.coworkdata.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.huawei.coworkdata.entity.MemoryEventEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface MemoryEventMapper extends BaseMapper<MemoryEventEntity> {

    @Select("SELECT COALESCE(MAX(seq_no), 0) FROM memory_events "
            + "WHERE session_id = #{sessionId} AND layer = #{layer} "
            + "  AND (#{taskId} IS NULL OR task_id = #{taskId}) "
            + "  AND (#{agentId} IS NULL OR agent_id = #{agentId})")
    int maxSeqNo(
            @Param("sessionId") String sessionId,
            @Param("layer") String layer,
            @Param("taskId") String taskId,
            @Param("agentId") String agentId);

    @Select("SELECT COALESCE(MAX(topic_seq_no), 0) FROM memory_events WHERE topic = #{topic}")
    int maxTopicSeqNo(@Param("topic") String topic);

    @Update("UPDATE memory_events SET is_superseded = TRUE "
            + "WHERE topic = #{topic} "
            + "  AND type IN ('publication', 'session_publication') "
            + "  AND is_superseded = FALSE")
    int supersedePublicationTopic(@Param("topic") String topic);

    @Select("<script>"
            + "SELECT * FROM memory_events "
            + "WHERE session_id = #{sessionId} "
            + "  AND layer = #{layer} "
            + "  AND is_superseded = FALSE "
            + "  AND type IN "
            + "  <foreach item='t' collection='types' open='(' separator=',' close=')'> "
            + "    #{t} "
            + "  </foreach> "
            + "<if test='taskId != null'> AND task_id = #{taskId}</if> "
            + "<if test='agentId != null'> AND agent_id = #{agentId}</if> "
            + "ORDER BY timestamp ASC, seq_no ASC "
            + "</script>")
    List<MemoryEventEntity> loadViewRaw(
            @Param("sessionId") String sessionId,
            @Param("layer") String layer,
            @Param("types") List<String> types,
            @Param("taskId") String taskId,
            @Param("agentId") String agentId);
}
