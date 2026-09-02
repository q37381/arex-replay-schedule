package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper of the replay_compare_result table (mongo
 * ReplayCompareResult collection).
 */
@Mapper
public interface ReplayCompareResultMapper {

  String COLUMNS = "id, plan_id, plan_item_id, case_id, operation_id, service_name, "
      + "category_name, operation_name, replay_id, record_id, record_time, replay_time, "
      + "instance_id, base_msg, test_msg, logs, `ignore`, diff_result_code, data_create_time, "
      + "msg_info, data_change_create_time, data_change_update_time";

  @Insert("<script>"
      + "INSERT INTO replay_compare_result (" + COLUMNS + ") VALUES "
      + "<foreach collection='list' item='it' separator=','>"
      + "(#{it.id}, #{it.planId}, #{it.planItemId}, #{it.caseId}, #{it.operationId}, "
      + "#{it.serviceName}, #{it.categoryName}, #{it.operationName}, #{it.replayId}, "
      + "#{it.recordId}, #{it.recordTime}, #{it.replayTime}, #{it.instanceId}, #{it.baseMsg}, "
      + "#{it.testMsg}, #{it.logs}, #{it.ignore}, #{it.diffResultCode}, #{it.dataCreateTime}, "
      + "#{it.msgInfo}, #{it.dataChangeCreateTime}, #{it.dataChangeUpdateTime})"
      + "</foreach>"
      + "</script>")
  int insertBatch(@Param("list") List<ReplayCompareResultEntity> list);

  /**
   * Mirrors mongo save(): insert or full update when the id already exists.
   */
  @Insert("<script>"
      + "INSERT INTO replay_compare_result (" + COLUMNS + ") VALUES ("
      + "#{it.id}, #{it.planId}, #{it.planItemId}, #{it.caseId}, #{it.operationId}, "
      + "#{it.serviceName}, #{it.categoryName}, #{it.operationName}, #{it.replayId}, "
      + "#{it.recordId}, #{it.recordTime}, #{it.replayTime}, #{it.instanceId}, #{it.baseMsg}, "
      + "#{it.testMsg}, #{it.logs}, #{it.ignore}, #{it.diffResultCode}, #{it.dataCreateTime}, "
      + "#{it.msgInfo}, #{it.dataChangeCreateTime}, #{it.dataChangeUpdateTime}) "
      + "ON DUPLICATE KEY UPDATE plan_id = VALUES(plan_id), plan_item_id = VALUES(plan_item_id), "
      + "case_id = VALUES(case_id), operation_id = VALUES(operation_id), "
      + "service_name = VALUES(service_name), category_name = VALUES(category_name), "
      + "operation_name = VALUES(operation_name), replay_id = VALUES(replay_id), "
      + "record_id = VALUES(record_id), record_time = VALUES(record_time), "
      + "replay_time = VALUES(replay_time), instance_id = VALUES(instance_id), "
      + "base_msg = VALUES(base_msg), test_msg = VALUES(test_msg), logs = VALUES(logs), "
      + "`ignore` = VALUES(`ignore`), diff_result_code = VALUES(diff_result_code), "
      + "data_create_time = VALUES(data_create_time), msg_info = VALUES(msg_info), "
      + "data_change_update_time = VALUES(data_change_update_time)"
      + "</script>")
  int upsert(@Param("it") ReplayCompareResultEntity entity);

  @Delete("DELETE FROM replay_compare_result WHERE record_id = #{recordId} "
      + "AND plan_item_id = #{planItemId}")
  int deleteByRecord(@Param("recordId") String recordId, @Param("planItemId") String planItemId);

  @Select("SELECT " + COLUMNS + " FROM replay_compare_result WHERE id = #{id}")
  ReplayCompareResultEntity queryById(@Param("id") String id);
}
