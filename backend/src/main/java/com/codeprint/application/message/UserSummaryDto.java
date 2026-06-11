// 쪽지 응답 구성용 유저 요약 DTO — User 도메인 객체 대신 사용하여 컨텍스트 간 직접 참조 제거
package com.codeprint.application.message;

import java.util.UUID;

public record UserSummaryDto(UUID id, String username, String avatarUrl) {}
