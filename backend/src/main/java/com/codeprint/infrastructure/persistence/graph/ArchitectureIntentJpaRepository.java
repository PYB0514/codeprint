// architecture_intents 테이블 Spring Data JPA 인터페이스
package com.codeprint.infrastructure.persistence.graph;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ArchitectureIntentJpaRepository extends JpaRepository<ArchitectureIntentEntity, UUID> {
}
