// Graph 도메인에서 project 컨텍스트의 소유권·공개 여부를 확인/조회하는 포트 (project 도메인 모델 비노출)
package com.codeprint.domain.graph.port;

import com.codeprint.shared.gate.GatePolicy;

import java.util.UUID;

public interface ProjectAccessPort {

    // 소유자 검증만 수행 — 미소유·부재 시 예외, 반환값 없음
    void verifyOwnership(UUID projectId, UUID userId);

    // 공개 여부만 검증 — 비공개·부재 시 예외, 반환값 없음
    void verifyPublic(UUID projectId);

    // 소유자 검증 통과 후 프로젝트 view 반환 — 미소유·부재 시 예외
    ProjectAccessView getOwnedProject(UUID projectId, UUID userId);

    // 공개 프로젝트 view 반환 — 비공개·부재 시 예외
    ProjectAccessView getPublicProject(UUID projectId);

    // 접근 검증 없는 원시 조회 — 호출자가 이미 접근을 검증한 흐름 전용(오탐 신고 재현 페이로드용)
    java.util.Optional<String> findGithubRepoUrl(UUID projectId);

    // 접근 검증 없는 원시 조회 — 호출자가 별도 인가(팀 배분 등)를 이미 검증한 흐름 전용(MCP 팀 교차조회용)
    java.util.Optional<ProjectAccessView> getProjectById(UUID projectId);

    // 소유자 확인 후 게이트 정책(AUTO/DDD/LAYERED) 전환 — 게이트 테마 표면화 기능(1~2단계)
    void setGatePolicy(UUID projectId, UUID userId, GatePolicy policy);

    // graph 도메인이 필요로 하는 project 필드만 추린 view
    record ProjectAccessView(UUID id, UUID userId, String name, String githubRepoUrl, GatePolicy gatePolicy) {
        // 레포 owner가 프로젝트 소유자의 GitHub 계정과 일치하는지 (내 레포 vs 외부 레포 분석 판정)
        public boolean isOwnRepo(String ownerUsername) {
            return com.codeprint.shared.GithubRepoOwner.matches(githubRepoUrl, ownerUsername);
        }
    }
}
