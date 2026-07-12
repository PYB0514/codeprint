// FpReportService 단위 테스트 — 오탐 신고 멱등성·조회 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.FpReport;
import com.codeprint.domain.graph.FpReportRepository;
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
class FpReportServiceTest {

    @Mock private FpReportRepository repository;

    private FpReportService service;

    @BeforeEach
    void setUp() {
        service = new FpReportService(repository);
    }

    @Test
    @DisplayName("reportFalsePositive — 아직 신고하지 않았으면 저장")
    void report_notExists_saves() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprintAndReporterId(projectId, "fp1", reporterId)).thenReturn(false);

        service.reportFalsePositive(projectId, "fp1", "CROSS_DOMAIN_CALL", reporterId, "실제 사용 중인 코드입니다");

        verify(repository).save(any(FpReport.class));
    }

    @Test
    @DisplayName("reportFalsePositive — 동일 사용자가 이미 신고했으면 저장하지 않음(멱등)")
    void report_alreadyExists_noSave() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.existsByProjectIdAndFingerprintAndReporterId(projectId, "fp1", reporterId)).thenReturn(true);

        service.reportFalsePositive(projectId, "fp1", "CROSS_DOMAIN_CALL", reporterId, null);

        verify(repository, never()).save(any());
    }

    @Test
    @DisplayName("getReportedFingerprintsByUser — 리포지토리의 fingerprint 집합 반환")
    void getReported_returnsFromRepo() {
        UUID projectId = UUID.randomUUID();
        UUID reporterId = UUID.randomUUID();
        when(repository.findFingerprintsByProjectIdAndReporterId(projectId, reporterId)).thenReturn(Set.of("fp1", "fp2"));

        Set<String> result = service.getReportedFingerprintsByUser(projectId, reporterId);

        assertThat(result).containsExactlyInAnyOrder("fp1", "fp2");
    }
}
