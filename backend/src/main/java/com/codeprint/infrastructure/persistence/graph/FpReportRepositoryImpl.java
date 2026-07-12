// FpReportRepository JPA 구현체
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.FpReport;
import com.codeprint.domain.graph.FpReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class FpReportRepositoryImpl implements FpReportRepository {

    private final FpReportJpaRepository jpa;

    // 오탐 신고 저장
    @Override
    public void save(FpReport report) {
        jpa.save(report);
    }

    // 사용자가 신고한 fingerprint 집합 반환
    @Override
    public Set<String> findFingerprintsByProjectIdAndReporterId(UUID projectId, UUID reporterId) {
        return jpa.findByProjectIdAndReporterId(projectId, reporterId).stream()
                .map(FpReport::getFingerprint)
                .collect(Collectors.toSet());
    }

    // 중복 신고 여부 확인
    @Override
    public boolean existsByProjectIdAndFingerprintAndReporterId(UUID projectId, String fingerprint, UUID reporterId) {
        return jpa.existsByProjectIdAndFingerprintAndReporterId(projectId, fingerprint, reporterId);
    }
}
