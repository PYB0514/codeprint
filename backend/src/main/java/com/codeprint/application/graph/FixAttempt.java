// 리컨실러 T1 자동수정 시도 결과 — 트윈 검증 통과분만 SUCCESS(§17.4-3: 자동 머지 없음, PR 초안 재료로만 사용)
package com.codeprint.application.graph;

public record FixAttempt(Outcome outcome, String diff, String rationale, String reason) {

    public enum Outcome { SUCCESS, SKIPPED, FAILED }

    static FixAttempt success(String diff, String rationale) {
        return new FixAttempt(Outcome.SUCCESS, diff, rationale, null);
    }

    static FixAttempt skipped(String reason) {
        return new FixAttempt(Outcome.SKIPPED, null, null, reason);
    }

    static FixAttempt failed(String reason) {
        return new FixAttempt(Outcome.FAILED, null, null, reason);
    }
}
