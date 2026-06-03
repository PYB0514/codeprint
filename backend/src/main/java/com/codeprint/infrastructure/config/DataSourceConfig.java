// DATABASE_URL 환경변수의 postgresql:// 형식을 jdbc:postgresql://로 자동 변환하는 설정
package com.codeprint.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;

@Configuration
@ConditionalOnExpression("!'${DATABASE_URL:}'.isEmpty()")
public class DataSourceConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    // postgresql:// → jdbc:postgresql:// 변환 후 DataSource 생성
    @Bean
    @Primary
    public DataSource dataSource() {
        String jdbcUrl = databaseUrl.startsWith("postgresql://")
                ? "jdbc:" + databaseUrl
                : databaseUrl;

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
