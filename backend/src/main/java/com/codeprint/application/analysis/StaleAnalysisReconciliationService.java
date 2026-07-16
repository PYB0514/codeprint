// 안정성 갭 C+F 안전망 — 파싱→그래프빌드 구간에 타임아웃이 없어 영구 RUNNING이 될 수 있는 분석을 주기 감지해 정리
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.interfaces.websocket.AnalysisProgressHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StaleAnalysisReconciliationService {

    // 정상 분석 파이프라인은 수 분 내 끝나므로(진행률 로그 기준) 20분은 충분히 넉넉한 여유
    static final Duration STALE_THRESHOLD = Duration.ofMinutes(20);

    private final AnalysisRepository analysisRepository;
    private final AnalysisProgressHandler progressHandler;

    // 분석 시작 시각이 상한을 넘었는지 — 순수 함수(단위 테스트 대상)
    static boolean isStale(Instant startedAt, Instant now) {
        return Duration.between(startedAt, now).compareTo(STALE_THRESHOLD) > 0;
    }

    // RUNNING 상태 분석 중 시작 시각이 상한을 넘은 것을 FAILED로 정리 — 정리한 건수 반환
    @Transactional
    public int reconcile() {
        Instant now = Instant.now();
        int reconciled = 0;
        for (AnalysisResult analysis : analysisRepository.findByStatusIn(List.of(AnalysisStatus.RUNNING))) {
            if (analysis.getStartedAt() == null || !isStale(analysis.getStartedAt(), now)) continue;
            log.warn("스테일 RUNNING 분석 감지 — FAILED 처리: analysisId={}, startedAt={}",
                    analysis.getId(), analysis.getStartedAt());
            analysis.fail("분석이 " + STALE_THRESHOLD.toMinutes() + "분 이상 진행 중 상태로 멈춰 자동 실패 처리되었습니다. 다시 시도해주세요.");
            analysisRepository.save(analysis);
            progressHandler.sendProgress(analysis.getId(), 0, "FAILED");
            reconciled++;
        }
        return reconciled;
    }
}
