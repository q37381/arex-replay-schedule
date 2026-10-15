package com.arextest.schedule.dao.mysql;

import com.arextest.config.model.dao.config.SystemConfigurationCollection;
import com.arextest.config.model.dao.config.SystemConfigurationCollection.KeySummary;
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
    String value = row.getConfigValue();
    if (value != null) {
      dispatchValue(collection, key, value);
    }
    collection.setDataChangeCreateTime(row.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(row.getDataChangeUpdateTime());
    return collection;
  }

  private void dispatchValue(SystemConfigurationCollection c, String key, String value) {
    switch (key) {
      case KeySummary.AUTH_SWITCH:
        c.setAuthSwitch(Boolean.parseBoolean(value));
        break;
      case KeySummary.CALLBACK_URL:
        c.setCallbackUrl(value);
        break;
      case KeySummary.JWT_SEED:
        c.setJwtSeed(value);
        break;
      case KeySummary.REFRESH_DATA:
        c.setRefreshTaskMark(MysqlJsonSupport.readJson(value, REFRESH_TASK_MARK_TYPE));
        break;
      case KeySummary.DESERIALIZATION_JAR:
        c.setDesensitizationJar(MysqlJsonSupport.readJson(value, DesensitizationJar.class));
        break;
      case KeySummary.COMPARE_PLUGIN_INFO:
        c.setComparePluginInfo(MysqlJsonSupport.readJson(value, ComparePluginInfo.class));
        break;
      case KeySummary.IGNORE_NODE_SET:
        c.setIgnoreNodeSet(MysqlJsonSupport.readJson(value, STRING_SET));
        break;
      default:
        c.setConfigValue(value);
        break;
    }
  }
}
