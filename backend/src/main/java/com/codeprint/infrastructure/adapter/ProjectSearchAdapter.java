// Graph ProjectSearchPort의 project 컨텍스트 어댑터 — 공개 프로젝트 검색 위임
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.domain.graph.port.ProjectSearchPort;
import com.codeprint.domain.project.Project;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
public class ProjectSearchAdapter implements ProjectSearchPort {

    private final ProjectQueryService projectQueryService;

    @Override
    public List<Project> searchPublic(String query) {
        return projectQueryService.searchPublic(query);
    }
}
