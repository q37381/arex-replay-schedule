package com.arextest.schedule.dao;

import com.arextest.schedule.model.ReplayActionCaseItem;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.Data;

/**
 * ReplayRunDetails store. Implementations: mongodb.ReplayActionCaseItemMongoRepository
 * (legacy) and mysql.ReplayRunDetailsMysqlRepository, switched by
 * arex.schedule.repository.type.
 */
public interface ReplayActionCaseItemRepository extends RepositoryWriter<ReplayActionCaseItem> {

  /**
   * @return case list that need to be sent, ordered ascending by record time.
   * The order controls the sequence of extra mockers yields during replay;
   * do not change without discussing with the agent team.
   */
  List<ReplayActionCaseItem> waitingSendList(String planId, int pageSize,
      CaseQueryCondition condition, Long minRecordTime);

  /**
   * Get the case list that failed to send or compare.
   */
  List<ReplayActionCaseItem> failedCaseList(String planId, String planItemId);

  Map<String, Long> countWaitHandlingByAction(String planId, CaseQueryCondition condition);

  boolean updateSendResult(ReplayActionCaseItem replayActionCaseItem);

  boolean updateCompareStatus(String id, int comparedStatus);

  void batchUpdateStatus(List<ReplayActionCaseItem> replayActionCaseItemList);

  /**
   * Get the number of recorded cases and the earliest record time, grouped by case type.
   */
  List<GroupCountRes> getLastRecord(String planItemId);

  Set<String> getAllContextIdentifiers(String planId);

  boolean hasNullIdentifier(String planId);

  ReplayActionCaseItem getOneOfContext(String planId, String contextIdentifier);

  ReplayActionCaseItem queryById(String caseId);

  List<ReplayActionCaseItem> batchQueryById(List<String> caseIdList);

  boolean deleteExcludedCases(String planId, List<String> planItemIds);

  long updateCaseStatus(String recordId, Integer caseStatus);

  @Data
  class GroupCountRes {

    private String planItemId;
    private Long count;
    private Long lastRecordTime;
    private String caseType;
  }
}
