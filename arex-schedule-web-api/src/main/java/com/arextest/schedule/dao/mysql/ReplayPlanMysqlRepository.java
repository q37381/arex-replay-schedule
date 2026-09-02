package com.arextest.schedule.dao.mysql;

import com.arextest.schedule.dao.ReplayPlanRepository;
import com.arextest.schedule.model.ReplayPlan;
import com.arextest.schedule.model.converter.ReplayPlanConverter;
import com.arextest.schedule.model.dao.mongodb.ReplayPlanCollection;
import com.arextest.schedule.model.plan.ReplayPlanStageInfo;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * Mysql implementation of {@link ReplayPlanRepository}, active when
 * arex.schedule.repository.type=mysql (the default).
 *
 * Conversion chain mirrors the mongo edition: ReplayPlan <-> ReplayPlanCollection
 * (mapstruct, keeps the persisted field set identical) and ReplayPlanCollection
 * <-> ReplayPlanEntity (storage bridging: Date -> epoch millis, stage list ->
 * JSON column).
 */
@Repository
@RequiredArgsConstructor
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
public class ReplayPlanMysqlRepository implements ReplayPlanRepository {

  private final ReplayPlanMapper replayPlanMapper;

  @Override
  public boolean save(ReplayPlan replayPlan) {
    ReplayPlanCollection collection = ReplayPlanConverter.INSTANCE.daoFromDto(replayPlan);
    ReplayPlanEntity entity = toEntity(collection);
    entity.setId(MysqlJsonSupport.newId());
    boolean saved = replayPlanMapper.insert(entity) > 0;
    if (saved) {
      replayPlan.setId(entity.getId());
    }
    return saved;
  }

  @Override
  public boolean updateCaseTotal(String planId, int caseTotal) {
    return replayPlanMapper.updateCaseTotal(planId, caseTotal, System.currentTimeMillis()) > 0;
  }

  @Override
  public boolean finish(String planId) {
    return replayPlanMapper.finish(planId, System.currentTimeMillis()) > 0;
  }

  @Override
  public List<ReplayPlan> timeoutPlanList(Duration offsetDuration, Duration maxDuration) {
    long now = System.currentTimeMillis();
    long from = now - offsetDuration.toMillis();
    long to = from - maxDuration.toMillis();
    return replayPlanMapper.timeoutPlanList(to, from).stream()
        .map(ReplayPlanMysqlRepository::toCollection)
        .map(ReplayPlanConverter.INSTANCE::dtoFromDao)
        .collect(Collectors.toList());
  }

  @Override
  public void updateStage(ReplayPlan replayPlan) {
    replayPlanMapper.updateStage(replayPlan.getId(),
        MysqlJsonSupport.writeJson(replayPlan.getReplayPlanStageList()),
        System.currentTimeMillis());
  }

  @Override
  public ReplayPlan query(String planId) {
    ReplayPlanEntity entity = replayPlanMapper.query(planId);
    if (entity == null) {
      return null;
    }
    return ReplayPlanConverter.INSTANCE.dtoFromDao(toCollection(entity));
  }

  private static ReplayPlanEntity toEntity(ReplayPlanCollection collection) {
    ReplayPlanEntity entity = new ReplayPlanEntity();
    entity.setId(collection.getId());
    entity.setAppId(collection.getAppId());
    entity.setPlanName(collection.getPlanName());
    entity.setSourceEnv(collection.getSourceEnv());
    entity.setTargetEnv(collection.getTargetEnv());
    entity.setSourceHost(collection.getSourceHost());
    entity.setTargetHost(collection.getTargetHost());
    entity.setTargetImageId(collection.getTargetImageId());
    entity.setTargetImageName(collection.getTargetImageName());
    entity.setCaseSourceFrom(MysqlJsonSupport.toMillis(collection.getCaseSourceFrom()));
    entity.setCaseSourceTo(MysqlJsonSupport.toMillis(collection.getCaseSourceTo()));
    entity.setPlanCreateTime(MysqlJsonSupport.toMillis(collection.getPlanCreateTime()));
    entity.setPlanFinishTime(MysqlJsonSupport.toMillis(collection.getPlanFinishTime()));
    entity.setOperator(collection.getOperator());
    entity.setArexCordVersion(collection.getArexCordVersion());
    entity.setArexExtVersion(collection.getArexExtVersion());
    entity.setCaseRecordVersion(collection.getCaseRecordVersion());
    entity.setCaseTotalCount(collection.getCaseTotalCount());
    entity.setCaseSourceType(collection.getCaseSourceType());
    entity.setReplayPlanType(collection.getReplayPlanType());
    entity.setCaseCountLimit(collection.getCaseCountLimit());
    entity.setReplayPlanStageList(MysqlJsonSupport.writeJson(collection.getReplayPlanStageList()));
    entity.setDataChangeCreateTime(collection.getDataChangeCreateTime());
    entity.setDataChangeUpdateTime(collection.getDataChangeUpdateTime());
    return entity;
  }

  private static ReplayPlanCollection toCollection(ReplayPlanEntity entity) {
    ReplayPlanCollection collection = new ReplayPlanCollection();
    collection.setId(entity.getId());
    collection.setAppId(entity.getAppId());
    collection.setPlanName(entity.getPlanName());
    collection.setSourceEnv(entity.getSourceEnv());
    collection.setTargetEnv(entity.getTargetEnv());
    collection.setSourceHost(entity.getSourceHost());
    collection.setTargetHost(entity.getTargetHost());
    collection.setTargetImageId(entity.getTargetImageId());
    collection.setTargetImageName(entity.getTargetImageName());
    collection.setCaseSourceFrom(MysqlJsonSupport.toDate(entity.getCaseSourceFrom()));
    collection.setCaseSourceTo(MysqlJsonSupport.toDate(entity.getCaseSourceTo()));
    collection.setPlanCreateTime(MysqlJsonSupport.toDate(entity.getPlanCreateTime()));
    collection.setPlanFinishTime(MysqlJsonSupport.toDate(entity.getPlanFinishTime()));
    collection.setOperator(entity.getOperator());
    collection.setArexCordVersion(entity.getArexCordVersion());
    collection.setArexExtVersion(entity.getArexExtVersion());
    collection.setCaseRecordVersion(entity.getCaseRecordVersion());
    collection.setCaseTotalCount(
        entity.getCaseTotalCount() == null ? 0 : entity.getCaseTotalCount());
    collection.setCaseSourceType(
        entity.getCaseSourceType() == null ? 0 : entity.getCaseSourceType());
    collection.setReplayPlanType(
        entity.getReplayPlanType() == null ? 0 : entity.getReplayPlanType());
    collection.setCaseCountLimit(
        entity.getCaseCountLimit() == null ? 0 : entity.getCaseCountLimit());
    collection.setReplayPlanStageList(MysqlJsonSupport.readJson(entity.getReplayPlanStageList(),
        new TypeReference<List<ReplayPlanStageInfo>>() {
        }));
    collection.setDataChangeCreateTime(entity.getDataChangeCreateTime());
    collection.setDataChangeUpdateTime(entity.getDataChangeUpdateTime());
    return collection;
  }
}
