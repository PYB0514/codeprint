// 경고 suppress 저장소 인터페이스
package com.codeprint.domain.graph;

import java.util.Set;
import java.util.UUID;

public interface WarningSuppressionRepository {

    // suppress 저장 (이미 있으면 무시)
    void save(WarningSuppression suppression);

    // 프로젝트의 suppress된 fingerprint 집합 조회
    Set<String> findFingerprintsByProjectId(UUID projectId);

    // 프로젝트의 특정 fingerprint suppress 존재 여부
    boolean existsByProjectIdAndFingerprint(UUID projectId, String fingerprint);

    // 프로젝트의 특정 fingerprint suppress 해제
    void deleteByProjectIdAndFingerprint(UUID projectId, String fingerprint);
}
