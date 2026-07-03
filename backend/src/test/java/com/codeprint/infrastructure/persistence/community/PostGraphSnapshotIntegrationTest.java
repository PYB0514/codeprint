// 게시글-그래프 스냅샷 CASCADE 삭제 통합 테스트 — 실 Postgres로 FK ON DELETE CASCADE 검증
// 로컬은 docker compose의 codeprint-db, CI는 ci.yml의 postgres 서비스에 접속(둘 다 postgres/1234/codeprint).
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Post;
import com.codeprint.domain.community.PostGraphSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.TestPropertySource;

import java.util.Map;
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
class PostGraphSnapshotIntegrationTest {

    @Autowired
    private PostJpaRepository postJpa;

    @Autowired
    private PostGraphSnapshotJpaRepository snapshotJpa;

    @Autowired
    private TestEntityManager entityManager;

    // posts.user_id는 users FK — 시딩된 시스템 계정(V49__add_featured_repos.sql)을 재사용해 신규 유저 생성 없이 검증
    private static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    // 게시글 삭제 시 첨부된 그래프 스냅샷도 FK CASCADE로 함께 삭제된다
    @Test
    @DisplayName("게시글 삭제 시 그래프 스냅샷도 CASCADE로 함께 삭제된다")
    void deletingPost_cascadesSnapshotDeletion() {
        Post post = Post.create(SYSTEM_USER_ID, null, "제목", "내용", null, null, null, null, null);
        postJpa.save(post);

        PostGraphSnapshot snapshot = PostGraphSnapshot.create(
                post.getId(), UUID.randomUUID(), UUID.randomUUID(),
                Map.of("layoutPreset", "layer"), 0);
        snapshotJpa.save(snapshot);

        assertThat(snapshotJpa.findByPostIdOrderByPositionAsc(post.getId())).hasSize(1);

        postJpa.deleteById(post.getId());
        entityManager.flush();
        entityManager.clear();

        assertThat(snapshotJpa.findByPostIdOrderByPositionAsc(post.getId())).isEmpty();
    }
}
