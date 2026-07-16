// PR 게이트 리컨실리에이션 대상 프로젝트 — 값 객체 (소유자 GitHub 토큰까지 포함해 재조회 없이 바로 사용)
package com.codeprint.application.analysis;

import java.util.UUID;

public record PrGateConnectedProject(UUID projectId, UUID ownerId, String repoUrl, String githubToken) {}
