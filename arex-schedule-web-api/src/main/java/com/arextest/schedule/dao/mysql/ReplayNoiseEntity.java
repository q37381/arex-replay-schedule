package com.arextest.schedule.dao.mysql;

import lombok.Data;

/**
 * Flat relational row of the replay_noise table (mongo ReplayNoise
 * collection). mayIgnoreItems / mayDisorderItems are stored as JSON object
 * columns keyed by fuzzy path.
 */
@Data
public class ReplayNoiseEntity {

  private String id;
  private String planId;
  private String planItemId;
  private String categoryName;
  private String operationId;
  private String operationName;
  private Long operationNameHash;
  private String mayIgnoreItems;
  private String mayDisorderItems;
  private Long dataChangeCreateDate;
  private Long dataChangeCreateTime;
  private Long dataChangeUpdateTime;
}
