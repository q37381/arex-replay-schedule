package com.arextest.schedule.dao.mongodb;

import com.arextest.config.model.dao.config.SystemConfigurationCollection;
import com.arextest.schedule.dao.SystemConfigurationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Mongo implementation of {@link SystemConfigurationRepository}, active when
 * arex.schedule.repository.type=mongodb.
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mongodb")
public class SystemConfigurationMongoRepository implements SystemConfigurationRepository {

  @Autowired
  MongoTemplate mongoTemplate;

  @Override
  public SystemConfigurationCollection queryByKey(String key) {
    Query query = new Query(
        Criteria.where(SystemConfigurationCollection.Fields.key).is(key));
    return mongoTemplate.findOne(query, SystemConfigurationCollection.class,
        SystemConfigurationCollection.DOCUMENT_NAME);
  }
}
