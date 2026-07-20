// 오탐 신고 엔드포인트의 graphId 파싱 회귀 테스트 — 잘못된 UUID로 500 대신 400 반환하는지 확인
package com.codeprint.interfaces.api;

import com.codeprint.application.graph.FpReportService;
import com.codeprint.application.graph.GraphFacade;
import com.codeprint.application.graph.WarningSuppressionService;
import com.codeprint.domain.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WarningControllerTest {

    @Mock private WarningSuppressionService warningSuppressionService;
    @Mock private FpReportService fpReportService;
    @Mock private GraphFacade graphFacade;

    private WarningController controller;

    private final UUID projectId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private User user;

    @BeforeEach
    void setUp() {
        controller = new WarningController(warningSuppressionService, fpReportService, graphFacade);
        user = mock(User.class);
        lenient().when(user.getId()).thenReturn(userId);
    }

    @Test
    @DisplayName("reportFalsePositive — graphId가 UUID 형식이 아니면 400을 반환하고 서비스 호출 안 함")
    void reportFalsePositive_malformedGraphId_returnsBadRequest() {
        WarningController.ReportFpRequest req = new WarningController.ReportFpRequest(
                "fp1", "DEAD_CODE", null, "not-a-uuid", "message", "src/Foo.java", 10, 4, 20);

        ResponseEntity<Void> response = controller.reportFalsePositive(projectId, req, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        verifyNoInteractions(fpReportService);
    }

    @Test
    @DisplayName("reportFalsePositive — graphId가 정상 UUID면 파싱해 서비스에 전달")
    void reportFalsePositive_validGraphId_delegatesToService() {
        UUID graphId = UUID.randomUUID();
        WarningController.ReportFpRequest req = new WarningController.ReportFpRequest(
                "fp1", "DEAD_CODE", null, graphId.toString(), "message", "src/Foo.java", 10, 4, 20);

        ResponseEntity<Void> response = controller.reportFalsePositive(projectId, req, user);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        verify(fpReportService).reportFalsePositive(projectId, graphId, "fp1", "DEAD_CODE", userId, null,
                "message", "src/Foo.java", 10, 4, 20);
    }
}
