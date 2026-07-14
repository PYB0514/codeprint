// Graph SnapshotReferencePort의 community 컨텍스트 어댑터
package com.codeprint.infrastructure.adapter;

import com.codeprint.domain.graph.port.SnapshotReferencePort;
import com.codeprint.infrastructure.persistence.community.PostGraphSnapshotJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class GraphSnapshotReferenceAdapter implements SnapshotReferencePort {

    private final PostGraphSnapshotJpaRepository postGraphSnapshotJpaRepository;

    @Override
    public Set<UUID> findReferencedGraphIds(UUID projectId) {
        return new HashSet<>(postGraphSnapshotJpaRepository.findDistinctGraphIdsByProjectId(projectId));
    }
}
