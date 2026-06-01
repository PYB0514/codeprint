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
}
