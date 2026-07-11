// 오늘의 공개레포 통합 게시글 postId 저장소 JPA 구현체
package com.codeprint.infrastructure.persistence.featured;

import com.codeprint.domain.featured.FeaturedDailyPost;
import com.codeprint.domain.featured.FeaturedPostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class FeaturedPostRepositoryImpl implements FeaturedPostRepository {

    private static final short SINGLETON_ID = 1;

    private final FeaturedDailyPostJpaRepository jpa;

    @Override
    public Optional<UUID> findPostId() {
        return jpa.findById(SINGLETON_ID).map(FeaturedDailyPost::getPostId);
    }

    @Override
    public void savePostId(UUID postId) {
        jpa.save(FeaturedDailyPost.of(postId));
    }
}
