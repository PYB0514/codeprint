// 오탐 신고 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.FpReport;
import com.codeprint.domain.graph.FpReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FpReportService {

    private final FpReportRepository repository;

    // 오탐 신고 — 동일 사용자가 이미 신고했으면 무시(멱등)
    @Transactional
    public void reportFalsePositive(UUID projectId, String fingerprint, String warningType, UUID reporterId, String reason) {
        if (repository.existsByProjectIdAndFingerprintAndReporterId(projectId, fingerprint, reporterId)) {
            return;
        }
        repository.save(FpReport.create(projectId, fingerprint, warningType, reporterId, reason));
    }

    // 사용자가 신고한 fingerprint 집합 조회 — 버튼 상태 표시용
    @Transactional(readOnly = true)
    public Set<String> getReportedFingerprintsByUser(UUID projectId, UUID reporterId) {
        return repository.findFingerprintsByProjectIdAndReporterId(projectId, reporterId);
    }
}
