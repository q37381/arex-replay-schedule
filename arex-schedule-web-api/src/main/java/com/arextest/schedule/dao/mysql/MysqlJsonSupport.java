package com.arextest.schedule.dao.mysql;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Date;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Shared JSON (de)serialization and id/date helpers for the mysql backend.
 * Complex mongo sub-documents (stage lists, noise item maps, msgInfo) are
 * persisted as JSON text columns and converted here.
 */
@Slf4j
public final class MysqlJsonSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper()
      .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private MysqlJsonSupport() {
  }

  public static String writeJson(Object value) {
    if (value == null) {
      return null;
    }
    try {
      return MAPPER.writeValueAsString(value);
    } catch (Exception e) {
      LOGGER.warn("serialize field failed: {}", e.getMessage());
      return null;
    }
  }

  public static <T> T readJson(String json, Class<T> type) {
    if (StringUtils.isEmpty(json)) {
      return null;
    }
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      LOGGER.warn("deserialize field failed: {}", e.getMessage());
      return null;
    }
  }

  public static <T> T readJson(String json, TypeReference<T> type) {
    if (StringUtils.isEmpty(json)) {
      return null;
    }
    try {
      return MAPPER.readValue(json, type);
    } catch (Exception e) {
      LOGGER.warn("deserialize field failed: {}", e.getMessage());
      return null;
    }
  }

  public static Long toMillis(Date date) {
    return date == null ? null : date.getTime();
  }

  public static Date toDate(Long millis) {
    return millis == null ? null : new Date(millis);
  }

  /**
   * Mongo ObjectId-like unique id (32 hex chars) for rows inserted by the
   * mysql backend; the mongo edition used the driver generated ObjectId.
   */
  public static String newId() {
    return java.util.UUID.randomUUID().toString().replace("-", "");
  }

  /**
   * Stable 64-bit hash (first 8 bytes of SHA-256) for unique-key columns that
   * cannot use the full text because of InnoDB index length limits.
   */
  public static Long hash64(String value) {
    if (value == null) {
      return 0L;
    }
    try {
      byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
          .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
      long hash = 0;
      for (int i = 0; i < 8; i++) {
        hash = (hash << 8) | (digest[i] & 0xFF);
      }
      return hash;
    } catch (Exception e) {
      return (long) value.hashCode();
    }
  }

  /**
   * JSON path for a dynamic object key, escaping the characters that would
   * break the $."key" path syntax.
   */
  public static String jsonObjectPath(String key) {
    String escaped = key == null ? "" : key.replace("\\", "\\\\").replace("\"", "\\\"");
    return "$\"" + escaped + "\"";
  }
}
