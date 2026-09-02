package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.ReplayCompareResultRepository;
import com.arextest.schedule.model.ReplayCompareResult;
import com.arextest.schedule.model.converter.ReplayCompareResultConverter;
import com.arextest.schedule.model.dao.mongodb.ReplayCompareMsgInfoCollection;
import com.arextest.schedule.model.dao.mongodb.ReplayCompareResultCollection;
import jakarta.annotation.Resource;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Mysql implementation of {@link ReplayCompareResultRepository}, active when
 * arex.schedule.repository.type=mysql (the default).
 *
 * Conversion chain mirrors the mongo edition: ReplayCompareResult <->
 * ReplayCompareResultCollection (mapstruct + zstd/encryption of the message
 * payloads) and ReplayCompareResultCollection <-> ReplayCompareResultEntity
 * (storage bridging: Date -> epoch millis, msgInfo -> JSON column).
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ReplayCompareResultMysqlRepository implements ReplayCompareResultRepository {

  private static final int INSERT_BATCH_SIZE = 500;

  @Resource
  private ReplayCompareResultConverter replayCompareResultConverter;
  @Resource
  private ReplayCompareResultMapper replayCompareResultMapper;

  @Override
  public boolean save(List<ReplayCompareResult> itemList) {
    if (CollectionUtils.isEmpty(itemList)) {
      return true;
    }
    List<ReplayCompareResultEntity> entities = itemList.stream()
        .map(replayCompareResultConverter::daoFromBo)
        .map(ReplayCompareResultMysqlRepository::toEntity)
        .peek(entity -> {
          if (entity.getId() == null) {
            entity.setId(MysqlJsonSupport.newId());
          }
        })
        .collect(Collectors.toList());
    // chunked to stay under max_allowed_packet with MEDIUMTEXT msg payloads
    for (List<ReplayCompareResultEntity> chunk : ListUtils.partition(entities,
        INSERT_BATCH_SIZE)) {
      replayCompareResultMapper.insertBatch(chunk);
    }
    for (int i = 0; i < entities.size(); i++) {
      itemList.get(i).setId(entities.get(i).getId());
    }
    return true;
  }

  @Override
  public boolean save(ReplayCompareResult item) {
    ReplayCompareResultCollection collection = replayCompareResultConverter.daoFromBo(item);
    ReplayCompareResultEntity entity = toEntity(collection);
    if (entity.getId() == null) {
      entity.setId(MysqlJsonSupport.newId());
    }
    replayCompareResultMapper.upsert(entity);
    return true;
  }

  @Override
  public boolean deleteByRecord(String recordId, String planItemId) {
    return replayCompareResultMapper.deleteByRecord(recordId, planItemId) > 0;
  }

  @Override
  public ReplayCompareResult queryCompareResultsById(String objectId) {
    ReplayCompareResultEntity entity = replayCompareResultMapper.queryById(objectId);
    if (entity == null) {
      return null;
    }
    return replayCompareResultConverter.boFromDao(toCollection(entity));
  }

  private static ReplayCompareResultEntity toEntity(ReplayCompareResultCollection collection) {
    ReplayCompareResultEntity entity = new ReplayCompareResultEntity();
    entity.setId(collection.getId());
    entity.setPlanId(collection.getPlanId());
    entity.setPlanItemId(collection.getPlanItemId());
    entity.setCaseId(collection.getCaseId());
    entity.setOperationId(collection.getOperationId());
    entity.setServiceName(collection.getServiceName());
    entity.setCategoryName(collection.getCategoryName());
    entity.setOperationName(collection.getOperationName());
    entity.setReplayId(collection.getReplayId());
    entity.setRecordId(collection.getRecordId());
    entity.setRecordTime(collection.getRecordTime());
    entity.setReplayTime(collection.getReplayTime());
    entity.setInstanceId(collection.getInstanceId());
    entity.setBaseMsg(collection.getBaseMsg());
    entity.setTestMsg(collection.getTestMsg());
    entity.setLogs(collection.getLogs());
    entity.setIgnore(collection.getIgnore());
    entity.setDiffResultCode(collection.getDiffResultCode());
    entity.setDataCreateTime(MysqlJsonSupport.toMillis(collection.getDataCreateTime()));
    entity.setMsgInfo(MysqlJsonSupport.writeJson(collection.getMsgInfo()));
    entity.setDataChangeCreateTime(collection.getDataChangeCreateTime());
    entity.setDataChangeUpdateTime(collection.getDataChangeUpdateTime());
    return entity;
  }

  private static ReplayCompareResultCollection toCollection(ReplayCompareResultEntity entity) {
    ReplayCompareResultCollection collection = new ReplayCompareResultCollection();
    collection.setId(entity.getId());
    collection.setPlanId(entity.getPlanId());
    collection.setPlanItemId(entity.getPlanItemId());
    collection.setCaseId(entity.getCaseId());
    collection.setOperationId(entity.getOperationId());
    collection.setServiceName(entity.getServiceName());
    collection.setCategoryName(entity.getCategoryName());
    collection.setOperationName(entity.getOperationName());
    collection.setReplayId(entity.getReplayId());
    collection.setRecordId(entity.getRecordId());
    collection.setRecordTime(entity.getRecordTime() == null ? 0L : entity.getRecordTime());
    collection.setReplayTime(entity.getReplayTime() == null ? 0L : entity.getReplayTime());
    collection.setInstanceId(entity.getInstanceId());
    collection.setBaseMsg(entity.getBaseMsg());
    collection.setTestMsg(entity.getTestMsg());
    collection.setLogs(entity.getLogs());
    collection.setIgnore(entity.getIgnore());
    collection.setDiffResultCode(
        entity.getDiffResultCode() == null ? 0 : entity.getDiffResultCode());
    collection.setDataCreateTime(MysqlJsonSupport.toDate(entity.getDataCreateTime()));
    collection.setMsgInfo(
        MysqlJsonSupport.readJson(entity.getMsgInfo(), ReplayCompareMsgInfoCollection.class));
    collection.setDataChangeCreateTime(entity.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(entity.getDataChangeUpdateTime());
    return collection;
  }
}
