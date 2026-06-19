// 프로젝트별 의도 아키텍처 저장소 인터페이스
package com.codeprint.domain.graph;

import java.util.Optional;
import java.util.UUID;

public interface ArchitectureIntentRepository {

    // 프로젝트 ID로 저장된 의도 아키텍처 JSON 조회
    Optional<String> findJsonByProjectId(UUID projectId);

    // 프로젝트의 의도 아키텍처를 JSON 문자열로 저장(없으면 삽입, 있으면 갱신)
    void upsert(UUID projectId, String intentJson);

    // 프로젝트의 의도 아키텍처 삭제
    void deleteByProjectId(UUID projectId);
}
