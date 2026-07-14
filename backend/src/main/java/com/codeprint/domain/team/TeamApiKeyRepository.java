// 팀 API 키 리포지토리 인터페이스
package com.codeprint.domain.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamApiKeyRepository {

    TeamApiKey save(TeamApiKey key);

    Optional<TeamApiKey> findById(UUID id);

    // 해시로 조회 — 인증 필터가 요청 헤더의 평문을 해시해 이 메서드로 대조
    Optional<TeamApiKey> findByKeyHash(String keyHash);

    List<TeamApiKey> findByTeamId(UUID teamId);
}
