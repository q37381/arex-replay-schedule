package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper of the replay_biz_log table (mongo ReplayBizLog collection).
 * Rows are listed ascending by the auto-increment auto_id, mirroring the mongo
 * edition's _id ASC ordering.
 */
@Mapper
public interface ReplayBizLogMapper {

  String COLUMNS = "id, log_date, log_level, message, log_type, plan_id, resumed_execution, "
      + "context_name, context_identifier, case_item_id, action_item_id, operation_name, "
      + "exception, request, response, trace_id, extra, re_run, data_change_create_time, "
      + "data_change_update_time";

  String FILTERS = "<if test='levels != null and levels.size() > 0'>"
      + "AND log_level IN <foreach collection='levels' item='v' open='(' separator=',' close=')'>#{v}</foreach>"
      + "</if>"
      + "<if test='types != null and types.size() > 0'>"
      + "AND log_type IN <foreach collection='types' item='v' open='(' separator=',' close=')'>#{v}</foreach>"
      + "</if>"
      + "<if test='actionItems != null and actionItems.size() > 0'>"
      + "AND action_item_id IN <foreach collection='actionItems' item='v' open='(' separator=',' close=')'>#{v}</foreach>"
      + "</if>"
      + "<if test='resumedExecution != null'>AND resumed_execution = #{resumedExecution}</if>";

  @Insert("<script>INSERT INTO replay_biz_log (" + COLUMNS + ") VALUES "
      + "<foreach collection='list' item='it' separator=','>"
      + "(#{it.id}, #{it.logDate}, #{it.logLevel}, #{it.message}, #{it.logType}, #{it.planId}, "
      + "#{it.resumedExecution}, #{it.contextName}, #{it.contextIdentifier}, #{it.caseItemId}, "
      + "#{it.actionItemId}, #{it.operationName}, #{it.exception}, #{it.request}, #{it.response}, "
      + "#{it.traceId}, #{it.extra}, #{it.reRun}, #{it.dataChangeCreateTime}, "
      + "#{it.dataChangeUpdateTime})"
      + "</foreach></script>")
  int batchInsert(@Param("list") List<ReplayBizLogEntity> list);

  @Select("<script>SELECT auto_id, " + COLUMNS + " FROM replay_biz_log WHERE plan_id = #{planId} "
      + FILTERS + " ORDER BY auto_id ASC "
      + "<if test='offset != null and limit != null'>LIMIT #{offset}, #{limit}</if></script>")
  List<ReplayBizLogEntity> query(@Param("planId") String planId,
      @Param("levels") List<Integer> levels, @Param("types") List<Integer> types,
      @Param("actionItems") List<String> actionItems,
      @Param("resumedExecution") Boolean resumedExecution,
      @Param("offset") Integer offset, @Param("limit") Integer limit);

  @Select("<script>SELECT COUNT(*) FROM replay_biz_log WHERE plan_id = #{planId} "
      + FILTERS + "</script>")
  long count(@Param("planId") String planId,
      @Param("levels") List<Integer> levels, @Param("types") List<Integer> types,
      @Param("actionItems") List<String> actionItems,
      @Param("resumedExecution") Boolean resumedExecution);
}
