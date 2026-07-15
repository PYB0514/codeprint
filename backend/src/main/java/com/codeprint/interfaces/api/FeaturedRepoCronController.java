// 오늘의 공개레포 갱신 — GitHub Actions cron이 공유 시크릿으로 호출하는 외부 트리거
package com.codeprint.interfaces.api;

import com.codeprint.application.featured.FeaturedRepoService;
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
@RequestMapping("/api/cron/refresh-featured")
@RequiredArgsConstructor
public class FeaturedRepoCronController {

    private final FeaturedRepoService featuredRepoService;

    @Value("${cron.secret:}")
    private String cronSecret;

    // 매일 06:00 KST 갤러리 5개 로테이션 갱신 — 내부 @Scheduled를 대체(백엔드 유휴 시간에 App Sleeping 허용 목적)
    @PostMapping
    public ResponseEntity<Map<String, String>> refreshFeatured(
            @RequestHeader(value = "X-Cron-Secret", required = false) String secret) {
        if (!CronSecretVerifier.verify(cronSecret, secret)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("status", "invalid secret"));
        }
        try {
            featuredRepoService.refreshDailyFeatured();
            log.info("오늘의 공개레포 갱신 완료(cron)");
            return ResponseEntity.ok(Map.of("status", "done"));
        } catch (Exception ex) {
            log.error("오늘의 공개레포 갱신 실패(cron)", ex);
            return ResponseEntity.internalServerError().body(Map.of("status", "failed"));
        }
    }
}
