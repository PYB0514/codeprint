// GraphCommandService.pinGraph 통합 테스트 — 실 Postgres로 clearPinnedSlot flush 순서 검증
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.application.graph;

import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.graph.Graph;
import com.codeprint.domain.project.Project;
import com.codeprint.infrastructure.persistence.analysis.AnalysisJpaRepository;
import com.codeprint.infrastructure.persistence.graph.EdgeJpaRepository;
import com.codeprint.infrastructure.persistence.graph.GraphJpaRepository;
import com.codeprint.infrastructure.persistence.graph.GraphRepositoryImpl;
import com.codeprint.infrastructure.persistence.graph.NodeJpaRepository;
import com.codeprint.infrastructure.persistence.project.ProjectJpaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ddl-auto=validate를 강제해, Flyway가 만든 스키마와 @Entity 매핑이 어긋나면 컨텍스트 로드 자체가 실패한다
// 트랜잭션 롤백(@DataJpaTest 기본)이라 실 DB에 잔여 데이터를 남기지 않는다.
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({GraphRepositoryImpl.class, GraphCommandService.class})
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/codeprint",
        "spring.datasource.username=postgres",
        "spring.datasource.password=1234",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.flyway.enabled=true",
        "spring.flyway.validate-on-migrate=false"
})
class GraphCommandServicePinIntegrationTest {

    @Autowired
    private ProjectJpaRepository projectJpa;

    @Autowired
    private AnalysisJpaRepository analysisJpa;

    @Autowired
    private GraphJpaRepository graphJpa;

    @Autowired
    private NodeJpaRepository nodeJpa; // GraphRepositoryImpl 생성자 의존성 — 직접 사용 안 함

    @Autowired
    private EdgeJpaRepository edgeJpa; // GraphRepositoryImpl 생성자 의존성 — 직접 사용 안 함

    @Autowired
    private GraphCommandService service;

    @Autowired
    private TestEntityManager entityManager;

    // projects.user_id는 users FK — 시딩된 시스템 계정(V49__add_featured_repos.sql)을 재사용
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // 다른 그래프가 이미 점유한 슬롯을 재고정해도 unique 제약 위반 없이 성공한다
    @Test
    @DisplayName("그래프 고정 — 다른 그래프가 점유한 슬롯을 재고정해도 unique 제약 위반 없이 성공한다")
    void pinGraph_slotOccupiedByOtherGraph_reassignsWithoutConstraintViolation() {
        Project project = Project.create(SYSTEM_USER_ID, "https://github.com/x/pin-test", "pin-test", null);
        projectJpa.save(project);
        AnalysisResult analysis = AnalysisResult.create(project.getId(), "main");
        analysisJpa.save(analysis);

        Graph graphA = service.createGraph(project.getId(), analysis.getId());
        Graph graphB = service.createGraph(project.getId(), analysis.getId());

        service.pinGraph(project.getId(), graphA.getId(), 3);
        entityManager.flush();
        entityManager.clear();

        service.pinGraph(project.getId(), graphB.getId(), 3);
        entityManager.flush();
        entityManager.clear();

        assertThat(graphJpa.findById(graphA.getId()).orElseThrow().getPinnedSlot()).isNull();
        assertThat(graphJpa.findById(graphB.getId()).orElseThrow().getPinnedSlot()).isEqualTo((short) 3);
    }
}
