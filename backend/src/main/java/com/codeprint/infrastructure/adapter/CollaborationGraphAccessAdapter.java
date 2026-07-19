// collaboration 컨텍스트 GraphAccessPort의 graph 컨텍스트 어댑터 — 소유·팀 접근 검증 위임
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.graph.GraphFacade;
import com.codeprint.domain.collaboration.port.GraphAccessPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CollaborationGraphAccessAdapter implements GraphAccessPort {

    private final GraphFacade graphFacade;

    @Override
    public void verifyAccess(UUID graphId, UUID userId) {
        graphFacade.verifyGraphOwnership(graphId, userId);
    }
}
