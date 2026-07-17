// 모노레포 서비스 경계 판별 — application(GraphWarningService)·infrastructure(GraphBuilder) 양쪽에서 쓰는 공유 어휘(Shared Kernel)
package com.codeprint.shared.topology;

import java.util.Set;

public final class ServiceBoundary {

    private ServiceBoundary() {}

    // 래퍼 디렉터리 별칭 — services/apps/packages/modules 뒤 세그먼트를 서비스명으로,
    // 없으면(예: spring-petclinic-microservices처럼 서비스가 바로 최상위인 레포) 첫 세그먼트를 그대로 서비스명으로 쓴다.
    private static final Set<String> SERVICE_WRAPPER_DIRS = Set.of("services", "apps", "packages", "modules");

    // 파일 경로에서 모노레포 서비스(최상위 디렉터리) 식별자를 추출 — 판별 불가(세그먼트 부족)면 null
    public static String serviceOf(String filePath) {
        if (filePath == null) return null;
        String p = filePath.replace("\\", "/");
        while (p.startsWith("/")) p = p.substring(1);
        String[] segments = p.split("/");
        if (segments.length < 2) return null;
        int idx = SERVICE_WRAPPER_DIRS.contains(segments[0].toLowerCase()) ? 1 : 0;
        if (idx >= segments.length - 1) return null;
        return segments[idx];
    }
}
