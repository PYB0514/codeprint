// TeamProjectAllocationRepository JPA 구현체
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamProjectAllocation;
import com.codeprint.domain.team.TeamProjectAllocationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamProjectAllocationRepositoryImpl implements TeamProjectAllocationRepository {

    private final TeamProjectAllocationJpaRepository jpa;

    @Override public TeamProjectAllocation save(TeamProjectAllocation a) { return jpa.save(a); }
    @Override public void deleteById(UUID id) { jpa.deleteById(id); }
    @Override public List<TeamProjectAllocation> findByTeamId(UUID teamId) { return jpa.findByTeamId(teamId); }
    @Override public Optional<TeamProjectAllocation> findByTeamIdAndProjectId(UUID teamId, UUID projectId) { return jpa.findByTeamIdAndProjectId(teamId, projectId); }
    @Override public int sumAllocatedSeatsByTeamId(UUID teamId) { return jpa.sumAllocatedSeatsByTeamId(teamId); }
}
