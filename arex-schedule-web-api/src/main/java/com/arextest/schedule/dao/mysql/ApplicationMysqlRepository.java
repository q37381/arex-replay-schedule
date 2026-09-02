package com.arextest.schedule.dao.mysql;

import com.arextest.config.model.dao.config.AppCollection;
import com.arextest.schedule.dao.ApplicationRepository;
import com.arextest.schedule.dao.mysql.ConfigRows.Application;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Mysql implementation of {@link ApplicationRepository}: read-only lookup on
 * the shared config_application table owned by arex-storage. Active when
 * arex.schedule.repository.type=mysql (the default).
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ApplicationMysqlRepository implements ApplicationRepository {

  private static final TypeReference<Set<String>> STRING_SET = new TypeReference<Set<String>>() {
  };
  private static final TypeReference<Map<String, Set<String>>> TAGS_MAP =
      new TypeReference<Map<String, Set<String>>>() {
      };

  private final ConfigApplicationMapper configApplicationMapper;

  @Override
  public AppCollection query(String appId) {
    Application row = configApplicationMapper.selectByAppId(appId);
    if (row == null) {
      return null;
    }
    AppCollection collection = new AppCollection();
    collection.setId(row.getId());
    collection.setAppId(row.getAppId());
    collection.setFeatures(row.getFeatures() == null ? 0 : row.getFeatures());
    collection.setGroupName(row.getGroupName());
    collection.setGroupId(row.getGroupId());
    collection.setAgentVersion(row.getAgentVersion());
    collection.setAgentExtVersion(row.getAgentExtVersion());
    collection.setAppName(row.getAppName());
    collection.setDescription(row.getDescription());
    collection.setCategory(row.getCategory());
    collection.setOwner(row.getOwner());
    collection.setOwners(MysqlJsonSupport.readJson(row.getOwners(), STRING_SET));
    collection.setOrganizationName(row.getOrganizationName());
    collection.setRecordedCaseCount(row.getRecordedCaseCount());
    collection.setOrganizationId(row.getOrganizationId());
    collection.setStatus(row.getStatus());
    collection.setVisibilityLevel(row.getVisibilityLevel() == null ? 0 : row.getVisibilityLevel());
    collection.setTags(MysqlJsonSupport.readJson(row.getTags(), TAGS_MAP));
    collection.setDataChangeCreateTime(row.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(row.getDataChangeUpdateTime());
    return collection;
  }
}
