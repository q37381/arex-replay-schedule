package com.arextest.schedule.dao;

import com.arextest.schedule.model.bizlog.BizLog;
import com.arextest.schedule.model.bizlog.ReplayBizLogQueryCondition;
import com.arextest.schedule.model.dao.mongodb.ReplayBizLogCollection;
import java.util.Collection;
import java.util.List;

/**
 * ReplayBizLog store. Implementations: mongodb.ReplayBizLogMongoRepository
 * (legacy) and mysql.ReplayBizLogMysqlRepository, switched by
 * arex.schedule.repository.type.
 *
 * The query methods return the shared ReplayBizLogCollection POJO so callers
 * (PlanBizLogService) stay unchanged between backends.
 */
public interface ReplayBizLogRepository {

  void saveAll(Collection<BizLog> logs);

  List<ReplayBizLogCollection> queryByPlanId(String planId);

  List<ReplayBizLogCollection> queryByPlanId(String planId, ReplayBizLogQueryCondition condition);

  long countByPlanId(String planId, ReplayBizLogQueryCondition condition);
}
