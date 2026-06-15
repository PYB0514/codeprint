// 일일 다이제스트 생성 완료 이벤트 — 관리자 알림/푸시 발송을 트리거 (사이드이펙트 분리)
package com.codeprint.shared.event;

import java.util.List;
import java.util.UUID;

public record DailyDigestReadyEvent(List<UUID> adminIds, String message) {}
