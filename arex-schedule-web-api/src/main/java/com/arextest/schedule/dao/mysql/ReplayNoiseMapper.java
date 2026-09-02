package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * MyBatis mapper of the replay_noise table (mongo ReplayNoise collection).
 * The unique key (plan_id, plan_item_id, category_name, operation_name_hash)
 * backs the mongo upsert semantics; callers merge the JSON item maps inside
 * a transaction guarded by selectForUpdate.
 */
@Mapper
public interface ReplayNoiseMapper {

  String COLUMNS = "id, plan_id, plan_item_id, category_name, operation_id, operation_name, "
      + "operation_name_hash, may_ignore_items, may_disorder_items, data_change_create_date, "
      + "data_change_create_time, data_change_update_time";

  @Insert("INSERT INTO replay_noise (" + COLUMNS + ") VALUES ("
      + "#{id}, #{planId}, #{planItemId}, #{categoryName}, #{operationId}, #{operationName}, "
      + "#{operationNameHash}, #{mayIgnoreItems}, #{mayDisorderItems}, #{dataChangeCreateDate}, "
      + "#{dataChangeCreateTime}, #{dataChangeUpdateTime})")
  int insert(ReplayNoiseEntity entity);

  /**
   * Row lock for the read-merge-write cycle of the noise item maps, matched on
   * the hashed unique key (plus the name itself to rule out hash collisions).
   */
  @Select("SELECT " + COLUMNS + " FROM replay_noise WHERE plan_id = #{planId} "
      + "AND plan_item_id = #{planItemId} AND category_name = #{categoryName} "
      + "AND operation_name_hash = #{operationNameHash} "
      + "AND operation_name = #{operationName} FOR UPDATE")
  ReplayNoiseEntity selectForUpdate(@Param("planId") String planId,
      @Param("planItemId") String planItemId, @Param("categoryName") String categoryName,
      @Param("operationNameHash") Long operationNameHash,
      @Param("operationName") String operationName);

  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM replay_noise WHERE plan_id = #{planId} "
      + "<if test='planItemId != null'>AND plan_item_id = #{planItemId} </if>"
      + "</script>")
  List<ReplayNoiseEntity> queryByPlan(@Param("planId") String planId,
      @Param("planItemId") String planItemId);

  /**
   * Equality lookup driven by the queryFields map of UpdateNoiseItem
   * (planId / operationId / categoryName triple in practice), locking the
   * matched rows so the JSON status rewrite is atomic under concurrency.
   */
  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM replay_noise "
      + "<where>"
      + "<if test='planId != null'>AND plan_id = #{planId} </if>"
      + "<if test='planItemId != null'>AND plan_item_id = #{planItemId} </if>"
      + "<if test='categoryName != null'>AND category_name = #{categoryName} </if>"
      + "<if test='operationId != null'>AND operation_id = #{operationId} </if>"
      + "<if test='operationName != null'>AND operation_name = #{operationName} </if>"
      + "</where>"
      + " FOR UPDATE"
      + "</script>")
  List<ReplayNoiseEntity> queryByFieldsForUpdate(@Param("planId") String planId,
      @Param("planItemId") String planItemId, @Param("categoryName") String categoryName,
      @Param("operationId") String operationId, @Param("operationName") String operationName);

  @Update("UPDATE replay_noise SET may_ignore_items = #{mayIgnoreItems}, "
      + "may_disorder_items = #{mayDisorderItems}, data_change_update_time = #{now} "
      + "WHERE id = #{id}")
  int updateItems(ReplayNoiseEntity entity, @Param("now") long now);

  @Delete("<script>"
      + "DELETE FROM replay_noise WHERE plan_item_id IN "
      + "<foreach collection='planItemIds' item='id' open='(' separator=',' close=')'>"
      + "#{id}</foreach>"
      + "</script>")
  int deleteByPlanItemIds(@Param("planItemIds") List<String> planItemIds);
}
