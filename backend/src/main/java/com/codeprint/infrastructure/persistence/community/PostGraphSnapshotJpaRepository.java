// 게시글 그래프 스냅샷 JPA 레포지토리
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostGraphSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PostGraphSnapshotJpaRepository extends JpaRepository<PostGraphSnapshot, UUID> {

    // 게시글 ID로 스냅샷 목록 조회 (노출 순)
    List<PostGraphSnapshot> findByPostIdOrderByPositionAsc(UUID postId);

    // 게시글 ID로 스냅샷 전부 삭제
    void deleteByPostId(UUID postId);

    // 스냅샷을 가진 게시글 ID만 일괄 조회 (N+1 제거용 배치 존재 확인)
    @Query("SELECT DISTINCT s.postId FROM PostGraphSnapshot s WHERE s.postId IN :postIds")
    List<UUID> findDistinctPostIdsByPostIdIn(List<UUID> postIds);
}
