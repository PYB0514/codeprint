// 파일 경로에서 바운디드 컨텍스트(도메인/기능 단위)를 추론하는 유틸 — GraphWarningService(컨텍스트 경계
// 위반 판정)와 RepoMapService(컨텍스트별 트리 export)가 공유하는 단일 소스(2026-07-27, GraphWarningService
// 전용이었던 로직을 추출 — decisions/DECISIONS_ANALYSIS.md 참조)
package com.codeprint.application.graph;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class BoundedContextResolver {

    private BoundedContextResolver() {}

    // 아키텍처 레이어/하위패키지 용어 — 헥사고날·클린아키텍처에서 application/domain/, application/port/ 처럼
    // 레이어명이 컨텍스트 자리에 오는 것을 바운디드 컨텍스트로 오인하지 않도록 제외(buckpal 류 교과서 FP 방지).
    // shared·common·seedwork·shared_kernel·kernel 은 Shared Kernel(모든 컨텍스트가 공유하는 베이스)이라
    // 바운디드 컨텍스트가 아니다 — 이를 import하는 것은 정상이므로 컨텍스트로 인식하면 cross-context 오탐을 낸다.
    static final Set<String> LAYER_TERMS = Set.of(
        "domain", "application", "infrastructure", "interfaces", "presentation",
        "adapter", "adapters", "port", "ports", "service", "services",
        "model", "models", "entity", "entities", "repository", "repositories",
        "controller", "controllers", "usecase", "usecases", "use_case",
        "in", "out", "web", "persistence", "api", "rest", "config",
        "common", "shared", "dto", "dtos", "mapper", "mappers", "util", "utils",
        "seedwork", "shared_kernel", "kernel"
    );

    // 도메인 레이어 디렉터리 별칭 — 실제 Java 레포는 domain 외에 core·domains 로도 도메인 레이어를 명명한다.
    static final Set<String> DOMAIN_LAYER_DIRS = Set.of("domain", "domains", "core");
    // 애플리케이션 레이어 디렉터리 별칭.
    static final Set<String> APPLICATION_LAYER_DIRS = Set.of("application", "usecase", "usecases", "services");

    // 컨텍스트 경계를 이루는 내부 레이어 디렉터리 — 한 세그먼트가 이들 중 2개 이상을 직접 선행하면 그 세그먼트는
    // 바운디드 컨텍스트(context-first 레이아웃 {context}/{layer}/)일 가능성이 높다.
    private static final Set<String> CONTEXT_BOUNDARY_LAYERS = Set.of(
        "application", "usecase", "usecases", "model", "models", "domain", "domains", "core",
        "infrastructure", "infra", "adapter", "adapters", "persistence", "web", "port", "ports",
        "interfaces", "presentation", "api", "dao");
    // context-first 레이아웃에서 도메인 레이어로 인정하는 디렉터리 — 별칭 + model(DDD 애그리거트 모델).
    // model을 전역 DOMAIN_LAYER_DIRS에 넣지 않고 여기서만 쓰는 이유: model은 흔한 일반 디렉터리라
    // 확인된 context-first 컨텍스트의 직하위일 때만 도메인으로 보아야 precision이 안전하다.
    private static final Set<String> CONTEXT_FIRST_DOMAIN_DIRS = Set.of(
        "domain", "domains", "core", "model", "models");

    // 전역 레이아웃 추론: {context}/{layer}/ 형태의 context-first 컨텍스트 집합을 반환.
    // 판별 = 한 세그먼트가 서로 다른 CONTEXT_BOUNDARY_LAYERS를 2개 이상 직접 선행하고, 그런 세그먼트가 2개 이상일 때만
    // (layer-first 레포의 패키지 루트는 단 하나만 레이어를 선행하므로 1개<2로 배제 → 무회귀).
    public static Set<String> detectContextFirstContexts(Collection<String> paths) {
        Map<String, Set<String>> segToLayers = new HashMap<>();
        for (String raw : paths) {
            if (raw == null) continue;
            String[] segs = raw.replace("\\", "/").split("/");
            for (int i = 0; i + 1 < segs.length; i++) {
                String next = segs[i + 1];
                String seg = segs[i];
                if (seg.isEmpty() || LAYER_TERMS.contains(seg)) continue;
                if (CONTEXT_BOUNDARY_LAYERS.contains(next)) {
                    segToLayers.computeIfAbsent(seg, k -> new HashSet<>()).add(next);
                }
            }
        }
        Set<String> candidates = new HashSet<>();
        for (Map.Entry<String, Set<String>> e : segToLayers.entrySet()) {
            if (e.getValue().size() >= 2) candidates.add(e.getKey());
        }
        // 후보가 2개 미만이면 단일 루트(layer-first)이므로 context-first가 아니다.
        return candidates.size() >= 2 ? candidates : Set.of();
    }

    // application 컨텍스트 — context-first면 application 마커 앞 세그먼트(확인된 컨텍스트), 아니면 layer-first 추출.
    public static String applicationContextOf(String path, Set<String> cfContexts) {
        if (!cfContexts.isEmpty()) {
            String cf = contextBeforeLayer(path, APPLICATION_LAYER_DIRS, cfContexts);
            if (cf != null) return cf;
        }
        return extractContextFromApplicationPath(path);
    }

    // domain 컨텍스트 — context-first면 도메인 레이어(model 포함) 마커 앞 세그먼트, 아니면 layer-first 추출.
    public static String domainContextOf(String path, Set<String> cfContexts) {
        if (!cfContexts.isEmpty()) {
            String cf = contextBeforeLayer(path, CONTEXT_FIRST_DOMAIN_DIRS, cfContexts);
            if (cf != null) return cf;
        }
        return extractContextFromDomainPath(path);
    }

    // FUNCTION 노드의 컨텍스트 — domain 우선, 없으면 application 레이어 기준으로 폴백.
    public static String functionContextOf(String path, Set<String> cfContexts) {
        String dc = domainContextOf(path, cfContexts);
        if (dc != null) return dc;
        return applicationContextOf(path, cfContexts);
    }

    // 레이어 별칭 마커(/{layer}/) 바로 앞 세그먼트가 확인된 context-first 컨텍스트면 반환 — 아니면 null.
    private static String contextBeforeLayer(String path, Set<String> layerDirs, Set<String> cfContexts) {
        String p = path.replace("\\", "/");
        for (String layer : layerDirs) {
            int idx = p.indexOf("/" + layer + "/");
            if (idx < 0) continue;
            String before = p.substring(0, idx);
            int ls = before.lastIndexOf('/');
            String seg = ls >= 0 ? before.substring(ls + 1) : before;
            if (cfContexts.contains(seg)) return seg;
        }
        return null;
    }

    // application 레이어 별칭(application/usecase 등) 바로 다음 세그먼트를 컨텍스트명으로 추출 — 레이어 용어면 null.
    private static String extractContextFromApplicationPath(String path) {
        return extractContextAfterLayer(path, APPLICATION_LAYER_DIRS, false);
    }

    // domain 레이어 별칭(domain/domains/core 등) 바로 다음 세그먼트를 컨텍스트명으로 추출 — 없으면 null.
    // domain 마커가 application 마커 하위에 중첩(application/domain/model)이면 헥사고날 레이어이지 top-level 도메인
    // 레이어가 아니므로 컨텍스트로 보지 않는다. 추출 세그먼트가 레이어 용어인 경우도 제외.
    public static String extractContextFromDomainPath(String path) {
        return extractContextAfterLayer(path, DOMAIN_LAYER_DIRS, true);
    }

    // 경로에서 주어진 레이어 별칭 디렉터리 바로 다음 세그먼트를 컨텍스트명으로 추출 — 레이어 용어면 다음 별칭 시도.
    // excludeNestedUnderApplication=true면 해당 마커가 application 별칭 하위에 중첩된 경우 건너뛴다.
    private static String extractContextAfterLayer(String path, Set<String> layerDirs, boolean excludeNestedUnderApplication) {
        String p = path.replace("\\", "/");
        for (String layer : layerDirs) {
            String marker = "/" + layer + "/";
            int idx = p.indexOf(marker);
            if (idx < 0) continue;
            if (excludeNestedUnderApplication) {
                int appIdx = firstLayerIndex(p, APPLICATION_LAYER_DIRS);
                if (appIdx >= 0 && appIdx < idx) continue;
            }
            String after = p.substring(idx + marker.length());
            int slash = after.indexOf('/');
            if (slash <= 0) continue;
            String seg = after.substring(0, slash);
            if (LAYER_TERMS.contains(seg)) continue;
            return seg;
        }
        return null;
    }

    // 경로에서 주어진 레이어 별칭 마커(/{dir}/) 중 가장 앞선 인덱스 — 없으면 -1. 중첩 도메인 가드용.
    private static int firstLayerIndex(String p, Set<String> dirs) {
        int best = -1;
        for (String d : dirs) {
            int i = p.indexOf("/" + d + "/");
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    // 경로에서 features/{X}/ 의 피처명 X 추출 — features 직속 파일(features/x.ts)이나 미해당이면 null.
    // React/JS 피처-슬라이스 레이아웃 전용(CROSS_FEATURE_IMPORT 등)이었으나, DDD 컨텍스트가 없는 프로젝트의
    // 대체 그룹핑 축으로 RepoMapService(§16 컨텍스트별 export)도 공유하도록 공용화(2026-08-02, decisions/DECISIONS_ANALYSIS.md 참조).
    public static String featureOf(String path) {
        if (path == null) return null;
        String p = path.replace("\\", "/");
        int idx = p.indexOf("/features/");
        int start;
        if (idx >= 0) start = idx + "/features/".length();
        else if (p.startsWith("features/")) start = "features/".length();
        else return null;
        int slash = p.indexOf('/', start);
        if (slash <= start) return null;
        return p.substring(start, slash);
    }
}
