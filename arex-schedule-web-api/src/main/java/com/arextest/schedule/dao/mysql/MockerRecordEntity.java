package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Entity mapping to arex_storage_db.mocker_record table.
 */
@Data
public class MockerRecordEntity {
  private String id;
  private String category;
  private String recordId;
  private String replayId;
  private String appId;
  private String operationName;
  private int recordEnv;
  private String recordVersion;
  private Boolean useMock;
  private Long creationTime;
  private Long updateTime;
  private Long expirationTime;
  private String tags;
  private String eigenMap;
  private String targetRequest;
  private String targetResponse;
}
