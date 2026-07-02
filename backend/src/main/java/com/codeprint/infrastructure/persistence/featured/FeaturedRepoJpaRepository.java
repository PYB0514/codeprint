// 오늘의 공개레포 JPA Repository (Spring Data)
package com.codeprint.infrastructure.persistence.featured;

import com.codeprint.domain.featured.FeaturedRepo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FeaturedRepoJpaRepository extends JpaRepository<FeaturedRepo, UUID> {

    // 노출된 적 없거나(null) 가장 오래전에 노출된 순
    @Query(value = "select * from featured_repos order by last_featured_at asc nulls first limit :limit",
            nativeQuery = true)
    List<FeaturedRepo> findRotationCandidates(@Param("limit") int limit);

    // 이미 분석된(project_id 존재) 것 중 최근 노출된 순
    @Query(value = "select * from featured_repos where project_id is not null "
            + "order by last_featured_at desc limit :limit", nativeQuery = true)
    List<FeaturedRepo> findMostRecentlyFeatured(@Param("limit") int limit);
}
