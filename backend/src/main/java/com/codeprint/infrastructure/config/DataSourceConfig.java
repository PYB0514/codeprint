// DATABASE_URL 환경변수를 파싱하여 JDBC DataSource를 구성하는 설정
package com.codeprint.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
@ConditionalOnExpression("!'${DATABASE_URL:}'.isEmpty()")
public class DataSourceConfig {

    @Value("${DATABASE_URL}")
    private String databaseUrl;

    // postgresql://user:password@host:port/db 형식을 파싱하여 DataSource 생성
    // ★버그 수정(2026-07-17): @ConfigurationProperties 누락으로 application.yml의
    // spring.datasource.hikari.*(minimum-idle·max-lifetime 등)가 이 수동 생성 빈에는 전혀
    // 바인딩되지 않아 프로덕션이 계속 HikariCP 기본값(pool 10·max-lifetime 30분)으로 동작 중이었다
    // (로컬은 DATABASE_URL이 없어 이 빈 자체가 비활성화되고 표준 자동설정이 대신 쓰여 정상으로 보였음).
    // Spring Boot 표준 패턴 — Bean 메서드에 @ConfigurationProperties를 붙이면 생성된 인스턴스에
    // 해당 prefix 설정을 세터로 바인딩해준다(HikariDataSource의 setMinimumIdle 등과 매칭).
    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource dataSource() throws Exception {
        String url = databaseUrl.startsWith("postgresql://")
                ? databaseUrl.replace("postgresql://", "http://")
                : databaseUrl.replace("jdbc:postgresql://", "http://");

        URI uri = new URI(url);
        String host = uri.getHost();
        int port = uri.getPort();
        String database = uri.getPath().replace("/", "");
        String[] userInfo = uri.getUserInfo() != null ? uri.getUserInfo().split(":") : new String[]{"", ""};
        String username = userInfo[0];
        String password = userInfo.length > 1 ? userInfo[1] : "";

        String jdbcUrl = String.format("jdbc:postgresql://%s:%d/%s", host, port, database);

        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(username);
        ds.setPassword(password);
        ds.setDriverClassName("org.postgresql.Driver");
        return ds;
    }
}
