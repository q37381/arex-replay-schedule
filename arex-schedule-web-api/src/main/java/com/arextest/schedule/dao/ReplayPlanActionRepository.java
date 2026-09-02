package com.arextest.schedule.dao;

import com.arextest.schedule.model.ReplayActionItem;
import java.util.List;

/**
 * ReplayPlanItem store. Implementations: mongodb.ReplayPlanActionMongoRepository
 * (legacy) and mysql.ReplayPlanItemMysqlRepository, switched by
 * arex.schedule.repository.type.
 */
public interface ReplayPlanActionRepository extends RepositoryWriter<ReplayActionItem> {

  boolean update(ReplayActionItem actionItem);

  List<ReplayActionItem> queryPlanActionList(String planId);

  boolean updateNoiseOfContextFinished(String id, String contextName, int count);

  boolean bulkUpdateNoiseFinishedContexts(List<ReplayActionItem> actionItems);
}
