// 오늘의 공개레포 통합 게시글 postId JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.featured;

import com.codeprint.domain.featured.FeaturedDailyPost;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeaturedDailyPostJpaRepository extends JpaRepository<FeaturedDailyPost, Short> {
}
