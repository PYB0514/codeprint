// 게이트 체크 결과 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.GateCheckLog;
import com.codeprint.domain.analysis.GateCheckLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class GateCheckLogRepositoryImpl implements GateCheckLogRepository {

    private final GateCheckLogJpaRepository jpa;

    // 게이트 체크 결과 엔티티를 저장하고 반환
    @Override
    public GateCheckLog save(GateCheckLog gateCheckLog) {
        return jpa.save(gateCheckLog);
    }

    // 프로젝트의 가장 최근 게이트 체크 결과 조회
    @Override
    public Optional<GateCheckLog> findLatestByProjectId(UUID projectId) {
        return jpa.findTopByProjectIdOrderByCreatedAtDesc(projectId);
    }
}
