// G-5 웹훅 유실 안전망 — GitHub Actions cron이 공유 시크릿으로 호출하는 외부 트리거
package com.codeprint.interfaces.api;

import com.codeprint.application.analysis.PrGateReconciliationService;
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
@RequestMapping("/api/cron/reconcile-pr-gate")
@RequiredArgsConstructor
public class PrGateReconciliationCronController {

    private final PrGateReconciliationService prGateReconciliationService;

    @Value("${cron.secret:}")
    private String cronSecret;

    // 매시 정각 실행 — 연결된 프로젝트의 열린 PR 중 codeprint/structure 상태 없는 것을 찾아 리뷰 재트리거(G-5 안전망)
    @PostMapping
    public ResponseEntity<Map<String, Object>> reconcile(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (!CronSecretVerifier.verify(cronSecret, secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "invalid secret"));
        }
        try {
            int triggered = prGateReconciliationService.reconcile();
            log.info("G-5 리컨실리에이션 완료(cron) — 재트리거 {}건", triggered);
            return ResponseEntity.ok(Map.of("status", "done", "triggered", triggered));
        } catch (Exception ex) {
            log.error("G-5 리컨실리에이션 실패(cron)", ex);
            return ResponseEntity.internalServerError().body(Map.of("status", "failed"));
        }
    }
}
