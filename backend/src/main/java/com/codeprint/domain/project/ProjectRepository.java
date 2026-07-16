// 프로젝트 도메인 Repository 인터페이스
package com.codeprint.domain.project;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository {

    // 프로젝트 저장
    Project save(Project project);

    // ID로 프로젝트 조회
    Optional<Project> findById(UUID id);

    // 사용자 ID로 프로젝트 목록 조회
    List<Project> findByUserId(UUID userId);

    // GitHub 레포 URL로 프로젝트 목록 조회 (.git 접미사·대소문자 무시) — webhook이 repo로 프로젝트 역해석
    List<Project> findByRepoUrl(String repoHttpsUrl);

    // PR 게이트 webhook이 연결된(시크릿 발급된) 프로젝트 전체 조회 — G-5 리컨실리에이션 대상
    List<Project> findAllWithPrGateConnected();

    // 사용자 ID로 비공개 프로젝트 수 조회 (공개 프로젝트는 제한 제외)
    int countPrivateByUserId(UUID userId);

    // 특정 유저의 공개 프로젝트 목록 조회
    List<Project> findPublicByUserId(UUID userId);

    // ID로 프로젝트 삭제
    void deleteById(UUID id);

    // 어드민 전용 — 전체 프로젝트 수 조회
    long count();
}
