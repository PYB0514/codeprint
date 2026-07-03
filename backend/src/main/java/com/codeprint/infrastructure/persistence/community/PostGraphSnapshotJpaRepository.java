// 게시글 그래프 스냅샷 JPA 레포지토리
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostGraphSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostGraphSnapshotJpaRepository extends JpaRepository<PostGraphSnapshot, UUID> {

    // 게시글 ID로 스냅샷 목록 조회 (노출 순)
    List<PostGraphSnapshot> findByPostIdOrderByPositionAsc(UUID postId);
}
