package com.arextest.schedule.model.converter;

import com.arextest.model.mock.AREXMocker;
import com.arextest.model.mock.Mocker;
import com.arextest.schedule.dao.mysql.MockerRecordEntity;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

/**
 * Converter from storage's MockerRecordEntity to AREXMocker.
 */
@Slf4j
@Component
public class MockerRecordConverter {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final TypeReference<Map<String, String>> STRING_MAP_TYPE_REF =
      new TypeReference<Map<String, String>>() {};

  public AREXMocker toMocker(MockerRecordEntity entity) {
    if (entity == null) {
      return null;
    }

    AREXMocker mocker = new AREXMocker();
    mocker.setId(entity.getId());
    mocker.setCategoryType(com.arextest.model.mock.MockCategoryType.createEntryPoint(entity.getCategory()));
    mocker.setRecordId(entity.getRecordId());
    mocker.setReplayId(entity.getReplayId());
    mocker.setAppId(entity.getAppId());
    mocker.setOperationName(entity.getOperationName());
    mocker.setRecordEnvironment(entity.getRecordEnv());
    mocker.setRecordVersion(entity.getRecordVersion());
    mocker.setUseMock(entity.getUseMock());
    mocker.setCreationTime(entity.getCreationTime());

    // Parse target request
    if (StringUtils.isNotBlank(entity.getTargetRequest())) {
      try {
        Mocker.Target request = OBJECT_MAPPER.readValue(entity.getTargetRequest(), Mocker.Target.class);
        mocker.setTargetRequest(request);
      } catch (Exception e) {
        LOGGER.warn("Failed to parse target_request for id: {}", entity.getId(), e);
      }
    }

    // Parse target response
    if (StringUtils.isNotBlank(entity.getTargetResponse())) {
      try {
        Mocker.Target response = OBJECT_MAPPER.readValue(entity.getTargetResponse(), Mocker.Target.class);
        mocker.setTargetResponse(response);
      } catch (Exception e) {
        LOGGER.warn("Failed to parse target_response for id: {}", entity.getId(), e);
      }
    }

    // Parse tags (Map<String, String>)
    if (StringUtils.isNotBlank(entity.getTags())) {
      try {
        Map<String, String> tags = OBJECT_MAPPER.readValue(entity.getTags(), STRING_MAP_TYPE_REF);
        mocker.setTags(tags);
      } catch (Exception e) {
        LOGGER.warn("Failed to parse tags for id: {}", entity.getId(), e);
      }
    }

    return mocker;
  }
}
