// 게시글 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.Post;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostJpaRepository extends JpaRepository<Post, UUID> {

    List<Post> findByUserId(UUID userId);

    List<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    // 제목 또는 본문에 키워드가 포함된 게시글 검색 (대소문자 무시)
    List<Post> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCaseOrderByCreatedAtDesc(
            String title, String content, Pageable pageable);
}
