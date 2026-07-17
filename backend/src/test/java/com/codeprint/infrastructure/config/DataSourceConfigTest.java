// DATABASE_URL 기반 수동 DataSource 빈에도 spring.datasource.hikari.* 설정이 바인딩되는지 검증
package com.codeprint.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class DataSourceConfigTest {

    // ★회귀 가드(2026-07-17): @ConfigurationProperties 누락으로 이 수동 생성 빈이 spring.datasource.hikari.*를
    // 전혀 안 읽어 프로덕션이 계속 HikariCP 기본값(pool 10·max-lifetime 30분)으로 동작하던 버그.
    @Test
    @DisplayName("DATABASE_URL 파싱 DataSource에도 spring.datasource.hikari.* 설정이 바인딩된다")
    void hikariProperties_bindToManualDataSource() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(DataSourceConfig.class)
                .withPropertyValues(
                        "DATABASE_URL=postgresql://appuser:secret@localhost:5432/testdb",
                        "spring.datasource.hikari.maximum-pool-size=5",
                        "spring.datasource.hikari.minimum-idle=2",
                        "spring.datasource.hikari.max-lifetime=120000"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(DataSource.class);
                    DataSource dataSource = context.getBean(DataSource.class);
                    assertThat(dataSource).isInstanceOf(HikariDataSource.class);

                    HikariDataSource hikari = (HikariDataSource) dataSource;
                    assertThat(hikari.getMaximumPoolSize()).isEqualTo(5);
                    assertThat(hikari.getMinimumIdle()).isEqualTo(2);
                    assertThat(hikari.getMaxLifetime()).isEqualTo(120000);
                    // URL 파싱 자체도 여전히 정상 동작하는지(회귀 아님) 함께 확인
                    assertThat(hikari.getUsername()).isEqualTo("appuser");
                    assertThat(hikari.getJdbcUrl()).isEqualTo("jdbc:postgresql://localhost:5432/testdb");
                });
    }

    @Test
    @DisplayName("DATABASE_URL이 없으면 이 빈은 생성되지 않는다(로컬 개발은 표준 자동설정 사용)")
    void noDatabaseUrl_beanNotCreated() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
                .withUserConfiguration(DataSourceConfig.class)
                .run(context -> assertThat(context).doesNotHaveBean(DataSource.class));
    }
}
