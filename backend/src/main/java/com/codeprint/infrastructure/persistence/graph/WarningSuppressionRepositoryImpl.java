// WarningSuppressionRepository JPA 구현체
package com.codeprint.infrastructure.persistence.graph;

import com.codeprint.domain.graph.WarningSuppression;
import com.codeprint.domain.graph.WarningSuppressionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class WarningSuppressionRepositoryImpl implements WarningSuppressionRepository {

    private final WarningSuppressionJpaRepository jpa;

    // suppress 저장
    @Override
    public void save(WarningSuppression suppression) {
        jpa.save(suppression);
    }

    // 프로젝트의 suppress된 fingerprint 집합 반환
    @Override
    public Set<String> findFingerprintsByProjectId(UUID projectId) {
        return jpa.findByProjectId(projectId).stream()
                .map(WarningSuppression::getFingerprint)
                .collect(Collectors.toSet());
    }

    // suppress 존재 여부 확인
    @Override
    public boolean existsByProjectIdAndFingerprint(UUID projectId, String fingerprint) {
        return jpa.existsByProjectIdAndFingerprint(projectId, fingerprint);
    }

    // suppress 해제
    @Override
    @Transactional
    public void deleteByProjectIdAndFingerprint(UUID projectId, String fingerprint) {
        jpa.deleteByProjectIdAndFingerprint(projectId, fingerprint);
    }
}
