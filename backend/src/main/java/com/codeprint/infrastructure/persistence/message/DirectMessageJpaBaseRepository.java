// 쪽지 기본 JPA 저장소
package com.codeprint.infrastructure.persistence.message;

import com.codeprint.domain.message.DirectMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DirectMessageJpaBaseRepository extends JpaRepository<DirectMessage, UUID> {

    // 안 읽은 쪽지 수 (readAt이 null인 것)
    long countByReceiverIdAndReadAtIsNull(UUID receiverId);
}
