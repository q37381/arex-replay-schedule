package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.ReplayPlanActionRepository;
import com.arextest.schedule.model.ReplayActionItem;
import com.arextest.schedule.model.converter.ReplayPlanItemConverter;
import com.arextest.schedule.model.dao.mongodb.ReplayPlanItemCollection;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mysql implementation of {@link ReplayPlanActionRepository}, active when
 * arex.schedule.repository.type=mysql (the default).
 *
 * Conversion chain mirrors the mongo edition: ReplayActionItem <->
 * ReplayPlanItemCollection (mapstruct) and ReplayPlanItemCollection <->
 * ReplayPlanItemEntity (storage bridging: Date -> epoch millis,
 * noiseFinishedContexts -> JSON text column). The mongo $inc on
 * noiseFinishedContexts.&lt;contextName&gt; is merged in Java inside a
 * SELECT ... FOR UPDATE transaction (same pattern as ReplayNoiseMysqlRepository),
 * so no MySQL JSON path function is used.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ReplayPlanItemMysqlRepository implements ReplayPlanActionRepository {

  private static final TypeReference<Map<String, Integer>> NOISE_CONTEXTS_TYPE =
      new TypeReference<Map<String, Integer>>() {
      };
  private static final int MAX_LOCK_ATTEMPTS = 3;

  private final ReplayPlanItemMapper replayPlanItemMapper;
  private final TransactionTemplate transactionTemplate;

  public ReplayPlanItemMysqlRepository(ReplayPlanItemMapper replayPlanItemMapper,
      PlatformTransactionManager transactionManager) {
    this.replayPlanItemMapper = replayPlanItemMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public boolean save(ReplayActionItem actionItem) {
    ReplayPlanItemCollection collection = ReplayPlanItemConverter.INSTANCE.daoFromDto(actionItem);
    ReplayPlanItemEntity entity = toEntity(collection);
    entity.setId(MysqlJsonSupport.newId());
    boolean saved = replayPlanItemMapper.insert(entity) > 0;
    if (saved) {
      actionItem.setId(entity.getId());
    }
    return saved;
  }

  @Override
  public boolean save(List<ReplayActionItem> actionItems) {
    if (CollectionUtils.isEmpty(actionItems)) {
      return true;
    }
    List<ReplayPlanItemEntity> entities = actionItems.stream()
        .map(ReplayPlanItemConverter.INSTANCE::daoFromDto)
        .map(ReplayPlanItemMysqlRepository::toEntity)
        .peek(entity -> entity.setId(MysqlJsonSupport.newId()))
        .collect(Collectors.toList());

    int inserted = replayPlanItemMapper.insertBatch(entities);
    if (inserted != actionItems.size()) {
      LOGGER.error("Error saving action items, save size does not match source.");
      return false;
    }
    for (int i = 0; i < entities.size(); i++) {
      actionItems.get(i).setId(entities.get(i).getId());
    }
    return true;
  }

  @Override
  public boolean update(ReplayActionItem actionItem) {
    ReplayPlanItemCollection collection = ReplayPlanItemConverter.INSTANCE.daoFromDto(actionItem);
    ReplayPlanItemEntity entity = toEntity(collection);
    entity.setId(actionItem.getId());
    return replayPlanItemMapper.update(entity, System.currentTimeMillis()) > 0;
  }

  @Override
  public List<ReplayActionItem> queryPlanActionList(String planId) {
    return replayPlanItemMapper.queryByPlanId(planId).stream()
        .map(ReplayPlanItemMysqlRepository::toCollection)
        .map(ReplayPlanItemConverter.INSTANCE::dtoFromDao)
        .collect(Collectors.toList());
  }

  @Override
  public boolean updateNoiseOfContextFinished(String id, String contextName, int count) {
    for (int attempt = 0; attempt < MAX_LOCK_ATTEMPTS; attempt++) {
      try {
        Boolean updated = transactionTemplate.execute(status -> {
          ReplayPlanItemEntity row = replayPlanItemMapper.selectNoiseForUpdate(id);
          if (row == null) {
            return false;
          }
          Map<String, Integer> read =
              MysqlJsonSupport.readJson(row.getNoiseFinishedContexts(), NOISE_CONTEXTS_TYPE);
          Map<String, Integer> contexts = read == null ? new HashMap<>() : read;
          contexts.merge(contextName, count, Integer::sum);
          return replayPlanItemMapper.updateNoiseFinishedContexts(id,
              MysqlJsonSupport.writeJson(contexts), System.currentTimeMillis()) > 0;
        });
        return Boolean.TRUE.equals(updated);
      } catch (DeadlockLoserDataAccessException e) {
        if (attempt == MAX_LOCK_ATTEMPTS - 1) {
          LOGGER.error("updateNoiseOfContextFinished deadlock, id: {}", id, e);
          return false;
        }
        LOGGER.warn("noise finished contexts update deadlock, retry {}", attempt + 1);
      }
    }
    return false;
  }

  @Override
  public boolean bulkUpdateNoiseFinishedContexts(List<ReplayActionItem> actionItems) {
    if (CollectionUtils.isEmpty(actionItems)) {
      return true;
    }
    try {
      long now = System.currentTimeMillis();
      for (ReplayActionItem actionItem : actionItems) {
        replayPlanItemMapper.updateNoiseFinishedContexts(actionItem.getId(),
            MysqlJsonSupport.writeJson(actionItem.getNoiseFinishedContexts()), now);
      }
    } catch (RuntimeException exception) {
      LOGGER.error("ReplayPlanItemMysqlRepository.bulkUpdateNoiseFinishedContexts error",
          exception);
      return false;
    }
    return true;
  }

  private static ReplayPlanItemEntity toEntity(ReplayPlanItemCollection collection) {
    ReplayPlanItemEntity entity = new ReplayPlanItemEntity();
    entity.setId(collection.getId());
    entity.setAppId(collection.getAppId());
    entity.setOperationId(collection.getOperationId());
    entity.setPlanId(collection.getPlanId());
    entity.setReplayCaseCount(collection.getReplayCaseCount());
    entity.setNoiseFinishedContexts(
        MysqlJsonSupport.writeJson(collection.getNoiseFinishedContexts()));
    entity.setReplayStatus(collection.getReplayStatus());
    entity.setReplayBeginTime(MysqlJsonSupport.toMillis(collection.getReplayBeginTime()));
    entity.setReplayFinishTime(MysqlJsonSupport.toMillis(collection.getReplayFinishTime()));
    entity.setDataChangeCreateTime(collection.getDataChangeCreateTime());
    entity.setDataChangeUpdateTime(collection.getDataChangeUpdateTime());
    return entity;
  }

  private static ReplayPlanItemCollection toCollection(ReplayPlanItemEntity entity) {
    ReplayPlanItemCollection collection = new ReplayPlanItemCollection();
    collection.setId(entity.getId());
    collection.setAppId(entity.getAppId());
    collection.setOperationId(entity.getOperationId());
    collection.setPlanId(entity.getPlanId());
    collection.setReplayCaseCount(
        entity.getReplayCaseCount() == null ? 0 : entity.getReplayCaseCount());
    collection.setNoiseFinishedContexts(
        MysqlJsonSupport.readJson(entity.getNoiseFinishedContexts(), NOISE_CONTEXTS_TYPE));
    collection.setReplayStatus(entity.getReplayStatus() == null ? 0 : entity.getReplayStatus());
    collection.setReplayBeginTime(MysqlJsonSupport.toDate(entity.getReplayBeginTime()));
    collection.setReplayFinishTime(MysqlJsonSupport.toDate(entity.getReplayFinishTime()));
    collection.setDataChangeCreateTime(entity.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(entity.getDataChangeUpdateTime());
    return collection;
  }
}
