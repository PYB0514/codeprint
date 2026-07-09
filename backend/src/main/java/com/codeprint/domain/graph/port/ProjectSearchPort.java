// Graph 도메인에서 project 컨텍스트의 공개 프로젝트 검색을 조회하는 포트
package com.codeprint.domain.graph.port;

import com.codeprint.domain.project.Project;

import java.util.List;

public interface ProjectSearchPort {

    // 공개 프로젝트 검색 — 이름·레포 URL로 필터 (query가 null이면 전체)
    List<Project> searchPublic(String query);
}
