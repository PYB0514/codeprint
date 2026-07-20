// 팀 JPA 리포지토리
package com.codeprint.infrastructure.persistence.team;

import com.codeprint.domain.team.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface TeamJpaRepository extends JpaRepository<Team, UUID> {
    List<Team> findByOwnerUserId(UUID ownerUserId);

    // 조회 없는 원자적 증분 — 동시 확정 요청이 서로의 증가분을 덮어쓰지 않도록 DB가 직접 더함
    @Modifying
    @Query("UPDATE Team t SET t.totalSeats = t.totalSeats + :delta WHERE t.id = :teamId")
    void incrementSeats(@Param("teamId") UUID teamId, @Param("delta") int delta);
}
