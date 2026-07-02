// 오늘의 공개레포 도메인 Repository 인터페이스
package com.codeprint.domain.featured;

import java.util.List;

public interface FeaturedRepoRepository {

    // 엔티티 저장
    FeaturedRepo save(FeaturedRepo featuredRepo);

    // 로테이션 대상 조회 — 노출된 적 없거나(null) 가장 오래전에 노출된 순으로 limit개
    List<FeaturedRepo> findRotationCandidates(int limit);

    // 랜딩페이지 노출용 — 이미 분석된(projectId 존재) 것 중 최근 노출된 순으로 limit개
    List<FeaturedRepo> findMostRecentlyFeatured(int limit);
}
