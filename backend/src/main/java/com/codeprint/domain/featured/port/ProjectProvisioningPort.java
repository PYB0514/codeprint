// Featured 도메인에서 시스템 계정 소유의 공개 프로젝트 생성을 요청하는 포트
package com.codeprint.domain.featured.port;

import java.util.UUID;

public interface ProjectProvisioningPort {

    // 시스템 계정 소유로 공개 프로젝트 생성 — 생성된 프로젝트 ID 반환
    UUID createSystemProject(String githubRepoUrl, String name);
}
