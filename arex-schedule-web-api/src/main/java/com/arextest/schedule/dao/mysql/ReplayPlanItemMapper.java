package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper of the replay_plan_item table (mongo ReplayPlanItem
 * collection). The $inc on noiseFinishedContexts.&lt;contextName&gt; becomes a
 * single atomic JSON_SET statement.
 */
@Mapper
public interface ReplayPlanItemMapper {

  String COLUMNS = "id, app_id, operation_id, plan_id, replay_case_count, noise_finished_contexts, "
      + "replay_status, replay_begin_time, replay_finish_time, data_change_create_time, "
      + "data_change_update_time";

  @Insert("INSERT INTO replay_plan_item (" + COLUMNS + ") VALUES ("
      + "#{id}, #{appId}, #{operationId}, #{planId}, #{replayCaseCount}, #{noiseFinishedContexts}, "
      + "#{replayStatus}, #{replayBeginTime}, #{replayFinishTime}, #{dataChangeCreateTime}, "
      + "#{dataChangeUpdateTime})")
  int insert(ReplayPlanItemEntity entity);

  @Insert("<script>"
      + "INSERT INTO replay_plan_item (" + COLUMNS + ") VALUES "
      + "<foreach collection='list' item='it' separator=','>"
      + "(#{it.id}, #{it.appId}, #{it.operationId}, #{it.planId}, #{it.replayCaseCount}, "
      + "#{it.noiseFinishedContexts}, #{it.replayStatus}, #{it.replayBeginTime}, "
      + "#{it.replayFinishTime}, #{it.dataChangeCreateTime}, #{it.dataChangeUpdateTime})"
      + "</foreach>"
      + "</script>")
  int insertBatch(@Param("list") List<ReplayPlanItemEntity> list);

  @Update("UPDATE replay_plan_item SET replay_status = #{it.replayStatus}, "
      + "replay_begin_time = #{it.replayBeginTime}, replay_finish_time = #{it.replayFinishTime}, "
      + "replay_case_count = #{it.replayCaseCount}, data_change_update_time = #{now} "
      + "WHERE id = #{it.id}")
  int update(@Param("it") ReplayPlanItemEntity entity, @Param("now") long now);

  @Select("SELECT " + COLUMNS + " FROM replay_plan_item WHERE plan_id = #{planId}")
  List<ReplayPlanItemEntity> queryByPlanId(@Param("planId") String planId);

  /**
   * Atomic equivalent of mongo's $inc on the dynamic key
   * noiseFinishedContexts.&lt;contextName&gt;. The json path is bound as a
   * prepared-statement parameter (pre-built escaped $."key"), so no SQL/JSON
   * path injection is possible.
   */
  @Update("UPDATE replay_plan_item SET noise_finished_contexts = "
      + "JSON_SET(COALESCE(noise_finished_contexts, JSON_OBJECT()), "
      + "#{path}, "
      + "COALESCE(CAST(JSON_UNQUOTE(JSON_EXTRACT(noise_finished_contexts, #{path})) AS SIGNED), 0) "
      + "+ #{count}), "
      + "data_change_update_time = #{now} WHERE id = #{id}")
  int incNoiseFinishedContext(@Param("id") String id, @Param("path") String path,
      @Param("count") int count, @Param("now") long now);

  @Update("UPDATE replay_plan_item SET noise_finished_contexts = #{noiseFinishedContexts}, "
      + "data_change_update_time = #{now} WHERE id = #{id}")
  int updateNoiseFinishedContexts(@Param("id") String id,
      @Param("noiseFinishedContexts") String noiseFinishedContexts, @Param("now") long now);
}
