package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Read-only flat rows of the shared config tables owned by arex-storage
 * (config_application / config_system in arex_storage_db). Complex fields
 * (owners, tags, desensitizationJar, ...) stay JSON strings here and are
 * decoded in the repository layer via {@link MysqlJsonSupport}.
 */
public final class ConfigRows {

  private ConfigRows() {
  }

  @Data
  public static class Application {

    private Long autoId;
    private String id;
    private String appId;
    private Integer features;
    private String groupName;
    private String groupId;
    private String agentVersion;
    private String agentExtVersion;
    private String appName;
    private String description;
    private String category;
    private String owner;
    private String owners;
    private String organizationName;
    private Integer recordedCaseCount;
    private String organizationId;
    private Integer status;
    private Integer visibilityLevel;
    private String tags;
    private Long dataChangeCreateTime;
    private Long dataChangeUpdateTime;
  }

  @Data
  public static class System {

    private String id;
    private String configKey;
    private String refreshTaskMark;
    private String desensitizationJar;
    private String callbackUrl;
    private Boolean authSwitch;
    private String comparePluginInfo;
    private String jwtSeed;
    private String ignoreNodeSet;
    private Long dataChangeCreateTime;
    private Long dataChangeUpdateTime;
  }
}
