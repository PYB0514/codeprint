// 팀 JPA 리포지토리
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TeamJpaRepository extends JpaRepository<Team, UUID> {
    List<Team> findByOwnerUserId(UUID ownerUserId);
}
