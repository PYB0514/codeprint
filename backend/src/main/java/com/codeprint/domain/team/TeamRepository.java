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
}
