// 운영자 공지사항 도메인 엔티티
package com.codeprint.domain.notice;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notices")
@Getter
@NoArgsConstructor
public class Notice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // 새 공지사항 생성 (비활성 상태로 시작)
    public static Notice create(String title, String content) {
        Notice notice = new Notice();
        notice.title = title;
        notice.content = content;
        notice.active = false;
        notice.createdAt = Instant.now();
        notice.updatedAt = Instant.now();
        return notice;
    }

    // 공지사항 활성화
    public void activate() {
        this.active = true;
        this.updatedAt = Instant.now();
    }

    // 공지사항 비활성화
    public void deactivate() {
        this.active = false;
        this.updatedAt = Instant.now();
    }
}
