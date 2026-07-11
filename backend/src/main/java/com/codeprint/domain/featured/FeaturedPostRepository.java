// 오늘의 공개레포 통합 게시글 postId 저장소 (싱글톤)
package com.codeprint.domain.featured;

import java.util.Optional;
import java.util.UUID;

public interface FeaturedPostRepository {

    // 저장된 postId 조회 — 최초 실행 전이면 empty
    Optional<UUID> findPostId();

    // postId 저장(최초 생성 시 1회)
    void savePostId(UUID postId);
}
