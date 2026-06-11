// 피드백 레포지토리 인터페이스 (도메인 포트)
package com.codeprint.domain.community;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
}
