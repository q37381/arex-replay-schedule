package com.arextest.schedule.dao;

import com.arextest.config.model.dao.config.AppCollection;

/**
 * Read-only access to the application config (mongo "App" collection /
 * mysql config_application table owned by arex-storage). Used by
 * AppAuthAspectExecutor.
 */
public interface ApplicationRepository {

  AppCollection query(String appId);
}
