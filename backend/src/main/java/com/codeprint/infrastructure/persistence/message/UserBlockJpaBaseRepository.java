// 차단 관계 기본 JPA 저장소
package com.codeprint.infrastructure.persistence.message;

import com.codeprint.domain.message.UserBlock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserBlockJpaBaseRepository extends JpaRepository<UserBlock, UUID> {

    boolean existsByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);

    List<UserBlock> findByBlockerId(UUID blockerId);

    void deleteByBlockerIdAndBlockedId(UUID blockerId, UUID blockedId);
}
