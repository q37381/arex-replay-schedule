package com.arextest.schedule.dao;

import com.arextest.schedule.model.ReplayCompareResult;

/**
 * ReplayCompareResult store. Implementations:
 * mongodb.ReplayCompareResultMongoRepository (legacy) and
 * mysql.ReplayCompareResultMysqlRepository, switched by
 * arex.schedule.repository.type.
 */
public interface ReplayCompareResultRepository extends RepositoryWriter<ReplayCompareResult> {

  boolean deleteByRecord(String recordId, String planItemId);

  ReplayCompareResult queryCompareResultsById(String objectId);
}
