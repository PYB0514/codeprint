// project 컨텍스트가 team 컨텍스트를 직접 참조하지 않기 위한 포트 — ID만 주고받는다
package com.codeprint.domain.project.port;

import java.util.UUID;

public interface TeamAccessPort {

    // 이 프로젝트가 속한 팀의 멤버인지(OWNER·MEMBER 구분 없이 동일 권한)
    boolean hasAccessViaTeam(UUID projectId, UUID userId);
}
