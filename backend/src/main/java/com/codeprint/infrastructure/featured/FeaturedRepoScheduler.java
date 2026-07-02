// 오늘의 공개레포 스케줄러 — 매일 정해진 시각에 5개 로테이션 선정·분석을 트리거하는 얇은 트리거
package com.codeprint.infrastructure.featured;

import com.codeprint.application.featured.FeaturedRepoService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FeaturedRepoScheduler {

    private static final Logger log = LoggerFactory.getLogger(FeaturedRepoScheduler.class);

    private final FeaturedRepoService featuredRepoService;

    // 매일 06:00 KST — 방문자 유입 전에 그날의 5개 레포 선정·분석 갱신
    @Scheduled(cron = "0 0 6 * * *", zone = "Asia/Seoul")
    public void runDaily() {
        try {
            featuredRepoService.refreshDailyFeatured();
            log.info("오늘의 공개레포 갱신 완료");
        } catch (Exception ex) {
            log.error("오늘의 공개레포 갱신 실패", ex);
        }
    }
}
