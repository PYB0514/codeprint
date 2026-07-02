// Featured 도메인에서 analysis 컨텍스트의 비동기 분석 실행을 요청하는 포트
package com.codeprint.domain.featured.port;

import java.util.UUID;

public interface AnalysisTriggerPort {

    // 프로젝트 기본 브랜치로 분석 시작 (인증 토큰 없이 공개 레포만 대상)
    void triggerAnalysis(UUID projectId, String githubRepoUrl);
}
