// PrWebhookTargetAdapter 단위 테스트 — 프로젝트별 webhook 시크릿 서명 검증·후보 선택 분기 회귀 방지
package com.codeprint.infrastructure.adapter;

import com.codeprint.application.project.ProjectQueryService;
import com.codeprint.application.user.UserQueryService;
import com.codeprint.domain.project.Project;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PrWebhookTargetAdapterTest {

    private ProjectQueryService projectQueryService;
    private UserQueryService userQueryService;
    private PrWebhookTargetAdapter adapter;

    private static final byte[] BODY = "{\"action\":\"opened\"}".getBytes(StandardCharsets.UTF_8);

    @BeforeEach
    void setUp() {
        projectQueryService = mock(ProjectQueryService.class);
        userQueryService = mock(UserQueryService.class);
        adapter = new PrWebhookTargetAdapter(projectQueryService, userQueryService);
    }

    // 프로젝트별 발급된 실제 시크릿으로 유효한 X-Hub-Signature-256 헤더 계산
    private String validSig(Project project, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(project.getWebhookSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(body);
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return "sha256=" + sb;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("연결(시크릿 발급)되지 않은 프로젝트만 있으면 서명이 어떤 값이든 대상 없음")
    void resolve_projectNotConnected_empty() {
        Project project = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "n", null);
        when(projectQueryService.findByRepoUrl("https://github.com/owner/repo")).thenReturn(List.of(project));

        Optional<?> result = adapter.resolve("owner/repo", BODY, "sha256=whatever");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("시크릿은 연결됐지만 서명이 그 시크릿과 불일치하면 대상 없음")
    void resolve_wrongSignature_empty() {
        Project project = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "n", null);
        project.generateWebhookSecret();
        when(projectQueryService.findByRepoUrl("https://github.com/owner/repo")).thenReturn(List.of(project));

        Optional<?> result = adapter.resolve("owner/repo", BODY, "sha256=deadbeef");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("서명이 일치해도 소유자가 GitHub 토큰이 없으면 대상 없음")
    void resolve_validSignature_noToken_empty() {
        Project project = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "n", null);
        project.generateWebhookSecret();
        when(projectQueryService.findByRepoUrl("https://github.com/owner/repo")).thenReturn(List.of(project));
        when(userQueryService.findGithubAccessToken(project.getUserId())).thenReturn(Optional.empty());

        Optional<?> result = adapter.resolve("owner/repo", BODY, validSig(project, BODY));

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("서명이 일치하고 소유자 토큰이 있으면 해당 프로젝트를 대상으로 반환")
    void resolve_validSignatureAndToken_returnsTarget() {
        Project project = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "n", null);
        project.generateWebhookSecret();
        when(projectQueryService.findByRepoUrl("https://github.com/owner/repo")).thenReturn(List.of(project));
        when(userQueryService.findGithubAccessToken(project.getUserId())).thenReturn(Optional.of("gh-token"));

        var result = adapter.resolve("owner/repo", BODY, validSig(project, BODY));

        assertThat(result).isPresent();
        assertThat(result.get().projectId()).isEqualTo(project.getId());
        assertThat(result.get().ownerId()).isEqualTo(project.getUserId());
        assertThat(result.get().githubToken()).isEqualTo("gh-token");
    }

    @Test
    @DisplayName("여러 후보 중 서명이 일치하는 프로젝트만 골라 반환 — 다른 프로젝트 시크릿으로는 매칭되지 않는다")
    void resolve_multipleCandidates_onlySignatureMatchWins() {
        Project other = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "other", null);
        other.generateWebhookSecret();
        Project mine = Project.create(UUID.randomUUID(), "https://github.com/owner/repo", "mine", null);
        mine.generateWebhookSecret();
        when(projectQueryService.findByRepoUrl("https://github.com/owner/repo")).thenReturn(List.of(other, mine));
        when(userQueryService.findGithubAccessToken(mine.getUserId())).thenReturn(Optional.of("tok"));

        var result = adapter.resolve("owner/repo", BODY, validSig(mine, BODY));

        assertThat(result).isPresent();
        assertThat(result.get().projectId()).isEqualTo(mine.getId());
    }
}
