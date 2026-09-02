package com.arextest.schedule.dao;

import com.arextest.schedule.model.ReplayPlan;
import java.time.Duration;
import java.util.List;

/**
 * ReplayPlan store. Implementations: mongodb.ReplayPlanMongoRepository
 * (legacy) and mysql.ReplayPlanMysqlRepository, switched by
 * arex.schedule.repository.type.
 */
public interface ReplayPlanRepository {

  boolean save(ReplayPlan replayPlan);

  boolean updateCaseTotal(String planId, int caseTotal);

  boolean finish(String planId);

  List<ReplayPlan> timeoutPlanList(Duration offsetDuration, Duration maxDuration);

  void updateStage(ReplayPlan replayPlan);

  ReplayPlan query(String planId);
}
