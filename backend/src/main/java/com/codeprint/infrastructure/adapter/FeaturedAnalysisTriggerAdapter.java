// Featured AnalysisTriggerPort의 analysis 컨텍스트 어댑터 — 인증 토큰 없이 비동기 분석 시작
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.analysis.AnalysisRunner;
import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.featured.port.AnalysisTriggerPort;
import com.codeprint.infrastructure.config.AnalysisConcurrencyGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class FeaturedAnalysisTriggerAdapter implements AnalysisTriggerPort {

    private final AnalysisRepository analysisRepository;
    private final AnalysisRunner analysisRunner;
    private final AnalysisConcurrencyGuard concurrencyGuard;

    // 기본 브랜치·비인증(공개 레포 전용)으로 분석 레코드 생성 후 비동기 실행. AnalysisRunner.run()이
    // 작업 완료 시점에 슬롯을 반납하므로, 이 호출자도 AnalysisApplicationService와 동일하게 먼저
    // 슬롯을 예약해야 한다 — 예약 없이 호출하면 세마포어가 매번 과다 반납(over-release)돼 40개
    // 상한이 영구히 깨진다(2026-07-30 적대적 검증 CONFIRMED로 발견, 매일 cron으로 반복 실행되는
    // 경로라 방치 시 상한 자체가 무의미해짐).
    @Override
    @Transactional
    public void triggerAnalysis(UUID projectId, String githubRepoUrl) {
        if (!concurrencyGuard.tryAcquire()) {
            log.warn("동시 처리 한도 초과, featured 레포 분석 스킵(다음 스케줄에 재시도): projectId={}", projectId);
            return;
        }
        boolean submitted = false;
        try {
            AnalysisResult analysis = AnalysisResult.create(projectId, null);
            analysisRepository.save(analysis);
            analysisRunner.run(analysis.getId(), projectId, githubRepoUrl, null, null, null);
            submitted = true;
        } finally {
            if (!submitted) {
                concurrencyGuard.release();
            }
        }
    }
}
