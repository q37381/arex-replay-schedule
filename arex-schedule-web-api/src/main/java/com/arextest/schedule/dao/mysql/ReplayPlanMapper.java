package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper of the replay_plan table (mongo ReplayPlan collection).
 */
@Mapper
public interface ReplayPlanMapper {

  String COLUMNS = "id, app_id, plan_name, source_env, target_env, source_host, target_host, "
      + "target_image_id, target_image_name, case_source_from, case_source_to, plan_create_time, "
      + "plan_finish_time, operator, arex_cord_version, arex_ext_version, case_record_version, "
      + "case_total_count, case_source_type, replay_plan_type, case_count_limit, "
      + "replay_plan_stage_list, data_change_create_time, data_change_update_time";

  @Insert("INSERT INTO replay_plan (" + COLUMNS + ") VALUES ("
      + "#{id}, #{appId}, #{planName}, #{sourceEnv}, #{targetEnv}, #{sourceHost}, #{targetHost}, "
      + "#{targetImageId}, #{targetImageName}, #{caseSourceFrom}, #{caseSourceTo}, "
      + "#{planCreateTime}, #{planFinishTime}, #{operator}, #{arexCordVersion}, #{arexExtVersion}, "
      + "#{caseRecordVersion}, #{caseTotalCount}, #{caseSourceType}, #{replayPlanType}, "
      + "#{caseCountLimit}, #{replayPlanStageList}, #{dataChangeCreateTime}, "
      + "#{dataChangeUpdateTime})")
  int insert(ReplayPlanEntity entity);

  @Update("UPDATE replay_plan SET case_total_count = #{caseTotal}, "
      + "data_change_update_time = #{now} WHERE id = #{planId}")
  int updateCaseTotal(@Param("planId") String planId, @Param("caseTotal") int caseTotal,
      @Param("now") long now);

  @Update("UPDATE replay_plan SET plan_finish_time = #{now}, data_change_update_time = #{now} "
      + "WHERE id = #{planId}")
  int finish(@Param("planId") String planId, @Param("now") long now);

  /**
   * Unfinished plans created inside [from - maxDuration, from].
   */
  @Select("SELECT " + COLUMNS + " FROM replay_plan WHERE plan_finish_time IS NULL "
      + "AND plan_create_time >= #{fromInclusive} AND plan_create_time <= #{toInclusive}")
  List<ReplayPlanEntity> timeoutPlanList(@Param("fromInclusive") long fromInclusive,
      @Param("toInclusive") long toInclusive);

  @Update("UPDATE replay_plan SET replay_plan_stage_list = #{stageList}, "
      + "data_change_update_time = #{now} WHERE id = #{id}")
  int updateStage(@Param("id") String id, @Param("stageList") String stageList,
      @Param("now") long now);

  @Select("SELECT " + COLUMNS + " FROM replay_plan WHERE id = #{planId}")
  ReplayPlanEntity query(@Param("planId") String planId);
}
