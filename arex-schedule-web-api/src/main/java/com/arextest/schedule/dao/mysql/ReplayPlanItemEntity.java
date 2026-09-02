package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_plan_item table (mongo ReplayPlanItem
 * collection). noiseFinishedContexts is stored as a JSON object column.
 */
@Data
public class ReplayPlanItemEntity {

  private String id;
  private String appId;
  private String operationId;
  private String planId;
  private Integer replayCaseCount;
  private String noiseFinishedContexts;
  private Integer replayStatus;
  private Long replayBeginTime;
  private Long replayFinishTime;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
