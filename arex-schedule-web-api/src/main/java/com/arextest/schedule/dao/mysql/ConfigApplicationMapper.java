package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.mysql.ConfigRows.Application;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * Read-only mapper of the shared config_application table (owned by
 * arex-storage). Backs the AppCollection lookup of AppAuthAspectExecutor.
 */
@Mapper
public interface ConfigApplicationMapper {

  String COLUMNS = "seq, id, app_id, features, group_name, group_id, agent_version, "
      + "agent_ext_version, app_name, description, category, owner, owners, organization_name, "
      + "recorded_case_count, organization_id, status, visibility_level, tags, "
      + "data_change_create_time, data_change_update_time";

  @Select("SELECT " + COLUMNS + " FROM config_application WHERE app_id = #{appId} "
      + "ORDER BY seq DESC LIMIT 1")
  Application selectByAppId(@Param("appId") String appId);
}
