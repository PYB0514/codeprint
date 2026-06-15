// 그래프 버전 보존 정책 — 비고정 최근 N개만 유지하고 초과분을 삭제 대상으로 선별 (고정 슬롯은 항상 보존)
package com.codeprint.domain.graph;

import java.util.Comparator;
import java.util.List;

public final class GraphRetentionPolicy {

    // 프로젝트당 유지할 비고정 그래프 최대 개수
    public static final int MAX_RECENT = 10;

    private GraphRetentionPolicy() {}

    // 삭제 대상 선별 — 비고정 그래프를 최신순 정렬 후 MAX_RECENT 초과분 반환 (고정 그래프는 대상에서 제외)
    public static List<Graph> selectEvictable(List<Graph> projectGraphs) {
        List<Graph> nonPinned = projectGraphs.stream()
                .filter(g -> !g.isPinned())
                .sorted(Comparator.comparing(Graph::getCreatedAt).reversed())
                .toList();
        if (nonPinned.size() <= MAX_RECENT) {
            return List.of();
        }
        return List.copyOf(nonPinned.subList(MAX_RECENT, nonPinned.size()));
    }
}
