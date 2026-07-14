// 팀 API 키 JPA 리포지토리
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamApiKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamApiKeyJpaRepository extends JpaRepository<TeamApiKey, UUID> {
    Optional<TeamApiKey> findByKeyHash(String keyHash);
    List<TeamApiKey> findByTeamId(UUID teamId);
}
