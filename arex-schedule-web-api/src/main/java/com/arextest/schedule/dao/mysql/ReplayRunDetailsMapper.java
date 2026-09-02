package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.ReplayActionCaseItemRepository.GroupCountRes;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper of the replay_run_details table (mongo ReplayRunDetails
 * collection) - the hot case table of the replay pipeline.
 */
@Mapper
public interface ReplayRunDetailsMapper {

  String COLUMNS = "id, plan_id, plan_item_id, operation_id, case_provider_code, record_id, "
      + "target_result_id, source_result_id, context_identifier, send_status, compare_status, "
      + "case_type, record_time, expiration_time, target_request, case_status, "
      + "data_change_create_time, data_change_update_time";

  @Insert("INSERT INTO replay_run_details (" + COLUMNS + ") VALUES ("
      + "#{id}, #{planId}, #{planItemId}, #{operationId}, #{caseProviderCode}, #{recordId}, "
      + "#{targetResultId}, #{sourceResultId}, #{contextIdentifier}, #{sendStatus}, "
      + "#{compareStatus}, #{caseType}, #{recordTime}, #{expirationTime}, #{targetRequest}, "
      + "#{caseStatus}, #{dataChangeCreateTime}, #{dataChangeUpdateTime})")
  int insert(ReplayRunDetailsEntity entity);

  @Insert("<script>"
      + "INSERT INTO replay_run_details (" + COLUMNS + ") VALUES "
      + "<foreach collection='list' item='it' separator=','>"
      + "(#{it.id}, #{it.planId}, #{it.planItemId}, #{it.operationId}, #{it.caseProviderCode}, "
      + "#{it.recordId}, #{it.targetResultId}, #{it.sourceResultId}, #{it.contextIdentifier}, "
      + "#{it.sendStatus}, #{it.compareStatus}, #{it.caseType}, #{it.recordTime}, "
      + "#{it.expirationTime}, #{it.targetRequest}, #{it.caseStatus}, #{it.dataChangeCreateTime}, "
      + "#{it.dataChangeUpdateTime})"
      + "</foreach>"
      + "</script>")
  int insertBatch(@Param("list") List<ReplayRunDetailsEntity> list);

  /**
   * Pending cases of the plan ordered ascending by record time (the order
   * drives the extra-mocker yield sequence; do not change it).
   */
  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM replay_run_details "
      + "<where>"
      + "plan_id = #{planId} "
      + "AND (send_status = #{waitSend} OR compare_status = #{waitCompare}) "
      + "<if test='nullContext'>AND context_identifier IS NULL </if>"
      + "<if test='contextIdentifier != null'>AND context_identifier = #{contextIdentifier} </if>"
      + "<if test='planItemIds != null'>AND plan_item_id IN "
      + "<foreach collection='planItemIds' item='itemId' open='(' separator=',' close=')'>"
      + "#{itemId}</foreach> </if>"
      + "<if test='minRecordTime != null'>AND record_time &gt;= #{minRecordTime} </if>"
      + "</where>"
      + "ORDER BY record_time ASC LIMIT #{pageSize}"
      + "</script>")
  List<ReplayRunDetailsEntity> waitingSendList(@Param("planId") String planId,
      @Param("pageSize") int pageSize, @Param("nullContext") boolean nullContext,
      @Param("contextIdentifier") String contextIdentifier,
      @Param("planItemIds") List<String> planItemIds,
      @Param("minRecordTime") Long minRecordTime,
      @Param("waitSend") int waitSend, @Param("waitCompare") int waitCompare);

  /**
   * Cases that failed to send or compare, excluding expired ones.
   */
  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM replay_run_details "
      + "<where>"
      + "plan_id = #{planId} "
      + "<if test='planItemId != null'>AND plan_item_id = #{planItemId} </if>"
      + "AND (send_status != #{sendSuccess} OR compare_status != #{comparePass}) "
      + "AND (expiration_time IS NULL OR expiration_time &gt; #{now}) "
      + "AND case_status = #{caseNormal}"
      + "</where>"
      + "</script>")
  List<ReplayRunDetailsEntity> failedCaseList(@Param("planId") String planId,
      @Param("planItemId") String planItemId, @Param("sendSuccess") int sendSuccess,
      @Param("comparePass") int comparePass, @Param("caseNormal") int caseNormal,
      @Param("now") long now);

  @Select("<script>"
      + "SELECT plan_item_id AS planItemId, COUNT(1) AS count FROM replay_run_details "
      + "<where>"
      + "plan_id = #{planId} AND send_status = #{waitSend} "
      + "<if test='nullContext'>AND context_identifier IS NULL </if>"
      + "<if test='contextIdentifier != null'>AND context_identifier = #{contextIdentifier} </if>"
      + "<if test='planItemIds != null'>AND plan_item_id IN "
      + "<foreach collection='planItemIds' item='itemId' open='(' separator=',' close=')'>"
      + "#{itemId}</foreach> </if>"
      + "</where>"
      + "GROUP BY plan_item_id"
      + "</script>")
  List<GroupCountRes> countWaitHandlingByAction(@Param("planId") String planId,
      @Param("nullContext") boolean nullContext,
      @Param("contextIdentifier") String contextIdentifier,
      @Param("planItemIds") List<String> planItemIds,
      @Param("waitSend") int waitSend);

  @Update("UPDATE replay_run_details SET send_status = #{it.sendStatus}, "
      + "source_result_id = #{it.sourceResultId}, target_result_id = #{it.targetResultId}, "
      + "data_change_update_time = #{now} WHERE id = #{it.id}")
  int updateSendResult(@Param("it") ReplayRunDetailsEntity entity, @Param("now") long now);

  @Update("UPDATE replay_run_details SET compare_status = #{compareStatus}, "
      + "data_change_update_time = #{now} WHERE id = #{id}")
  int updateCompareStatus(@Param("id") String id, @Param("compareStatus") int compareStatus,
      @Param("now") long now);

  /**
   * Single-row status update; the repository loops over the batch instead of
   * using a multi-statement script, so the JDBC url needs no
   * allowMultiQueries flag.
   */
  @Update("UPDATE replay_run_details SET send_status = #{it.sendStatus}, "
      + "compare_status = #{it.compareStatus}, data_change_update_time = #{now} "
      + "WHERE id = #{it.id}")
  int updateStatus(@Param("it") ReplayRunDetailsEntity entity, @Param("now") long now);

  /**
   * Case count and earliest record time grouped by case type.
   */
  @Select("SELECT case_type AS caseType, MIN(record_time) AS lastRecordTime, COUNT(1) AS count "
      + "FROM replay_run_details WHERE plan_item_id = #{planItemId} GROUP BY case_type")
  List<GroupCountRes> getLastRecord(@Param("planItemId") String planItemId);

  /**
   * Distinct context identifiers of cases not fully succeeded yet. NULL is
   * included, mirroring mongo's findDistinct.
   */
  @Select("SELECT DISTINCT context_identifier FROM replay_run_details WHERE plan_id = #{planId} "
      + "AND (send_status != #{sendSuccess} OR compare_status != #{comparePass})")
  List<String> getAllContextIdentifiers(@Param("planId") String planId,
      @Param("sendSuccess") int sendSuccess, @Param("comparePass") int comparePass);

  @Select("SELECT 1 FROM replay_run_details WHERE plan_id = #{planId} "
      + "AND (send_status != #{sendSuccess} OR compare_status != #{comparePass}) "
      + "AND context_identifier IS NULL LIMIT 1")
  Integer hasNullIdentifier(@Param("planId") String planId, @Param("sendSuccess") int sendSuccess,
      @Param("comparePass") int comparePass);

  @Select("SELECT " + COLUMNS + " FROM replay_run_details WHERE plan_id = #{planId} "
      + "AND context_identifier = #{contextIdentifier} LIMIT 1")
  ReplayRunDetailsEntity getOneOfContext(@Param("planId") String planId,
      @Param("contextIdentifier") String contextIdentifier);

  @Select("SELECT " + COLUMNS + " FROM replay_run_details WHERE id = #{id}")
  ReplayRunDetailsEntity queryById(@Param("id") String id);

  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM replay_run_details WHERE id IN "
      + "<foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach>"
      + "</script>")
  List<ReplayRunDetailsEntity> batchQueryById(@Param("ids") List<String> ids);

  @Delete("<script>"
      + "DELETE FROM replay_run_details WHERE plan_id = #{planId} AND plan_item_id IN "
      + "<foreach collection='planItemIds' item='id' open='(' separator=',' close=')'>"
      + "#{id}</foreach>"
      + "</script>")
  int deleteExcludedCases(@Param("planId") String planId,
      @Param("planItemIds") List<String> planItemIds);

  @Update("UPDATE replay_run_details SET case_status = #{caseStatus}, "
      + "data_change_update_time = #{now} WHERE record_id = #{recordId}")
  int updateCaseStatus(@Param("recordId") String recordId, @Param("caseStatus") Integer caseStatus,
      @Param("now") long now);
}
