// 프로젝트 단위로 숨긴(suppress) 경고 — fingerprint로 재분석에도 동일 경고를 식별
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "warning_suppressions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WarningSuppression {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "project_id", nullable = false, columnDefinition = "uuid")
    private UUID projectId;

    @Column(nullable = false, length = 64)
    private String fingerprint;

    @Column(name = "warning_type", length = 50)
    private String warningType;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // 프로젝트의 특정 경고를 숨김 처리하는 인스턴스 생성
    public static WarningSuppression create(UUID projectId, String fingerprint, String warningType) {
        WarningSuppression w = new WarningSuppression();
        w.id = UUID.randomUUID();
        w.projectId = projectId;
        w.fingerprint = fingerprint;
        w.warningType = warningType;
        w.createdAt = Instant.now();
        return w;
    }
}
