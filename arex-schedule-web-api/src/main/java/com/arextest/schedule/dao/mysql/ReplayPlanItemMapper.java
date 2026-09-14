package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper of the replay_plan_item table (mongo ReplayPlanItem
 * collection). noise_finished_contexts is a plain TEXT column holding a JSON
 * object; the mongo $inc on noiseFinishedContexts.&lt;contextName&gt; is done in
 * the repository as a read-merge-write guarded by selectNoiseForUpdate, so no
 * MySQL JSON path function is involved.
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
   * Row lock for the read-merge-write cycle of noise_finished_contexts, mirroring
   * the replay_noise mapper. The JSON map is merged in the repository (Java), not
   * in SQL, so concurrent $inc on different context keys of the same row serialize
   * on this lock instead of racing.
   */
  @Select("SELECT id, noise_finished_contexts FROM replay_plan_item WHERE id = #{id} FOR UPDATE")
  ReplayPlanItemEntity selectNoiseForUpdate(@Param("id") String id);

  @Update("UPDATE replay_plan_item SET noise_finished_contexts = #{noiseFinishedContexts}, "
      + "data_change_update_time = #{now} WHERE id = #{id}")
  int updateNoiseFinishedContexts(@Param("id") String id,
      @Param("noiseFinishedContexts") String noiseFinishedContexts, @Param("now") long now);
}
