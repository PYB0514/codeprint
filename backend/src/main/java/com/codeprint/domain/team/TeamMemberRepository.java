// 팀 멤버 리포지토리 인터페이스
package com.codeprint.domain.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamMemberRepository {

    TeamMember save(TeamMember member);

    void deleteById(UUID id);

    List<TeamMember> findByTeamId(UUID teamId);

    // 유저가 속한 팀 목록 조회용
    List<TeamMember> findByUserId(UUID userId);

    Optional<TeamMember> findByTeamIdAndUserId(UUID teamId, UUID userId);

    // 팀의 현재 멤버 수 (석수 초과 검증용)
    long countByTeamId(UUID teamId);

    // OWNER 제외 멤버 수 (석수 초과 검증 시 소유자 제외)
    long countMembersExcludingOwner(UUID teamId);
}
