// AnalysisApplicationService 단위 테스트 — 분석 시작 조율(save→run)·조회 미존재 throw·브랜치 폴백 회귀 방지
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnalysisApplicationServiceTest {

    @Mock
    private AnalysisRepository analysisRepository;
    @Mock
    private AnalysisRunner analysisRunner;
    @Mock
    private GitHubApiClient gitHubApiClient;

    private AnalysisApplicationService service() {
        return new AnalysisApplicationService(analysisRepository, analysisRunner, gitHubApiClient);
    }

    @Test
    @DisplayName("startAnalysis는 PENDING 레코드를 저장하고 그 ID로 비동기 분석을 실행한다(직전 분석 없음)")
    void startAnalysis_저장후_실행() {
        UUID projectId = UUID.randomUUID();

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        // PENDING 상태로 생성·반환
        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        assertThat(result.getBranch()).isEqualTo("main");
        // save가 호출되고, runner.run이 생성된 분석의 ID·전달 파라미터로 호출됨(트랜잭션 커밋 전 URL 선전달)
        verify(analysisRepository).save(result);
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"));
    }

    @Test
    @DisplayName("startAnalysis는 직전 DONE 분석과 커밋 SHA가 같으면 새로 분석하지 않고 기존 결과를 반환한다")
    void startAnalysis_커밋동일_스킵() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-abc");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main")).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok")).thenReturn("sha-abc");

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isSameAs(prev);
        verify(analysisRepository, never()).save(any());
        verifyNoInteractions(analysisRunner);
    }

    @Test
    @DisplayName("startAnalysis는 커밋 SHA가 바뀌었으면 새로 분석한다")
    void startAnalysis_커밋변경_새로분석() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-old");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main")).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok")).thenReturn("sha-new");

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"));
    }

    @Test
    @DisplayName("startAnalysis는 직전 분석이 DONE이 아니면(RUNNING) 스킵 판정 없이 새로 분석한다")
    void startAnalysis_직전분석_RUNNING이면_스킵안함() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        ReflectionTestUtils.setField(prev, "status", AnalysisStatus.RUNNING);
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main")).thenReturn(Optional.of(prev));

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
        verifyNoInteractions(gitHubApiClient);
    }

    @Test
    @DisplayName("startAnalysis는 커밋 SHA 조회가 실패하면 스킵하지 않고 안전하게 새로 분석한다")
    void startAnalysis_SHA조회실패_안전하게_새로분석() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-abc");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main")).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok"))
                .thenThrow(new RuntimeException("GitHub API 500"));

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
    }

    @Test
    @DisplayName("getAnalysis는 존재하면 반환한다")
    void getAnalysis_존재() {
        UUID analysisId = UUID.randomUUID();
        AnalysisResult analysis = AnalysisResult.create(UUID.randomUUID(), "main");
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.of(analysis));

        assertThat(service().getAnalysis(analysisId)).isSameAs(analysis);
    }

    @Test
    @DisplayName("getAnalysis는 미존재 시 IllegalArgumentException을 던진다")
    void getAnalysis_미존재_예외() {
        UUID analysisId = UUID.randomUUID();
        when(analysisRepository.findById(analysisId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getAnalysis(analysisId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(analysisId.toString());
    }

    @Test
    @DisplayName("getBranchMap은 branch가 null인 분석을 'default'로 매핑한다")
    void getBranchMap_null_브랜치_default_폴백() {
        AnalysisResult withBranch = AnalysisResult.create(UUID.randomUUID(), "feature");
        AnalysisResult noBranch = AnalysisResult.create(UUID.randomUUID(), null);
        List<UUID> ids = List.of(withBranch.getId(), noBranch.getId());
        when(analysisRepository.findAllById(ids)).thenReturn(List.of(withBranch, noBranch));

        Map<UUID, String> map = service().getBranchMap(ids);

        assertThat(map.get(withBranch.getId())).isEqualTo("feature");
        assertThat(map.get(noBranch.getId())).isEqualTo("default");
    }

    @Test
    @DisplayName("getLatestAnalysisByBranch는 리포지토리 조회 결과를 그대로 전달한다")
    void getLatestByBranch_위임() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult analysis = AnalysisResult.create(projectId, "dev");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "dev")).thenReturn(Optional.of(analysis));

        assertThat(service().getLatestAnalysisByBranch(projectId, "dev")).containsSame(analysis);
    }
}
