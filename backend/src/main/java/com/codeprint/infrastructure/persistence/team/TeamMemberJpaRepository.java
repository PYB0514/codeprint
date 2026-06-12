// 팀 멤버 JPA 리포지토리
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.TeamMember;
import com.codeprint.domain.team.TeamRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberJpaRepository extends JpaRepository<TeamMember, UUID> {
    List<TeamMember> findByTeamId(UUID teamId);
    List<TeamMember> findByUserId(UUID userId);
    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);
    long countByTeamId(UUID teamId);
    long countByTeamIdAndRoleNot(UUID teamId, TeamRole role);
}
