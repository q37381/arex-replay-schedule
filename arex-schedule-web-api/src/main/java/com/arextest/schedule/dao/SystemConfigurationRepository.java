package com.arextest.schedule.dao;

import com.arextest.config.model.dao.config.SystemConfigurationCollection;

/**
 * Read-only access to the system config (mongo "SystemConfiguration"
 * collection / mysql config_system table owned by arex-storage). Used by
 * AppAuthAspectExecutor (auth switch) and the desensitization wiring
 * (desensitization jar url).
 */
public interface SystemConfigurationRepository {

  SystemConfigurationCollection queryByKey(String key);
}
