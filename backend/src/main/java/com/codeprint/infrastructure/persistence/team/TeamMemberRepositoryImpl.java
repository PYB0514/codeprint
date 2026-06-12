// TeamMemberRepository JPA 구현체
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamMember;
import com.codeprint.domain.team.TeamMemberRepository;
import com.codeprint.domain.team.TeamRole;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class TeamMemberRepositoryImpl implements TeamMemberRepository {

    private final TeamMemberJpaRepository jpa;

    @Override public TeamMember save(TeamMember m) { return jpa.save(m); }
    @Override public void deleteById(UUID id) { jpa.deleteById(id); }
    @Override public List<TeamMember> findByTeamId(UUID teamId) { return jpa.findByTeamId(teamId); }
    @Override public List<TeamMember> findByUserId(UUID userId) { return jpa.findByUserId(userId); }
    @Override public Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId) { return jpa.findByTeamIdAndUserId(teamId, userId); }
    @Override public long countByTeamId(UUID teamId) { return jpa.countByTeamId(teamId); }
    @Override public long countMembersExcludingOwner(UUID teamId) { return jpa.countByTeamIdAndRoleNot(teamId, TeamRole.OWNER); }
}
