// DATABASE_URL 환경변수를 파싱하여 JDBC DataSource를 구성하는 설정
package com.codeprint.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
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
    @Bean
    @Primary
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
