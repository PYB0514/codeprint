// 팀 API 키 인증 성공 시 SecurityContext에 실리는 팀 스코프 principal
package com.codeprint.domain.team;

import java.util.UUID;

public record TeamApiKeyPrincipal(UUID teamId, UUID apiKeyId) {}
