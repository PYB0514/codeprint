// AnalysisApplicationService 단위 테스트 — 분석 시작 조율(save→run)·조회 미존재 throw·브랜치 폴백 회귀 방지
package com.codeprint.application.analysis;

import com.codeprint.domain.analysis.AnalysisRepository;
import com.codeprint.domain.analysis.AnalysisResult;
import com.codeprint.domain.analysis.AnalysisStatus;
import com.codeprint.infrastructure.config.AnalysisConcurrencyGuard;
import com.codeprint.infrastructure.github.GitHubApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private AnalysisConcurrencyGuard concurrencyGuard;

    // 기본값: 슬롯 예약 성공 — 포화 시나리오를 다루는 테스트에서만 개별적으로 false로 덮어씀
    @BeforeEach
    void setUp() {
        lenient().when(concurrencyGuard.tryAcquire()).thenReturn(true);
    }

    private AnalysisApplicationService service() {
        return new AnalysisApplicationService(analysisRepository, analysisRunner, gitHubApiClient, concurrencyGuard);
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
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"), eq((String) null));
    }

    @Test
    @DisplayName("startAnalysis는 직전 DONE 분석과 커밋 SHA가 같으면 새로 분석하지 않고 기존 결과를 반환한다")
    void startAnalysis_커밋동일_스킵() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-abc");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main", null)).thenReturn(Optional.of(prev));
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
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main", null)).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok")).thenReturn("sha-new");

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"), eq((String) null));
    }

    @Test
    @DisplayName("startAnalysis는 ref(특정 커밋)를 지정하면 직전 분석 커밋과 무관하게 스킵 판정 자체를 하지 않고 그 ref로 항상 새로 분석한다")
    void startAnalysis_ref지정시_스킵판정없이_새로분석() {
        UUID projectId = UUID.randomUUID();

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok", "sha-abc");

        verify(analysisRepository).save(result);
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"), eq("sha-abc"));
        // ref가 있으면 직전 분석 조회·최신 SHA 조회(스킵 판정 자체)를 하지 않음 — 단, 크기 상한 검사는 ref 유무와 무관하게 항상 수행
        verify(analysisRepository, never()).findLatestByProjectIdAndBranch(any(), any(), any());
        verify(gitHubApiClient, never()).fetchLatestCommitSha(any(), any(), any());
    }

    @Test
    @DisplayName("startAnalysis는 직전 분석이 DONE이 아니면(RUNNING) 스킵 판정 없이 새로 분석한다")
    void startAnalysis_직전분석_RUNNING이면_스킵안함() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        ReflectionTestUtils.setField(prev, "status", AnalysisStatus.RUNNING);
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main", null)).thenReturn(Optional.of(prev));

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
        // 직전 분석이 RUNNING이면 커밋 SHA 스킵 판정은 안 하지만, 크기 상한 검사는 여전히 수행
        verify(gitHubApiClient, never()).fetchLatestCommitSha(any(), any(), any());
    }

    @Test
    @DisplayName("startAnalysis는 커밋 SHA 조회가 실패하면 스킵하지 않고 안전하게 새로 분석한다")
    void startAnalysis_SHA조회실패_안전하게_새로분석() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-abc");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main", null)).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok"))
                .thenThrow(new RuntimeException("GitHub API 500"));

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result).isNotSameAs(prev);
        verify(analysisRepository).save(result);
    }

    @Test
    @DisplayName("startAnalysis는 레포 크기가 상한(1GB)을 넘으면 거부하고 저장·실행하지 않는다")
    void startAnalysis_레포크기_상한초과_거부() {
        UUID projectId = UUID.randomUUID();
        when(gitHubApiClient.fetchRepoSizeKb("https://github.com/a/b", "tok")).thenReturn(1_048_577L);

        assertThatThrownBy(() -> service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok"))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(analysisRepository);
        verifyNoInteractions(analysisRunner);
    }

    @Test
    @DisplayName("startAnalysis는 레포 크기 조회가 실패하면 상한 검사를 통과시키고 정상 진행한다(fail-open)")
    void startAnalysis_레포크기조회실패_안전하게_진행() {
        UUID projectId = UUID.randomUUID();
        when(gitHubApiClient.fetchRepoSizeKb("https://github.com/a/b", "tok"))
                .thenThrow(new RuntimeException("GitHub API 500"));

        AnalysisResult result = service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        assertThat(result.getStatus()).isEqualTo(AnalysisStatus.PENDING);
        verify(analysisRepository).save(result);
        verify(analysisRunner).run(eq(result.getId()), eq(projectId), eq("https://github.com/a/b"), eq("main"), eq("tok"), eq((String) null));
    }

    @Test
    @DisplayName("startAnalysis는 슬롯 예약에 실패하면(포화) 429로 거부하고 레포 크기 조회조차 하지 않는다")
    void startAnalysis_동시분석한도초과_거부() {
        UUID projectId = UUID.randomUUID();
        when(concurrencyGuard.tryAcquire()).thenReturn(false);

        assertThatThrownBy(() -> service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok"))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> assertThat(((ResponseStatusException) ex).getStatusCode())
                        .isEqualTo(HttpStatus.TOO_MANY_REQUESTS));

        verifyNoInteractions(gitHubApiClient);
        verifyNoInteractions(analysisRepository);
        verifyNoInteractions(analysisRunner);
        // 애초에 슬롯을 못 얻었으니 반납할 것도 없다
        verify(concurrencyGuard, never()).release();
    }

    @Test
    @DisplayName("startAnalysis는 정상 제출 시 슬롯을 반납하지 않는다(소유권이 AnalysisRunner로 이전)")
    void startAnalysis_정상제출_슬롯유지() {
        UUID projectId = UUID.randomUUID();

        service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        verify(concurrencyGuard, never()).release();
    }

    @Test
    @DisplayName("startAnalysis는 동일 커밋 스킵 시 예약했던 슬롯을 반납한다")
    void startAnalysis_동일커밋스킵_슬롯반납() {
        UUID projectId = UUID.randomUUID();
        AnalysisResult prev = AnalysisResult.create(projectId, "main");
        prev.complete("sha-abc");
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "main", null)).thenReturn(Optional.of(prev));
        when(gitHubApiClient.fetchLatestCommitSha("https://github.com/a/b", "main", "tok")).thenReturn("sha-abc");

        service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok");

        verify(concurrencyGuard).release();
    }

    @Test
    @DisplayName("startAnalysis는 레포 크기 초과로 거부될 때도 예약했던 슬롯을 반납한다")
    void startAnalysis_크기초과거부_슬롯반납() {
        UUID projectId = UUID.randomUUID();
        when(gitHubApiClient.fetchRepoSizeKb("https://github.com/a/b", "tok")).thenReturn(1_048_577L);

        assertThatThrownBy(() -> service().startAnalysis(projectId, "main", "https://github.com/a/b", "tok"))
                .isInstanceOf(IllegalArgumentException.class);

        verify(concurrencyGuard).release();
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
        when(analysisRepository.findLatestByProjectIdAndBranch(projectId, "dev", null)).thenReturn(Optional.of(analysis));

        assertThat(service().getLatestAnalysisByBranch(projectId, "dev", null)).containsSame(analysis);
    }
}
