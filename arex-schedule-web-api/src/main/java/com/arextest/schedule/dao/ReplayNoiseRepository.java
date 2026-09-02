package com.arextest.schedule.dao;

import com.arextest.schedule.model.noiseidentify.ReplayNoiseDto;
import com.arextest.schedule.model.noiseidentify.UpdateNoiseItem;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * ReplayNoise store. Implementations: mongodb.ReplayNoiseMongoRepository
 * (legacy) and mysql.ReplayNoiseMysqlRepository, switched by
 * arex.schedule.repository.type.
 */
public interface ReplayNoiseRepository {

  boolean saveList(List<ReplayNoiseDto> replayNoiseDtoList);

  boolean removeReplayNoise(List<String> planItemIds);

  List<ReplayNoiseDto> queryReplayNoise(@NotBlank String planId, String planItemId);

  boolean updateReplayNoiseStatus(List<UpdateNoiseItem> updateNoiseItems);
}
