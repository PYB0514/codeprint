// Featured ProjectProvisioningPort의 project 컨텍스트 어댑터 — 시스템 계정 소유 공개 프로젝트 생성
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.featured.port.ProjectProvisioningPort;
import com.codeprint.domain.project.Project;
import com.codeprint.domain.project.ProjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FeaturedProjectProvisioningAdapter implements ProjectProvisioningPort {

    // "오늘의 공개레포" 전용 시스템 계정 — V49 마이그레이션에서 시딩
    public static final UUID SYSTEM_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private final ProjectRepository projectRepository;

    // 시스템 계정 소유로 공개 프로젝트 생성
    @Override
    @Transactional
    public UUID createSystemProject(String githubRepoUrl, String name) {
        Project project = Project.create(SYSTEM_USER_ID, githubRepoUrl, name, null);
        project.makePublic();
        return projectRepository.save(project).getId();
    }
}
