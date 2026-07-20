// 팀 좌석 원자적 증분 통합 테스트 — 실 Postgres로 @Modifying UPDATE 쿼리 자체를 검증(팀 결제 TOCTOU 수정 회귀 방지)
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.Team;
import com.codeprint.shared.plan.UserPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// ddl-auto=validate를 강제해, Flyway가 만든 스키마와 @Entity 매핑이 어긋나면 컨텍스트 로드 자체가 실패한다
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
class TeamSeatIncrementIntegrationTest {

    @Autowired
    private TeamJpaRepository teamJpa;

    @Autowired
    private TestEntityManager entityManager;

    // teams.owner_user_id는 users FK — 시딩된 시스템 계정(V49__add_featured_repos.sql)을 재사용해 신규 유저 생성 없이 검증
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    @Test
    @DisplayName("incrementSeats — 조회 없는 UPDATE로 좌석 수가 증분만큼 증가한다")
    void incrementSeats_addsDeltaToTotalSeats() {
        Team team = Team.create(SYSTEM_USER_ID, "team", UserPlan.DESKTOP, 5);
        teamJpa.save(team);
        entityManager.flush();
        entityManager.clear();

        teamJpa.incrementSeats(team.getId(), 3);
        entityManager.flush();
        entityManager.clear();

        Team reloaded = teamJpa.findById(team.getId()).orElseThrow();
        assertThat(reloaded.getTotalSeats()).isEqualTo(8);
    }

    @Test
    @DisplayName("incrementSeats — 순서를 바꿔 두 번 연속 호출해도 각 증분이 누적된다(TOCTOU 회귀 방지)")
    void incrementSeats_twoSequentialCalls_bothAccumulate() {
        Team team = Team.create(SYSTEM_USER_ID, "team", UserPlan.DESKTOP, 5);
        teamJpa.save(team);
        entityManager.flush();
        entityManager.clear();

        // 절대치 지정(SET total_seats = N) 방식이었다면 두 번째 호출이 첫 번째 결과를 덮어썼을 상황 —
        // 원자적 증분(SET total_seats = total_seats + N)이라 호출 순서와 무관하게 둘 다 반영된다.
        teamJpa.incrementSeats(team.getId(), 3);
        teamJpa.incrementSeats(team.getId(), 5);
        entityManager.flush();
        entityManager.clear();

        Team reloaded = teamJpa.findById(team.getId()).orElseThrow();
        assertThat(reloaded.getTotalSeats()).isEqualTo(13);
    }
}
