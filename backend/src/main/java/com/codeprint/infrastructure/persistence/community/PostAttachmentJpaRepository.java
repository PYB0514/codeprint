// 게시글 첨부파일 JPA 레포지토리
package com.codeprint.infrastructure.persistence.community;

import com.codeprint.domain.community.PostAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PostAttachmentJpaRepository extends JpaRepository<PostAttachment, UUID> {

    // 게시글 ID로 첨부파일 목록 조회 (등록 순)
    List<PostAttachment> findByPostIdOrderByCreatedAtAsc(UUID postId);
}
