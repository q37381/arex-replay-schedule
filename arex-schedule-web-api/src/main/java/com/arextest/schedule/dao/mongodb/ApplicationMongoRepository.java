package com.arextest.schedule.dao.mongodb;

import com.arextest.config.model.dao.config.AppCollection;
import com.arextest.schedule.dao.ApplicationRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

/**
 * Mongo implementation of {@link ApplicationRepository}, active when
 * arex.schedule.repository.type=mongodb.
 *
 * @author wildeslam.
 * @create 2023/10/11 14:06
 */
@Slf4j
@Repository
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mongodb")
public class ApplicationMongoRepository implements ApplicationRepository {

  @Autowired
  MongoTemplate mongoTemplate;

  @Override
  public AppCollection query(String appId) {
    Query query = Query.query(Criteria.where(AppCollection.Fields.appId).is(appId));
    return mongoTemplate.findOne(query, AppCollection.class, AppCollection.DOCUMENT_NAME);
  }
}
