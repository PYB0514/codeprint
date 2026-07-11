// 게이트 체크 결과 도메인 Repository JPA 구현체
package com.codeprint.infrastructure.persistence.analysis;

import com.codeprint.domain.analysis.GateCheckLog;
import com.codeprint.domain.analysis.GateCheckLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class GateCheckLogRepositoryImpl implements GateCheckLogRepository {

    private final GateCheckLogJpaRepository jpa;

    // 게이트 체크 결과 엔티티를 저장하고 반환
    @Override
    public GateCheckLog save(GateCheckLog gateCheckLog) {
        return jpa.save(gateCheckLog);
    }
}
