// 일일 다이제스트 스케줄러 — 매일 정해진 시각에 전일 지표를 집계·발송하는 얇은 트리거
package com.codeprint.infrastructure.admin;

import com.codeprint.application.admin.AdminDigestService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Component
@RequiredArgsConstructor
public class DailyDigestScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final Logger log = LoggerFactory.getLogger(DailyDigestScheduler.class);

    private final AdminDigestService adminDigestService;

    // 매일 09:00 KST — 전일 지표 다이제스트 생성·관리자 발송
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        LocalDate target = LocalDate.now(KST).minusDays(1);
        try {
            adminDigestService.runFor(target);
            log.info("일일 다이제스트 발송 완료 — {}", target);
        } catch (Exception ex) {
            log.error("일일 다이제스트 발송 실패 — {}", target, ex);
        }
    }
}
