package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.mysql.ConfigRows.System;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only mapper of the shared config_system table (owned by arex-storage).
 * Backs the auth-switch lookup of AppAuthAspectExecutor.
 */
@Mapper
public interface ConfigSystemMapper {

  String COLUMNS = "id, config_key, config_value, data_change_create_time, "
      + "data_change_update_time";

  @Select("SELECT " + COLUMNS + " FROM config_system WHERE config_key = #{key} LIMIT 1")
  System selectByKey(@Param("key") String key);
}
