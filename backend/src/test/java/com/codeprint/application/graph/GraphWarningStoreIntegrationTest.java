// graphs.warnings jsonb 매핑 통합 테스트 — 실 Postgres 왕복(숫자·중첩 리스트 보존), ddl-auto=validate로 스키마-매핑 일치 검증
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.application.graph;

import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.project.Project;
import com.codeprint.infrastructure.persistence.analysis.AnalysisJpaRepository;
import com.codeprint.infrastructure.persistence.graph.GraphJpaRepository;
import com.codeprint.infrastructure.persistence.project.ProjectJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ddl-auto=validate를 강제해, Flyway V69가 만든 warnings 컬럼과 @Entity 매핑이 어긋나면 컨텍스트 로드 자체가 실패한다.
// 트랜잭션 롤백(@DataJpaTest 기본)이라 실 DB에 잔여 데이터를 남기지 않는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/codeprint",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.validate-on-migrate=false"
})
class GraphWarningStoreIntegrationTest {

    @Autowired private ProjectJpaRepository projectJpa;
    @Autowired private AnalysisJpaRepository analysisJpa;
    @Autowired private GraphJpaRepository graphJpa;
    @Autowired private TestEntityManager entityManager;

    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private Graph persistGraph() {
        Project project = Project.create(SYSTEM_USER_ID, "https://github.com/x/warn-test", "warn-test", null);
        projectJpa.save(project);
        AnalysisResult analysis = AnalysisResult.create(project.getId(), "main");
        analysisJpa.save(analysis);
        return graphJpa.save(Graph.create(project.getId(), analysis.getId()));
    }

    @Test
    @DisplayName("warnings jsonb 왕복 — 문자열·숫자·중첩 리스트가 보존된다")
    void warningsColumn_jsonbRoundTrip() {
        Graph graph = persistGraph();
        graph.cacheWarnings(List.of(
                Map.of("type", "DEAD_CODE", "message", "미사용 함수", "line", 42, "nodeIds", List.of("n1", "n2")),
                Map.of("type", "HIGH_FAN_OUT", "message", "호출 8개", "line", 7)), (short) 1);
        graphJpa.save(graph);
        entityManager.flush();
        entityManager.clear();

        Graph reloaded = graphJpa.findById(graph.getId()).orElseThrow();
        assertThat(reloaded.getWarnings()).hasSize(2);
        assertThat(reloaded.getWarnings().get(0))
                .containsEntry("type", "DEAD_CODE")
                .containsEntry("line", 42); // 숫자가 문자열로 바뀌지 않음
        assertThat(reloaded.getWarnings().get(0).get("nodeIds")).asInstanceOf(
                org.assertj.core.api.InstanceOfAssertFactories.LIST).containsExactly("n1", "n2");
    }

    @Test
    @DisplayName("warnings 미설정 시 null, 빈 리스트 설정 시 빈 리스트로 구분되어 조회된다")
    void warningsColumn_nullVsEmpty() {
        Graph never = persistGraph();
        entityManager.flush();
        entityManager.clear();
        assertThat(graphJpa.findById(never.getId()).orElseThrow().getWarnings()).isNull();

        Graph emptied = persistGraph();
        emptied.cacheWarnings(List.of(), (short) 1);
        graphJpa.save(emptied);
        entityManager.flush();
        entityManager.clear();
        assertThat(graphJpa.findById(emptied.getId()).orElseThrow().getWarnings()).isEmpty();
    }

    @Test
    @DisplayName("clearWarnings 벌크 UPDATE — 프로젝트의 채워진 그래프만 warnings·version을 NULL로 되돌린다")
    void clearWarnings_bulkUpdate() {
        Graph graph = persistGraph();
        UUID projectId = graph.getProjectId();
        graph.cacheWarnings(List.of(Map.of("type", "X")), (short) 1);
        graphJpa.save(graph);
        entityManager.flush();
        entityManager.clear();

        graphJpa.clearWarnings(projectId);
        entityManager.clear();

        Graph reloaded = graphJpa.findById(graph.getId()).orElseThrow();
        assertThat(reloaded.getWarnings()).isNull();
        assertThat(reloaded.hasFreshWarnings((short) 1)).isFalse();
    }
}
