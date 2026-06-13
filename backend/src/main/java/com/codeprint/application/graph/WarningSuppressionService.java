// 프로젝트 단위 경고 suppress(숨김)·해제·조회 애플리케이션 서비스
package com.codeprint.application.graph;

import com.codeprint.domain.graph.WarningSuppression;
import com.codeprint.domain.graph.WarningSuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WarningSuppressionService {

    private final WarningSuppressionRepository repository;

    // 경고 suppress — 이미 숨겨져 있으면 무시(멱등)
    @Transactional
    public void suppress(UUID projectId, String fingerprint, String warningType) {
        if (repository.existsByProjectIdAndFingerprint(projectId, fingerprint)) {
            return;
        }
        repository.save(WarningSuppression.create(projectId, fingerprint, warningType));
    }

    // suppress 해제
    @Transactional
    public void unsuppress(UUID projectId, String fingerprint) {
        repository.deleteByProjectIdAndFingerprint(projectId, fingerprint);
    }

    // 프로젝트의 suppress된 fingerprint 집합 조회
    @Transactional(readOnly = true)
    public Set<String> getSuppressedFingerprints(UUID projectId) {
        return repository.findFingerprintsByProjectId(projectId);
    }
}
