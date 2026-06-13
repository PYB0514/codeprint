// WarningSuppressionService 단위 테스트 — suppress 멱등성·해제·조회 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.WarningSuppression;
import com.codeprint.domain.graph.WarningSuppressionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarningSuppressionServiceTest {

    @Mock private WarningSuppressionRepository repository;

    private WarningSuppressionService service;

    @BeforeEach
    void setUp() {
        service = new WarningSuppressionService(repository);
    }

    @Test
    @DisplayName("suppress — 아직 숨겨지지 않았으면 저장")
    void suppress_notExists_saves() {
        UUID projectId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprint(projectId, "fp1")).thenReturn(false);

        service.suppress(projectId, "fp1", "CROSS_DOMAIN_CALL");

        verify(repository).save(any(WarningSuppression.class));
    }

    @Test
    @DisplayName("suppress — 이미 숨겨져 있으면 저장하지 않음(멱등)")
    void suppress_alreadyExists_noSave() {
        UUID projectId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprint(projectId, "fp1")).thenReturn(true);

        service.suppress(projectId, "fp1", "CROSS_DOMAIN_CALL");

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("unsuppress — 해당 fingerprint suppress 삭제")
    void unsuppress_deletes() {
        UUID projectId = UUID.randomUUID();

        service.unsuppress(projectId, "fp1");

        verify(repository).deleteByProjectIdAndFingerprint(projectId, "fp1");
    }

    @Test
    @DisplayName("getSuppressedFingerprints — 리포지토리의 fingerprint 집합 반환")
    void getSuppressedFingerprints_returnsFromRepo() {
        UUID projectId = UUID.randomUUID();
        when(repository.findFingerprintsByProjectId(projectId)).thenReturn(Set.of("fp1", "fp2"));

        Set<String> result = service.getSuppressedFingerprints(projectId);

        assertThat(result).containsExactlyInAnyOrder("fp1", "fp2");
    }
}
