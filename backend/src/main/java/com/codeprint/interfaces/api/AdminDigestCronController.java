// 일일 다이제스트 발송 — GitHub Actions cron이 공유 시크릿으로 호출하는 외부 트리거
package com.codeprint.interfaces.api;

import com.codeprint.application.admin.AdminDigestService;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/cron/daily-digest")
@RequiredArgsConstructor
public class AdminDigestCronController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final AdminDigestService adminDigestService;

    @Value("${cron.secret:}")
    private String cronSecret;

    // 매일 09:00 KST 전일 지표 다이제스트 생성·발송 — 내부 @Scheduled를 대체(백엔드 유휴 시간에 App Sleeping 허용 목적)
    @PostMapping
    public ResponseEntity<Map<String, String>> runDailyDigest(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (!CronSecretVerifier.verify(cronSecret, secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "invalid secret"));
        }
        LocalDate target = LocalDate.now(KST).minusDays(1);
        try {
            adminDigestService.runFor(target);
            log.info("일일 다이제스트 발송 완료(cron) — {}", target);
            return ResponseEntity.ok(Map.of("status", "done"));
        } catch (Exception ex) {
            log.error("일일 다이제스트 발송 실패(cron) — {}", target, ex);
            return ResponseEntity.internalServerError().body(Map.of("status", "failed"));
        }
    }
}
