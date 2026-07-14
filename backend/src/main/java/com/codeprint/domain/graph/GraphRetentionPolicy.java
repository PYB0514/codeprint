// 그래프 버전 보존 정책 — 비고정 최근 N개만 유지하고 초과분을 삭제 대상으로 선별 (고정 슬롯은 항상 보존)
package com.codeprint.domain.graph;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class GraphRetentionPolicy {

    // 프로젝트당 유지할 비고정 그래프 최대 개수
    public static final int MAX_RECENT = 10;

    // 시스템(갤러리) 계정 프로젝트에 적용하는 축소된 보존 개수 — 1이 아닌 이유는 갤러리 재발행이
    // 분석 직후 비동기로 일어나 직전 그래프가 잠시 참조 중일 수 있어서(§18.8-④ 1단계)
    public static final int MAX_RECENT_SYSTEM = 2;

    private GraphRetentionPolicy() {}

    // 삭제 대상 선별 — 비고정 그래프를 최신순 정렬 후 MAX_RECENT 초과분 반환 (고정 그래프는 대상에서 제외)
    public static List<Graph> selectEvictable(List<Graph> projectGraphs) {
        return selectEvictable(projectGraphs, MAX_RECENT, Set.of());
    }

    // 보존 개수·보호 대상(스냅샷이 참조 중인 graph_id)을 지정하는 버전 — 고정 그래프와 동일하게 삭제 대상에서 제외
    public static List<Graph> selectEvictable(List<Graph> projectGraphs, int maxRecent, Set<UUID> protectedGraphIds) {
        List<Graph> nonPinned = projectGraphs.stream()
                .filter(g -> !g.isPinned())
                .filter(g -> !protectedGraphIds.contains(g.getId()))
                .sorted(Comparator.comparing(Graph::getCreatedAt).reversed())
                .toList();
        if (nonPinned.size() <= maxRecent) {
            return List.of();
        }
        return List.copyOf(nonPinned.subList(maxRecent, nonPinned.size()));
    }
}
