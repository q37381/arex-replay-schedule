package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_compare_result table (mongo
 * ReplayCompareResult collection). baseMsg/testMsg/logs keep the compressed
 * + encrypted representation produced by ReplayCompareResultConverter;
 * msgInfo is stored as a JSON text column.
 */
@Data
public class ReplayCompareResultEntity {

  private String id;
  private String planId;
  private String planItemId;
  private String caseId;
  private String operationId;
  private String serviceName;
  private String categoryName;
  private String operationName;
  private String replayId;
  private String recordId;
  private Long recordTime;
  private Long replayTime;
  private String instanceId;
  private String baseMsg;
  private String testMsg;
  private String logs;
  private Boolean ignore;
  private Integer diffResultCode;
  private Long dataCreateTime;
  private String msgInfo;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
