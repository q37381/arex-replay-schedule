package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_plan table (mongo ReplayPlan collection).
 * replayPlanStageList is stored as a JSON text column.
 */
@Data
public class ReplayPlanEntity {

  private String id;
  private String appId;
  private String planName;
  private String sourceEnv;
  private String targetEnv;
  private String sourceHost;
  private String targetHost;
  private String targetImageId;
  private String targetImageName;
  private Long caseSourceFrom;
  private Long caseSourceTo;
  private Long planCreateTime;
  private Long planFinishTime;
  private String operator;
  private String arexCordVersion;
  private String arexExtVersion;
  private String caseRecordVersion;
  private Integer caseTotalCount;
  private Integer caseSourceType;
  private Integer replayPlanType;
  private Integer caseCountLimit;
  private String replayPlanStageList;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
