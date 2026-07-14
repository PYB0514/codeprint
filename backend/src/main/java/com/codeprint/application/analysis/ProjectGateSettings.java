// PR 게이트 등급(0/1/2단계) 판정에 필요한 프로젝트별 설정 — 값 객체
package com.codeprint.application.analysis;

public record ProjectGateSettings(
        boolean architectureGateEnabled, // 1단계(architecture) — 기본 true, 레거시 완충용으로 끌 수 있음
        boolean experimentalGateEnabled  // 2단계(experimental) — 기본 false, 옵트인
) {}
