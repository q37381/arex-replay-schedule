package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_run_details table (mongo ReplayRunDetails
 * collection). targetRequest keeps the zstd-compressed + encrypted payload
 * produced by ReplayRunDetailsConverter, exactly like the mongo edition.
 */
@Data
public class ReplayRunDetailsEntity {

  private String id;
  private String planId;
  private String planItemId;
  private String operationId;
  private Integer caseProviderCode;
  private String recordId;
  private String targetResultId;
  private String sourceResultId;
  private String contextIdentifier;
  private Integer sendStatus;
  private Integer compareStatus;
  private String caseType;
  private Long recordTime;
  private Long expirationTime;
  private String targetRequest;
  private Integer caseStatus;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
