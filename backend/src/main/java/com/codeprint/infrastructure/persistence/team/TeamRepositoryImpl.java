// TeamRepository JPA 구현체
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.Team;
import com.codeprint.domain.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamRepositoryImpl implements TeamRepository {

    private final TeamJpaRepository jpa;

    @Override public Team save(Team team) { return jpa.save(team); }
    @Override public Optional<Team> findById(UUID id) { return jpa.findById(id); }
    @Override public List<Team> findByOwnerUserId(UUID ownerUserId) { return jpa.findByOwnerUserId(ownerUserId); }
}
