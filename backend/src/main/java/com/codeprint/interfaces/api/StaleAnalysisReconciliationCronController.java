// 안정성 갭 C+F 안전망 — GitHub Actions cron이 공유 시크릿으로 호출하는 외부 트리거
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.StaleAnalysisReconciliationService;
import com.codeprint.domain.analysis.CronSecretVerifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cron/reconcile-stale-analyses")
@RequiredArgsConstructor
public class StaleAnalysisReconciliationCronController {

    private final StaleAnalysisReconciliationService staleAnalysisReconciliationService;

    @Value("${cron.secret:}")
    private String cronSecret;

    // 15분마다 실행 — 파싱→그래프빌드 구간 타임아웃 부재로 영구 RUNNING이 될 수 있는 분석을 감지해 FAILED로 정리
    @PostMapping
    public ResponseEntity<Map<String, Object>> reconcile(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (!CronSecretVerifier.verify(cronSecret, secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "invalid secret"));
        }
        try {
            int reconciled = staleAnalysisReconciliationService.reconcile();
            log.info("스테일 분석 리컨실리에이션 완료(cron) — {}건 정리", reconciled);
            return ResponseEntity.ok(Map.of("status", "done", "reconciled", reconciled));
        } catch (Exception ex) {
            log.error("스테일 분석 리컨실리에이션 실패(cron)", ex);
            return ResponseEntity.internalServerError().body(Map.of("status", "failed"));
        }
    }
}
