package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.ReplayNoiseRepository;
import com.arextest.schedule.model.converter.ReplayNoiseConverter;
import com.arextest.schedule.model.dao.mongodb.ReplayNoiseCollection;
import com.arextest.schedule.model.dao.mongodb.ReplayNoiseCollection.ReplayNoiseItemDao;
import com.arextest.schedule.model.noiseidentify.ReplayNoiseDto;
import com.arextest.schedule.model.noiseidentify.UpdateNoiseItem;
import com.arextest.schedule.utils.MapUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Mysql implementation of {@link ReplayNoiseRepository}, active when
 * arex.schedule.repository.type=mysql (the default).
 *
 * The mongo edition merges the noise item maps with per-key $set/$inc inside
 * an upsert. Here the two maps are JSON columns, so saveList runs a
 * read-merge-write cycle guarded by SELECT ... FOR UPDATE in a transaction;
 * the unique key uk_noise backs the insert side and a duplicate key triggers
 * a retry as merge-update (concurrent first insert). Each row runs in its own
 * transaction so a deadlock (InnoDB 1213 rolls back the whole transaction)
 * can be retried cleanly.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ReplayNoiseMysqlRepository implements ReplayNoiseRepository {

  private static final String MAY_IGNORE_ITEMS = ReplayNoiseCollection.Fields.MAY_IGNORE_ITEMS;
  private static final String STATUS_SUFFIX = "." + ReplayNoiseItemDao.Fields.STATUS;
  private static final int MAX_UPSERT_ATTEMPTS = 3;

  private final ReplayNoiseMapper replayNoiseMapper;
  private final TransactionTemplate transactionTemplate;

  public ReplayNoiseMysqlRepository(ReplayNoiseMapper replayNoiseMapper,
      PlatformTransactionManager transactionManager) {
    this.replayNoiseMapper = replayNoiseMapper;
    this.transactionTemplate = new TransactionTemplate(transactionManager);
  }

  @Override
  public boolean saveList(List<ReplayNoiseDto> replayNoiseDtoList) {
    if (CollectionUtils.isEmpty(replayNoiseDtoList)) {
      return true;
    }
    try {
      for (ReplayNoiseDto dto : replayNoiseDtoList) {
        saveOne(ReplayNoiseConverter.INSTANCE.daoFromDto(dto));
      }
    } catch (RuntimeException e) {
      LOGGER.error("ReplayNoiseRepository.saveList error", e);
      return false;
    }
    return true;
  }

  private void saveOne(ReplayNoiseCollection collection) {
    for (int attempt = 0; attempt < MAX_UPSERT_ATTEMPTS; attempt++) {
      try {
        transactionTemplate.executeWithoutResult(status -> upsertMerge(collection));
        return;
      } catch (DuplicateKeyException | DeadlockLoserDataAccessException e) {
        if (attempt == MAX_UPSERT_ATTEMPTS - 1) {
          throw e;
        }
        LOGGER.warn("noise upsert conflict ({}), retry {}", e.getClass().getSimpleName(),
            attempt + 1);
      }
    }
  }

  private void upsertMerge(ReplayNoiseCollection collection) {
    long now = System.currentTimeMillis();
    String categoryName = StringUtils.defaultString(collection.getCategoryName());
    String operationName = StringUtils.defaultString(collection.getOperationName());
    Long operationNameHash = MysqlJsonSupport.hash64(operationName);

    ReplayNoiseEntity existing = replayNoiseMapper.selectForUpdate(collection.getPlanId(),
        collection.getPlanItemId(), categoryName, operationNameHash, operationName);
    if (existing == null) {
      replayNoiseMapper.insert(newEntity(collection, categoryName, operationName,
          operationNameHash, now));
      return;
    }

    Map<String, ReplayNoiseItemDao> ignoreItems = readItems(existing.getMayIgnoreItems());
    Map<String, ReplayNoiseItemDao> disorderItems = readItems(existing.getMayDisorderItems());
    mergeItems(ignoreItems, collection.getMayIgnoreItems());
    mergeItems(disorderItems, collection.getMayDisorderItems());
    existing.setMayIgnoreItems(MysqlJsonSupport.writeJson(ignoreItems));
    existing.setMayDisorderItems(MysqlJsonSupport.writeJson(disorderItems));
    replayNoiseMapper.updateItems(existing, now);
  }

  /**
   * Mirrors the mongo edition's appendUpdate: nodePath / compareResultId /
   * logIndexes are overwritten, status is overwritten when present,
   * pathCount / caseCount / subPaths accumulate.
   */
  private static void mergeItems(Map<String, ReplayNoiseItemDao> target,
      Map<String, ReplayNoiseItemDao> source) {
    if (MapUtils.isEmpty(source)) {
      return;
    }
    for (Map.Entry<String, ReplayNoiseItemDao> entry : source.entrySet()) {
      ReplayNoiseItemDao src = entry.getValue();
      ReplayNoiseItemDao dest = target.get(entry.getKey());
      if (dest == null) {
        target.put(entry.getKey(), src);
        continue;
      }
      dest.setNodePath(src.getNodePath());
      dest.setCompareResultId(src.getCompareResultId());
      dest.setLogIndexes(src.getLogIndexes());
      if (src.getStatus() != null) {
        dest.setStatus(src.getStatus());
      }
      dest.setPathCount(nullToZero(dest.getPathCount()) + nullToZero(src.getPathCount()));
      dest.setCaseCount(nullToZero(dest.getCaseCount()) + nullToZero(src.getCaseCount()));
      if (MapUtils.isNotEmpty(src.getSubPaths())) {
        Map<String, Integer> subPaths =
            dest.getSubPaths() == null ? new HashMap<>() : dest.getSubPaths();
        src.getSubPaths().forEach((subPath, count) ->
            subPaths.merge(subPath, count, Integer::sum));
        dest.setSubPaths(subPaths);
      }
    }
  }

  @Override
  public boolean removeReplayNoise(List<String> planItemIds) {
    if (CollectionUtils.isEmpty(planItemIds)) {
      return true;
    }
    replayNoiseMapper.deleteByPlanItemIds(planItemIds);
    return true;
  }

  @Override
  public List<ReplayNoiseDto> queryReplayNoise(@NotBlank String planId, String planItemId) {
    return replayNoiseMapper
        .queryByPlan(planId, StringUtils.isEmpty(planItemId) ? null : planItemId)
        .stream()
        .map(ReplayNoiseMysqlRepository::toCollection)
        .map(ReplayNoiseConverter.INSTANCE::dtoFromDao)
        .collect(Collectors.toList());
  }

  /**
   * Mirrors the mongo edition: queryFields is an equality triple
   * (planId / operationId / categoryName in practice) and updateFields holds
   * dotted keys of the form mayIgnoreItems.&lt;identifier&gt;.status.
   */
  @Override
  public boolean updateReplayNoiseStatus(List<UpdateNoiseItem> updateNoiseItems) {
    try {
      for (UpdateNoiseItem updateNoiseItem : updateNoiseItems) {
        Map<String, Object> queryFields = updateNoiseItem.getQueryFields();
        Map<String, Object> updateFields = updateNoiseItem.getUpdateFields();
        if (MapUtils.isEmpty(queryFields) || MapUtils.isEmpty(updateFields)) {
          continue;
        }

        String planId = asString(queryFields.get(ReplayNoiseCollection.Fields.PLAN_ID));
        String planItemId = asString(queryFields.get(ReplayNoiseCollection.Fields.PLAN_ITEM_ID));
        String categoryName =
            asString(queryFields.get(ReplayNoiseCollection.Fields.CATEGORY_NAME));
        String operationId = asString(queryFields.get(ReplayNoiseCollection.Fields.OPERATION_ID));
        String operationName =
            asString(queryFields.get(ReplayNoiseCollection.Fields.OPERATION_NAME));
        updateStatusOne(planId, planItemId, categoryName, operationId, operationName,
            updateFields);
      }
    } catch (RuntimeException e) {
      LOGGER.error("ReplayNoiseRepository.updateReplayNoiseStatus error", e);
      return false;
    }
    return true;
  }

  private void updateStatusOne(String planId, String planItemId, String categoryName,
      String operationId, String operationName, Map<String, Object> updateFields) {
    for (int attempt = 0; attempt < MAX_UPSERT_ATTEMPTS; attempt++) {
      try {
        transactionTemplate.executeWithoutResult(status -> {
          long now = System.currentTimeMillis();
          List<ReplayNoiseEntity> rows = replayNoiseMapper.queryByFieldsForUpdate(planId,
              planItemId, categoryName, operationId, operationName);
          if (CollectionUtils.isEmpty(rows)) {
            return;
          }
          for (ReplayNoiseEntity row : rows) {
            if (applyStatusUpdates(row, updateFields)) {
              replayNoiseMapper.updateItems(row, now);
            }
          }
        });
        return;
      } catch (DeadlockLoserDataAccessException e) {
        if (attempt == MAX_UPSERT_ATTEMPTS - 1) {
          throw e;
        }
        LOGGER.warn("noise status update deadlock, retry {}", attempt + 1);
      }
    }
  }

  private static boolean applyStatusUpdates(ReplayNoiseEntity row,
      Map<String, Object> updateFields) {
    Map<String, ReplayNoiseItemDao> ignoreItems = null;
    boolean changed = false;
    for (Map.Entry<String, Object> entry : updateFields.entrySet()) {
      String key = entry.getKey();
      if (key == null || !key.startsWith(MAY_IGNORE_ITEMS + ".") || !key.endsWith(STATUS_SUFFIX)) {
        LOGGER.warn("unsupported noise update key: {}", key);
        continue;
      }
      String identifier =
          key.substring(MAY_IGNORE_ITEMS.length() + 1, key.length() - STATUS_SUFFIX.length());
      if (ignoreItems == null) {
        ignoreItems = readItems(row.getMayIgnoreItems());
      }
      // mongo $set on a missing path creates it; keep the same behavior
      ReplayNoiseItemDao itemDao =
          ignoreItems.computeIfAbsent(identifier, k -> new ReplayNoiseItemDao());
      itemDao.setStatus(entry.getValue() instanceof Number ? ((Number) entry.getValue()).intValue()
          : null);
      changed = true;
    }
    if (changed) {
      row.setMayIgnoreItems(MysqlJsonSupport.writeJson(ignoreItems));
    }
    return changed;
  }

  private static String asString(Object value) {
    return value == null ? null : String.valueOf(value);
  }

  private static int nullToZero(Integer value) {
    return value == null ? 0 : value;
  }

  private static Map<String, ReplayNoiseItemDao> readItems(String json) {
    Map<String, ReplayNoiseItemDao> items = MysqlJsonSupport.readJson(json,
        new TypeReference<Map<String, ReplayNoiseItemDao>>() {
        });
    return items == null ? new HashMap<>() : items;
  }

  private static ReplayNoiseEntity newEntity(ReplayNoiseCollection collection,
      String categoryName, String operationName, Long operationNameHash, long now) {
    ReplayNoiseEntity entity = new ReplayNoiseEntity();
    entity.setId(MysqlJsonSupport.newId());
    entity.setPlanId(collection.getPlanId());
    entity.setPlanItemId(collection.getPlanItemId());
    entity.setCategoryName(categoryName);
    entity.setOperationId(collection.getOperationId());
    entity.setOperationName(operationName);
    entity.setOperationNameHash(operationNameHash);
    entity.setMayIgnoreItems(MysqlJsonSupport.writeJson(collection.getMayIgnoreItems()));
    entity.setMayDisorderItems(MysqlJsonSupport.writeJson(collection.getMayDisorderItems()));
    entity.setDataChangeCreateDate(now);
    entity.setDataChangeCreateTime(now);
    entity.setDataChangeUpdateTime(now);
    return entity;
  }

  private static ReplayNoiseCollection toCollection(ReplayNoiseEntity entity) {
    ReplayNoiseCollection collection = new ReplayNoiseCollection();
    collection.setId(entity.getId());
    collection.setPlanId(entity.getPlanId());
    collection.setPlanItemId(entity.getPlanItemId());
    collection.setCategoryName(entity.getCategoryName());
    collection.setOperationId(entity.getOperationId());
    collection.setOperationName(entity.getOperationName());
    collection.setMayIgnoreItems(readItems(entity.getMayIgnoreItems()));
    collection.setMayDisorderItems(readItems(entity.getMayDisorderItems()));
    collection.setDataChangeCreateDate(MysqlJsonSupport.toDate(entity.getDataChangeCreateDate()));
    collection.setDataChangeCreateTime(entity.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(entity.getDataChangeUpdateTime());
    return collection;
  }
}
