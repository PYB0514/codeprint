// 팀 리포지토리 인터페이스
package com.codeprint.domain.team;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TeamRepository {

    Team save(Team team);

    Optional<Team> findById(UUID id);

    // 특정 유저가 소유한 팀 목록
    List<Team> findByOwnerUserId(UUID ownerUserId);

    // 팀 삭제 (멤버·석수배분은 DB CASCADE로 함께 삭제됨)
    void deleteById(UUID id);

    // 좌석 수를 DB에서 원자적으로 증분(조회 없는 UPDATE) — 결제 확정 동시 요청 간 lost update 방지(좌석 증가 결제 TOCTOU 대응)
    void incrementSeats(UUID teamId, int delta);
}
