// 오늘의 공개레포 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.featured;

import com.codeprint.domain.featured.FeaturedRepo;
import com.codeprint.domain.featured.FeaturedRepoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class FeaturedRepoRepositoryImpl implements FeaturedRepoRepository {

    private final FeaturedRepoJpaRepository jpa;

    @Override
    public FeaturedRepo save(FeaturedRepo featuredRepo) {
        return jpa.save(featuredRepo);
    }

    @Override
    public List<FeaturedRepo> findRotationCandidates(int limit) {
        return jpa.findRotationCandidates(limit);
    }

    @Override
    public List<FeaturedRepo> findMostRecentlyFeatured(int limit) {
        return jpa.findMostRecentlyFeatured(limit);
    }
}
