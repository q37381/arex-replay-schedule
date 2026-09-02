package com.arextest.schedule.dao.mysql;

import com.arextest.model.replay.CaseStatusEnum;
import com.arextest.schedule.dao.CaseQueryCondition;
import com.arextest.schedule.dao.ReplayActionCaseItemRepository;
import com.arextest.schedule.model.CaseSendStatusType;
import com.arextest.schedule.model.CompareProcessStatusType;
import com.arextest.schedule.model.ReplayActionCaseItem;
import com.arextest.schedule.model.converter.ReplayRunDetailsConverter;
import com.arextest.schedule.model.dao.mongodb.ReplayRunDetailsCollection;
import jakarta.annotation.Resource;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

/**
 * Mysql implementation of {@link ReplayActionCaseItemRepository}, active when
 * arex.schedule.repository.type=mysql (the default).
 *
 * Conversion chain mirrors the mongo edition: ReplayActionCaseItem <->
 * ReplayRunDetailsCollection (mapstruct + zstd/encryption of the request
 * payload) and ReplayRunDetailsCollection <-> ReplayRunDetailsEntity
 * (1:1 field copy, all fields are already flat).
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ReplayRunDetailsMysqlRepository implements ReplayActionCaseItemRepository {

  private static final int INSERT_BATCH_SIZE = 500;

  @Resource
  private ReplayRunDetailsConverter converter;
  @Resource
  private ReplayRunDetailsMapper replayRunDetailsMapper;

  @Override
  public boolean save(ReplayActionCaseItem replayActionCaseItem) {
    ReplayRunDetailsCollection collection = converter.daoFromDto(replayActionCaseItem);
    ReplayRunDetailsEntity entity = toEntity(collection);
    entity.setId(MysqlJsonSupport.newId());
    boolean saved = replayRunDetailsMapper.insert(entity) > 0;
    if (saved) {
      replayActionCaseItem.setId(entity.getId());
    }
    return saved;
  }

  @Override
  public boolean save(List<ReplayActionCaseItem> caseItems) {
    if (CollectionUtils.isEmpty(caseItems)) {
      return true;
    }
    List<ReplayRunDetailsEntity> entities = caseItems.stream()
        .map(converter::daoFromDto)
        .map(ReplayRunDetailsMysqlRepository::toEntity)
        .peek(entity -> entity.setId(MysqlJsonSupport.newId()))
        .collect(Collectors.toList());

    // multi-row INSERT carrying MEDIUMTEXT payloads can exceed
    // max_allowed_packet; commit in bounded chunks like the mongo bulk ops
    int inserted = 0;
    for (List<ReplayRunDetailsEntity> chunk : ListUtils.partition(entities, INSERT_BATCH_SIZE)) {
      inserted += replayRunDetailsMapper.insertBatch(chunk);
    }
    if (inserted != caseItems.size()) {
      LOGGER.error("Error saving case items, save size does not match source.");
      return false;
    }
    for (int i = 0; i < entities.size(); i++) {
      caseItems.get(i).setId(entities.get(i).getId());
    }
    return true;
  }

  @Override
  public List<ReplayActionCaseItem> waitingSendList(String planId, int pageSize,
      CaseQueryCondition condition, Long minRecordTime) {
    boolean nullContext = condition != null && condition.isNullContextIdentifier();
    String contextIdentifier =
        condition == null || nullContext ? null : condition.getContextIdentifier();
    List<String> planItemIds =
        condition == null || CollectionUtils.isEmpty(condition.getPlanItemIds()) ? null
            : condition.getPlanItemIds();

    return replayRunDetailsMapper.waitingSendList(planId, pageSize, nullContext, contextIdentifier,
            planItemIds, minRecordTime, CaseSendStatusType.WAIT_HANDLING.getValue(),
            CompareProcessStatusType.WAIT_HANDLING.getValue())
        .stream()
        .map(ReplayRunDetailsMysqlRepository::toCollection)
        .map(converter::dtoFromDao)
        .collect(Collectors.toList());
  }

  @Override
  public List<ReplayActionCaseItem> failedCaseList(String planId, String planItemId) {
    return replayRunDetailsMapper.failedCaseList(planId,
            StringUtils.hasText(planItemId) ? planItemId : null,
            CaseSendStatusType.SUCCESS.getValue(), CompareProcessStatusType.PASS.getValue(),
            CaseStatusEnum.NORMAL.getCode(), System.currentTimeMillis())
        .stream()
        .map(ReplayRunDetailsMysqlRepository::toCollection)
        .map(converter::dtoFromDao)
        .collect(Collectors.toList());
  }

  @Override
  public Map<String, Long> countWaitHandlingByAction(String planId, CaseQueryCondition condition) {
    boolean nullContext = condition != null && condition.isNullContextIdentifier();
    String contextIdentifier =
        condition == null || nullContext ? null : condition.getContextIdentifier();
    List<String> planItemIds =
        condition == null || CollectionUtils.isEmpty(condition.getPlanItemIds()) ? null
            : condition.getPlanItemIds();

    return replayRunDetailsMapper.countWaitHandlingByAction(planId, nullContext, contextIdentifier,
            planItemIds, CaseSendStatusType.WAIT_HANDLING.getValue())
        .stream()
        .collect(Collectors.toMap(GroupCountRes::getPlanItemId, GroupCountRes::getCount));
  }

  @Override
  public boolean updateSendResult(ReplayActionCaseItem replayActionCaseItem) {
    if (replayActionCaseItem.getSourceResultId() == null
        || replayActionCaseItem.getTargetResultId() == null) {
      throw new RuntimeException("update parameter is null");
    }
    ReplayRunDetailsEntity entity = new ReplayRunDetailsEntity();
    entity.setId(replayActionCaseItem.getId());
    entity.setSendStatus(replayActionCaseItem.getSendStatus());
    entity.setSourceResultId(replayActionCaseItem.getSourceResultId());
    entity.setTargetResultId(replayActionCaseItem.getTargetResultId());
    return replayRunDetailsMapper.updateSendResult(entity, System.currentTimeMillis()) > 0;
  }

  @Override
  public boolean updateCompareStatus(String id, int comparedStatus) {
    return replayRunDetailsMapper.updateCompareStatus(id, comparedStatus,
        System.currentTimeMillis()) > 0;
  }

  @Override
  public void batchUpdateStatus(List<ReplayActionCaseItem> replayActionCaseItemList) {
    if (CollectionUtils.isEmpty(replayActionCaseItemList)) {
      return;
    }
    long now = System.currentTimeMillis();
    for (ReplayActionCaseItem caseItem : replayActionCaseItemList) {
      ReplayRunDetailsEntity entity = new ReplayRunDetailsEntity();
      entity.setId(caseItem.getId());
      entity.setSendStatus(caseItem.getSendStatus());
      entity.setCompareStatus(caseItem.getCompareStatus());
      replayRunDetailsMapper.updateStatus(entity, now);
    }
  }

  @Override
  public List<GroupCountRes> getLastRecord(String planItemId) {
    return replayRunDetailsMapper.getLastRecord(planItemId);
  }

  // region <context>
  @Override
  public Set<String> getAllContextIdentifiers(String planId) {
    return new HashSet<>(replayRunDetailsMapper.getAllContextIdentifiers(planId,
        CaseSendStatusType.SUCCESS.getValue(), CompareProcessStatusType.PASS.getValue()));
  }

  @Override
  public boolean hasNullIdentifier(String planId) {
    return replayRunDetailsMapper.hasNullIdentifier(planId, CaseSendStatusType.SUCCESS.getValue(),
        CompareProcessStatusType.PASS.getValue()) != null;
  }

  // get one mocker of the given context
  @Override
  public ReplayActionCaseItem getOneOfContext(String planId, String contextIdentifier) {
    ReplayRunDetailsEntity entity = replayRunDetailsMapper.getOneOfContext(planId,
        contextIdentifier);
    return entity == null ? null : converter.dtoFromDao(toCollection(entity));
  }

  @Override
  public ReplayActionCaseItem queryById(String caseId) {
    ReplayRunDetailsEntity entity = replayRunDetailsMapper.queryById(caseId);
    return entity == null ? null : converter.dtoFromDao(toCollection(entity));
  }

  @Override
  public List<ReplayActionCaseItem> batchQueryById(List<String> caseIdList) {
    if (CollectionUtils.isEmpty(caseIdList)) {
      return java.util.Collections.emptyList();
    }
    return replayRunDetailsMapper.batchQueryById(caseIdList).stream()
        .map(ReplayRunDetailsMysqlRepository::toCollection)
        .map(converter::dtoFromDao)
        .collect(Collectors.toList());
  }

  @Override
  public boolean deleteExcludedCases(String planId, List<String> planItemIds) {
    if (CollectionUtils.isEmpty(planItemIds)) {
      return false;
    }
    return replayRunDetailsMapper.deleteExcludedCases(planId, planItemIds) > 0;
  }

  @Override
  public long updateCaseStatus(String recordId, Integer caseStatus) {
    return replayRunDetailsMapper.updateCaseStatus(recordId, caseStatus,
        System.currentTimeMillis());
  }

  // endregion <context>

  private static ReplayRunDetailsEntity toEntity(ReplayRunDetailsCollection collection) {
    ReplayRunDetailsEntity entity = new ReplayRunDetailsEntity();
    entity.setId(collection.getId());
    entity.setPlanId(collection.getPlanId());
    entity.setPlanItemId(collection.getPlanItemId());
    entity.setOperationId(collection.getOperationId());
    entity.setCaseProviderCode(collection.getCaseProviderCode());
    entity.setRecordId(collection.getRecordId());
    entity.setTargetResultId(collection.getTargetResultId());
    entity.setSourceResultId(collection.getSourceResultId());
    entity.setContextIdentifier(collection.getContextIdentifier());
    entity.setSendStatus(collection.getSendStatus());
    entity.setCompareStatus(collection.getCompareStatus());
    entity.setCaseType(collection.getCaseType());
    entity.setRecordTime(collection.getRecordTime());
    entity.setExpirationTime(collection.getExpirationTime());
    entity.setTargetRequest(collection.getTargetRequest());
    entity.setCaseStatus(collection.getCaseStatus());
    entity.setDataChangeCreateTime(collection.getDataChangeCreateTime());
    entity.setDataChangeUpdateTime(collection.getDataChangeUpdateTime());
    return entity;
  }

  private static ReplayRunDetailsCollection toCollection(ReplayRunDetailsEntity entity) {
    ReplayRunDetailsCollection collection = new ReplayRunDetailsCollection();
    collection.setId(entity.getId());
    collection.setPlanId(entity.getPlanId());
    collection.setPlanItemId(entity.getPlanItemId());
    collection.setOperationId(entity.getOperationId());
    collection.setCaseProviderCode(
        entity.getCaseProviderCode() == null ? 0 : entity.getCaseProviderCode());
    collection.setRecordId(entity.getRecordId());
    collection.setTargetResultId(entity.getTargetResultId());
    collection.setSourceResultId(entity.getSourceResultId());
    collection.setContextIdentifier(entity.getContextIdentifier());
    collection.setSendStatus(entity.getSendStatus() == null ? 0 : entity.getSendStatus());
    collection.setCompareStatus(entity.getCompareStatus() == null ? 0 : entity.getCompareStatus());
    collection.setCaseType(entity.getCaseType());
    collection.setRecordTime(entity.getRecordTime() == null ? 0L : entity.getRecordTime());
    collection.setExpirationTime(
        entity.getExpirationTime() == null ? 0L : entity.getExpirationTime());
    collection.setTargetRequest(entity.getTargetRequest());
    collection.setCaseStatus(entity.getCaseStatus() == null ? 0 : entity.getCaseStatus());
    collection.setDataChangeCreateTime(entity.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(entity.getDataChangeUpdateTime());
    return collection;
  }
}
