// 소스 파일 수집 결과 — 수집 파일 목록과 전체 대상 수 (MAX_FILES 초과 절단 감지용)
package com.codeprint.infrastructure.analysis;

import java.nio.file.Path;
import java.util.List;

public record WalkResult(
        List<Path> files,    // 분석 대상으로 수집된 파일 (최대 MAX_FILES개)
        int totalEligible    // 레포 내 전체 지원 언어 파일 수
) {}
