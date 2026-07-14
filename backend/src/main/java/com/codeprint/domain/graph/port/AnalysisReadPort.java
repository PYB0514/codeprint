// Graph 도메인에서 analysis 컨텍스트의 브랜치명을 조회하는 포트
package com.codeprint.domain.graph.port;

import java.util.Optional;
import java.util.UUID;

public interface AnalysisReadPort {

    // 분석 ID로 브랜치명 조회 — 분석 없거나 브랜치 미기록 시 empty
    Optional<String> findBranch(UUID analysisId);

    // 분석 ID로 커밋 SHA 조회 — 오탐 신고 재현 페이로드가 정확한 커밋을 특정하기 위함(분석 없거나 SHA 미기록 시 empty)
    Optional<String> findCommitSha(UUID analysisId);
}
