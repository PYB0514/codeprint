// 서버 재기동 시 중단된(stuck) 분석을 FAILED로 정리하는 리스너
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class StuckAnalysisCleaner {

    private final AnalysisRepository analysisRepository;

    // 서버 기동 직후 이전 인스턴스가 남긴 RUNNING/PENDING 분석을 FAILED로 청소
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void cleanupStuckAnalyses() {
        List<AnalysisResult> stuck = analysisRepository.findByStatusIn(
                List.of(AnalysisStatus.PENDING, AnalysisStatus.RUNNING));
        if (stuck.isEmpty()) return;

        for (AnalysisResult analysis : stuck) {
            analysis.fail("서버 재시작으로 분석이 중단되었습니다. 다시 시도해주세요.");
            analysisRepository.save(analysis);
        }
        log.warn("서버 기동 시 중단된 분석 {}건을 FAILED로 정리했습니다.", stuck.size());
    }
}
