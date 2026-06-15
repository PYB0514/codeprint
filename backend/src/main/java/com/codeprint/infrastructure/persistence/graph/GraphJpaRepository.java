// 그래프 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.Graph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GraphJpaRepository extends JpaRepository<Graph, UUID> {

    // 프로젝트 ID로 그래프 목록 조회
    List<Graph> findByProjectId(UUID projectId);

    // 프로젝트의 최신 그래프 조회
    Optional<Graph> findTopByProjectIdOrderByCreatedAtDesc(UUID projectId);

    // 같은 프로젝트의 지정 슬롯 고정을 해제 — 고정 덮어쓰기 시 unique 제약 충돌 방지(즉시 실행 후 영속성 컨텍스트 초기화)
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Graph g SET g.pinnedSlot = NULL WHERE g.projectId = :projectId AND g.pinnedSlot = :slot")
    void clearPinnedSlot(@Param("projectId") UUID projectId, @Param("slot") int slot);
}
