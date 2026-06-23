// 댓글 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Comment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface CommentJpaRepository extends JpaRepository<Comment, UUID> {

    List<Comment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    // 게시글 ID로 댓글 수 조회
    long countByPostId(UUID postId);

    // 여러 게시글의 댓글 수 일괄 조회 (GROUP BY) — [postId, count] 행 목록
    @Query("SELECT c.postId, COUNT(c) FROM Comment c WHERE c.postId IN :postIds GROUP BY c.postId")
    List<Object[]> countByPostIdIn(@Param("postIds") List<UUID> postIds);
}
