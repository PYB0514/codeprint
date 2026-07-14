// TeamApiKeyRepository JPA 구현체
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamApiKey;
import com.codeprint.domain.team.TeamApiKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamApiKeyRepositoryImpl implements TeamApiKeyRepository {

    private final TeamApiKeyJpaRepository jpa;

    @Override public TeamApiKey save(TeamApiKey key) { return jpa.save(key); }
    @Override public Optional<TeamApiKey> findById(UUID id) { return jpa.findById(id); }
    @Override public Optional<TeamApiKey> findByKeyHash(String keyHash) { return jpa.findByKeyHash(keyHash); }
    @Override public List<TeamApiKey> findByTeamId(UUID teamId) { return jpa.findByTeamId(teamId); }
}
