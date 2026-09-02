package com.arextest.schedule.dao.mysql;

import com.arextest.config.model.dao.config.SystemConfigurationCollection;
import com.arextest.config.model.dto.system.ComparePluginInfo;
import com.arextest.config.model.dto.system.DesensitizationJar;
import com.arextest.schedule.dao.SystemConfigurationRepository;
import com.arextest.schedule.dao.mysql.ConfigRows.System;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Mysql implementation of {@link SystemConfigurationRepository}: read-only
 * lookup on the shared config_system table owned by arex-storage. Active when
 * arex.schedule.repository.type=mysql (the default).
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class SystemConfigurationMysqlRepository implements SystemConfigurationRepository {

  private static final TypeReference<Map<String, Integer>> REFRESH_TASK_MARK_TYPE =
      new TypeReference<Map<String, Integer>>() {
      };
  private static final TypeReference<Set<String>> STRING_SET = new TypeReference<Set<String>>() {
  };

  private final ConfigSystemMapper configSystemMapper;

  @Override
  public SystemConfigurationCollection queryByKey(String key) {
    System row = configSystemMapper.selectByKey(key);
    if (row == null) {
      return null;
    }
    SystemConfigurationCollection collection = new SystemConfigurationCollection();
    collection.setId(row.getId());
    collection.setKey(row.getConfigKey());
    collection.setRefreshTaskMark(
        MysqlJsonSupport.readJson(row.getRefreshTaskMark(), REFRESH_TASK_MARK_TYPE));
    collection.setDesensitizationJar(
        MysqlJsonSupport.readJson(row.getDesensitizationJar(), DesensitizationJar.class));
    collection.setCallbackUrl(row.getCallbackUrl());
    collection.setAuthSwitch(row.getAuthSwitch());
    collection.setComparePluginInfo(
        MysqlJsonSupport.readJson(row.getComparePluginInfo(), ComparePluginInfo.class));
    collection.setJwtSeed(row.getJwtSeed());
    collection.setIgnoreNodeSet(MysqlJsonSupport.readJson(row.getIgnoreNodeSet(), STRING_SET));
    collection.setDataChangeCreateTime(row.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(row.getDataChangeUpdateTime());
    return collection;
  }
}
