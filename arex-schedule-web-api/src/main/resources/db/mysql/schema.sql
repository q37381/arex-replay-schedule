-- =====================================================================
-- AREX Schedule MySQL schema
-- Replaces the MongoDB collections used by arex-replay-schedule.
-- Deployed into the shared arex_storage_db database, so the config tables
-- (config_application / config_system, created by arex-storage) are reused
-- directly for app auth and desensitization lookups.
--
-- All dates are stored as epoch millis (BIGINT), mirroring the storage
-- edition. Nested structures (stage lists, noise item maps, msgInfo) are
-- persisted as JSON text columns.
-- =====================================================================

-- ReplayPlan collection
CREATE TABLE IF NOT EXISTS `replay_plan` (
  `auto_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                      VARCHAR(64)  NOT NULL,
  `app_id`                  VARCHAR(128) NOT NULL,
  `plan_name`               VARCHAR(255) DEFAULT NULL,
  `source_env`              VARCHAR(64)  DEFAULT NULL,
  `target_env`              VARCHAR(64)  DEFAULT NULL,
  `source_host`             VARCHAR(512) DEFAULT NULL,
  `target_host`             VARCHAR(512) DEFAULT NULL,
  `target_image_id`         VARCHAR(64)  DEFAULT NULL,
  `target_image_name`       VARCHAR(255) DEFAULT NULL,
  `case_source_from`        BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `case_source_to`          BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `plan_create_time`        BIGINT       NOT NULL COMMENT 'epoch millis',
  `plan_finish_time`        BIGINT       DEFAULT NULL COMMENT 'epoch millis; NULL while running',
  `operator`                VARCHAR(128) DEFAULT NULL,
  `arex_cord_version`       VARCHAR(64)  DEFAULT NULL,
  `arex_ext_version`        VARCHAR(64)  DEFAULT NULL,
  `case_record_version`     VARCHAR(64)  DEFAULT NULL,
  `case_total_count`        INT          NOT NULL DEFAULT 0,
  `case_source_type`        INT          NOT NULL DEFAULT 0,
  `replay_plan_type`        INT          NOT NULL DEFAULT 0,
  `case_count_limit`        INT          NOT NULL DEFAULT 0,
  `replay_plan_stage_list`  TEXT         DEFAULT NULL COMMENT 'JSON array of ReplayPlanStageInfo',
  `data_change_create_time` BIGINT       DEFAULT NULL,
  `data_change_update_time` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  -- timeoutPlanList: unfinished plans inside a create-time window
  KEY `idx_finish_create` (`plan_finish_time`, `plan_create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ReplayPlanItem collection
CREATE TABLE IF NOT EXISTS `replay_plan_item` (
  `auto_id`                  BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                       VARCHAR(64)  NOT NULL,
  `app_id`                   VARCHAR(128) NOT NULL,
  `operation_id`             VARCHAR(64)  NOT NULL DEFAULT '',
  `plan_id`                  VARCHAR(64)  NOT NULL,
  `replay_case_count`        INT          NOT NULL DEFAULT 0,
  `noise_finished_contexts`  MEDIUMTEXT   DEFAULT NULL COMMENT 'JSON object: contextName -> count',
  `replay_status`            INT          NOT NULL DEFAULT 0,
  `replay_begin_time`        BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `replay_finish_time`       BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `data_change_create_time`  BIGINT       DEFAULT NULL,
  `data_change_update_time`  BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  KEY `idx_plan_id` (`plan_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ReplayRunDetails collection: the hot table, one row per case to send
CREATE TABLE IF NOT EXISTS `replay_run_details` (
  `auto_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                      VARCHAR(64)  NOT NULL,
  `plan_id`                 VARCHAR(64)  NOT NULL,
  `plan_item_id`            VARCHAR(64)  DEFAULT NULL,
  `operation_id`            VARCHAR(64)  DEFAULT NULL,
  `case_provider_code`      INT          NOT NULL DEFAULT 0,
  `record_id`               VARCHAR(64)  NOT NULL DEFAULT '',
  `target_result_id`        VARCHAR(64)  NOT NULL DEFAULT '',
  `source_result_id`        VARCHAR(64)  NOT NULL DEFAULT '',
  `context_identifier`      VARCHAR(255) DEFAULT NULL,
  `send_status`             INT          NOT NULL DEFAULT 0,
  `compare_status`          INT          NOT NULL DEFAULT 0,
  `case_type`               VARCHAR(64)  DEFAULT NULL,
  `record_time`             BIGINT       NOT NULL DEFAULT 0,
  `expiration_time`         BIGINT       NOT NULL DEFAULT 0,
  `target_request`          MEDIUMTEXT   DEFAULT NULL COMMENT 'zstd+encrypted request payload',
  `case_status`             INT          NOT NULL DEFAULT 0,
  `data_change_create_time` BIGINT       DEFAULT NULL,
  `data_change_update_time` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  -- waitingSendList: per-plan pending cases ordered by record time
  KEY `idx_plan_send_time` (`plan_id`, `send_status`, `record_time`),
  -- waitingSendList compare_status OR-branch / failedCaseList
  KEY `idx_plan_compare_time` (`plan_id`, `compare_status`, `record_time`),
  KEY `idx_plan_item` (`plan_item_id`),
  KEY `idx_record_id` (`record_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ReplayCompareResult collection
CREATE TABLE IF NOT EXISTS `replay_compare_result` (
  `auto_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                      VARCHAR(64)  NOT NULL,
  `plan_id`                 VARCHAR(64)  DEFAULT NULL,
  `plan_item_id`            VARCHAR(64)  DEFAULT NULL,
  `case_id`                 VARCHAR(64)  DEFAULT NULL,
  `operation_id`            VARCHAR(64)  DEFAULT NULL,
  `service_name`            VARCHAR(512) DEFAULT NULL,
  `category_name`           VARCHAR(64)  DEFAULT NULL,
  `operation_name`          VARCHAR(512) DEFAULT NULL,
  `replay_id`               VARCHAR(64)  DEFAULT NULL,
  `record_id`               VARCHAR(64)  DEFAULT NULL,
  `record_time`             BIGINT       NOT NULL DEFAULT 0,
  `replay_time`             BIGINT       NOT NULL DEFAULT 0,
  `instance_id`             VARCHAR(64)  DEFAULT NULL,
  `base_msg`                MEDIUMTEXT   DEFAULT NULL COMMENT 'zstd+encrypted',
  `test_msg`                MEDIUMTEXT   DEFAULT NULL COMMENT 'zstd+encrypted',
  `logs`                    MEDIUMTEXT   DEFAULT NULL COMMENT 'zstd serialized LogEntity array',
  `ignore`                  TINYINT(1)   DEFAULT NULL,
  `diff_result_code`        INT          NOT NULL DEFAULT 0,
  `data_create_time`        BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `msg_info`                MEDIUMTEXT   DEFAULT NULL COMMENT 'JSON object',
  `data_change_create_time` BIGINT       DEFAULT NULL,
  `data_change_update_time` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  KEY `idx_record_item` (`record_id`, `plan_item_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ReplayNoise collection. Unique key backs the mongo upsert semantics of
-- saveList; item maps are JSON objects keyed by fuzzy path and merged in a
-- transaction (SELECT ... FOR UPDATE) to keep the $inc accumulation atomic.
CREATE TABLE IF NOT EXISTS `replay_noise` (
  `auto_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                      VARCHAR(64)  NOT NULL,
  `plan_id`                 VARCHAR(64)  NOT NULL,
  `plan_item_id`            VARCHAR(64)  NOT NULL,
  `category_name`           VARCHAR(128) NOT NULL DEFAULT '',
  `operation_id`            VARCHAR(64)  DEFAULT NULL,
  `operation_name`          VARCHAR(512) NOT NULL DEFAULT '',
  `operation_name_hash`     BIGINT       NOT NULL DEFAULT 0 COMMENT 'SHA-256(operation_name) first 8 bytes',
  `may_ignore_items`        MEDIUMTEXT   DEFAULT NULL COMMENT 'JSON object: path -> ReplayNoiseItemDao',
  `may_disorder_items`      MEDIUMTEXT   DEFAULT NULL COMMENT 'JSON object: path -> ReplayNoiseItemDao',
  `data_change_create_date` BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `data_change_create_time` BIGINT       DEFAULT NULL,
  `data_change_update_time` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  UNIQUE KEY `uk_noise` (`plan_id`, `plan_item_id`, `category_name`, `operation_name_hash`),
  KEY `idx_plan_item` (`plan_item_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- ReplayBizLog collection. auto_id preserves the mongo _id ASC listing order.
CREATE TABLE IF NOT EXISTS `replay_biz_log` (
  `auto_id`                 BIGINT       NOT NULL AUTO_INCREMENT,
  `id`                      VARCHAR(64)  NOT NULL,
  `log_date`                BIGINT       DEFAULT NULL COMMENT 'epoch millis',
  `log_level`               INT          NOT NULL DEFAULT 0,
  `message`                 TEXT         DEFAULT NULL,
  `log_type`                INT          NOT NULL DEFAULT 0,
  `plan_id`                 VARCHAR(64)  DEFAULT NULL,
  `resumed_execution`       TINYINT(1)   NOT NULL DEFAULT 0,
  `context_name`            VARCHAR(255) DEFAULT NULL,
  `context_identifier`      VARCHAR(255) DEFAULT NULL,
  `case_item_id`            VARCHAR(64)  DEFAULT NULL,
  `action_item_id`          VARCHAR(64)  DEFAULT NULL,
  `operation_name`          VARCHAR(512) DEFAULT NULL,
  `exception`               MEDIUMTEXT   DEFAULT NULL,
  `request`                 MEDIUMTEXT   DEFAULT NULL,
  `response`                MEDIUMTEXT   DEFAULT NULL,
  `trace_id`                VARCHAR(128) DEFAULT NULL,
  `extra`                   TEXT         DEFAULT NULL,
  `re_run`                  TINYINT(1)   DEFAULT NULL,
  `data_change_create_time` BIGINT       DEFAULT NULL,
  `data_change_update_time` BIGINT       DEFAULT NULL,
  PRIMARY KEY (`auto_id`),
  UNIQUE KEY `uk_id` (`id`),
  KEY `idx_plan` (`plan_id`, `auto_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
