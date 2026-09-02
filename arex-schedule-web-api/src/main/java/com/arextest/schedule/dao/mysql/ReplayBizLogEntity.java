package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_biz_log table (mongo ReplayBizLog
 * collection). The auto-increment seq preserves the mongo _id ASC listing
 * order used by the log query.
 */
@Data
public class ReplayBizLogEntity {

  private Long seq;
  private String id;
  private Long logDate;
  private Integer logLevel;
  private String message;
  private Integer logType;
  private String planId;
  private Boolean resumedExecution;
  private String contextName;
  private String contextIdentifier;
  private String caseItemId;
  private String actionItemId;
  private String operationName;
  private String exception;
  private String request;
  private String response;
  private String traceId;
  private String extra;
  private Boolean reRun;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
