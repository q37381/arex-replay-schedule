package com.arextest.schedule.beans;

import com.arextest.common.desensitization.DesensitizationProvider;
import com.arextest.extension.desensitization.DataDesensitization;
import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Relational (mysql) wiring, active when arex.schedule.repository.type=mysql
 * (the default). DataSourceAutoConfiguration is excluded application-wide (the
 * mongo backend must not require a database), so the mysql backend builds its
 * own pool. MybatisAutoConfiguration still applies: it activates on the
 * presence of this single DataSource bean and creates the SqlSessionFactory
 * for the mappers under com.arextest.schedule.dao.mysql.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "arex.schedule.repository.type", havingValue = "mysql",
    matchIfMissing = true)
@MapperScan("com.arextest.schedule.dao.mysql")
public class ScheduleMysqlConfiguration {

  @Bean
  public DataSource scheduleMysqlDataSource(
      @Value("${spring.datasource.url}") String url,
      @Value("${spring.datasource.username}") String username,
      @Value("${spring.datasource.password}") String password,
      @Value("${spring.datasource.driver-class-name}") String driverClassName) {
    HikariDataSource dataSource = new HikariDataSource();
    dataSource.setJdbcUrl(url);
    dataSource.setUsername(username);
    dataSource.setPassword(password);
    dataSource.setDriverClassName(driverClassName);
    // report rows actually changed, aligning update counts with mongo's
    // getModifiedCount() semantics used by the repository > 0 checks
    dataSource.addDataSourceProperty("useAffectedRows", "true");
    return dataSource;
  }

  /**
   * No remote desensitization jar in mysql mode, mirroring the storage
   * edition: DesensitizationProvider with a null jar url falls back to the
   * default implementation, so payloads encrypted by arex-storage(mysql)
   * decrypt consistently here.
   */
  @Bean
  @ConditionalOnMissingBean(DesensitizationProvider.class)
  public DesensitizationProvider scheduleMysqlDesensitizationProvider() {
    return new DesensitizationProvider(null);
  }

  @Bean
  @ConditionalOnMissingBean(DataDesensitization.class)
  public DataDesensitization scheduleMysqlDataDesensitization(
      DesensitizationProvider scheduleMysqlDesensitizationProvider) {
    return scheduleMysqlDesensitizationProvider.get();
  }
}
