// 오탐 신고 저장소 인터페이스
package com.codeprint.domain.graph;

import java.util.Set;
import java.util.UUID;

public interface FpReportRepository {

    // 오탐 신고 저장
    void save(FpReport report);

    // 프로젝트에서 특정 사용자가 신고한 fingerprint 집합 조회
    Set<String> findFingerprintsByProjectIdAndReporterId(UUID projectId, UUID reporterId);

    // 동일 사용자가 동일 fingerprint를 이미 신고했는지 여부
    boolean existsByProjectIdAndFingerprintAndReporterId(UUID projectId, String fingerprint, UUID reporterId);
}
