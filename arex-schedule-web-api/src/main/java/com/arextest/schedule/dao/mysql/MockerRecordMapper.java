package com.arextest.schedule.dao.mysql;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * MyBatis mapper for arex_storage_db.mocker_record table.
 * Reads record/replay mockers directly from storage's MySQL.
 */
@Mapper
public interface MockerRecordMapper {

  String COLUMNS = "id, category, record_id, replay_id, app_id, operation_name, "
      + "record_env, record_version, use_mock, creation_time, update_time, expiration_time, "
      + "tags, eigen_map, target_request, target_response";

  /**
   * Query mockers by recordId and optional categories.
   */
  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM mocker_record "
      + "<where>"
      + "record_id = #{recordId} "
      + "<if test='categories != null and categories.size() > 0'>"
      + "AND category IN "
      + "<foreach collection='categories' item='cat' open='(' separator=',' close=')'>"
      + "#{cat}</foreach>"
      + "</if>"
      + "</where>"
      + "ORDER BY creation_time ASC"
      + "</script>")
  List<MockerRecordEntity> queryByRecordId(@Param("recordId") String recordId,
      @Param("categories") List<String> categories);

  /**
   * Query mockers by replayId and optional categories.
   */
  @Select("<script>"
      + "SELECT " + COLUMNS + " FROM mocker_record "
      + "<where>"
      + "replay_id = #{replayId} "
      + "<if test='categories != null and categories.size() > 0'>"
      + "AND category IN "
      + "<foreach collection='categories' item='cat' open='(' separator=',' close=')'>"
      + "#{cat}</foreach>"
      + "</if>"
      + "</where>"
      + "ORDER BY creation_time ASC"
      + "</script>")
  List<MockerRecordEntity> queryByReplayId(@Param("replayId") String replayId,
      @Param("categories") List<String> categories);
}
