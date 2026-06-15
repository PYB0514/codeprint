// 사용자 문의/피드백 엔티티
package com.codeprint.domain.community;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feedbacks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Feedback {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "user_id", columnDefinition = "uuid")
    private UUID userId;

    @Column(nullable = false, length = 20)
    private String category;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text", nullable = false)
    private String content;

    @Column(length = 200)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 처리 상태 — OPEN(미처리) / RESOLVED(처리 완료)
    @Column(nullable = false, length = 20)
    private String status;

    // 피드백 생성
    public static Feedback create(UUID userId, String category, String title, String content, String email) {
        Feedback f = new Feedback();
        f.id = UUID.randomUUID();
        f.userId = userId;
        f.category = category;
        f.title = title;
        f.content = content;
        f.email = email;
        f.createdAt = Instant.now();
        f.status = "OPEN";
        return f;
    }

    // 문의를 처리 완료로 표시
    public void resolve() {
        this.status = "RESOLVED";
    }

    // 문의를 미처리로 되돌림
    public void reopen() {
        this.status = "OPEN";
    }
}
