// collaboration 컨텍스트가 graph 컨텍스트를 직접 참조하지 않기 위한 포트 — ID만 주고받는다
package com.codeprint.domain.collaboration.port;

import java.util.UUID;

public interface GraphAccessPort {

    // 이 그래프에 접근 권한이 있는지(소유자 또는 팀 배분) — 없으면 예외
    void verifyAccess(UUID graphId, UUID userId);
}
