// ArchitectureIntentService 단위 테스트 — 의도 아키텍처 JSON 직렬화 왕복·파싱 폴백 회귀 방지
package com.codeprint.application.graph;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.codeprint.domain.graph.ArchitectureIntentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureIntentServiceTest {

    private final UUID projectId = UUID.randomUUID();

    // 인메모리 저장소를 주입한 서비스 생성 (@RequiredArgsConstructor 필드 순서: repository, objectMapper)
    private ArchitectureIntentService serviceWith(InMemoryRepository repo) {
        return new ArchitectureIntentService(repo, new ObjectMapper());
    }

    @Test
    @DisplayName("저장 후 조회 — 모듈·글로브·규칙 왕복 보존")
    void saveThenFind_roundTrip() {
        InMemoryRepository repo = new InMemoryRepository();
        ArchitectureIntentService svc = serviceWith(repo);
        ArchitectureIntent intent = new ArchitectureIntent(
                List.of(
                        new ArchitectureIntent.Module("domain", List.of("**/domain/**")),
                        new ArchitectureIntent.Module("infra", List.of("**/infrastructure/**", "**/infra/**"))),
                List.of(new ArchitectureIntent.DependencyRule("domain", "infra")));

        svc.save(projectId, intent);
        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).hasSize(2);
        assertThat(loaded.modules().get(0).name()).isEqualTo("domain");
        assertThat(loaded.modules().get(0).globs()).containsExactly("**/domain/**");
        assertThat(loaded.modules().get(1).globs()).containsExactly("**/infrastructure/**", "**/infra/**");
        assertThat(loaded.rules()).hasSize(1);
        assertThat(loaded.rules().get(0).from()).isEqualTo("domain");
        assertThat(loaded.rules().get(0).to()).isEqualTo("infra");
    }

    @Test
    @DisplayName("저장 후 조회 — 모듈 선언 순서 보존(moduleOf 첫 매칭 우선 전제)")
    void saveThenFind_preservesModuleOrder() {
        InMemoryRepository repo = new InMemoryRepository();
        ArchitectureIntentService svc = serviceWith(repo);
        ArchitectureIntent intent = new ArchitectureIntent(
                List.of(
                        new ArchitectureIntent.Module("a", List.of("**/a/**")),
                        new ArchitectureIntent.Module("b", List.of("**/b/**")),
                        new ArchitectureIntent.Module("c", List.of("**/c/**"))),
                List.of(new ArchitectureIntent.DependencyRule("a", "c")));

        svc.save(projectId, intent);
        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).extracting(ArchitectureIntent.Module::name)
                .containsExactly("a", "b", "c");
    }

    @Test
    @DisplayName("빈 의도 저장 후 조회 — isEmpty 유지")
    void saveThenFind_emptyIntent() {
        InMemoryRepository repo = new InMemoryRepository();
        ArchitectureIntentService svc = serviceWith(repo);

        svc.save(projectId, new ArchitectureIntent(List.of(), List.of()));
        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).isEmpty();
        assertThat(loaded.rules()).isEmpty();
        assertThat(loaded.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("저장된 의도 없음 — 빈 Optional")
    void findByProjectId_absent() {
        ArchitectureIntentService svc = serviceWith(new InMemoryRepository());
        assertThat(svc.findByProjectId(projectId)).isEmpty();
    }

    @Test
    @DisplayName("손상된 JSON — 예외 없이 빈 의도로 폴백")
    void parse_malformedJson_fallsBackToEmpty() {
        InMemoryRepository repo = new InMemoryRepository();
        repo.store.put(projectId, "{ this is not valid json");
        ArchitectureIntentService svc = serviceWith(repo);

        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).isEmpty();
        assertThat(loaded.rules()).isEmpty();
        assertThat(loaded.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("필드 누락 JSON — modules/rules 없어도 빈 컬렉션으로 안전 파싱")
    void parse_missingFields_yieldsEmptyCollections() {
        InMemoryRepository repo = new InMemoryRepository();
        repo.store.put(projectId, "{}");
        ArchitectureIntentService svc = serviceWith(repo);

        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).isEmpty();
        assertThat(loaded.rules()).isEmpty();
    }

    @Test
    @DisplayName("모듈에 globs 키 누락 — 빈 글로브 목록으로 파싱")
    void parse_moduleWithoutGlobs() {
        InMemoryRepository repo = new InMemoryRepository();
        repo.store.put(projectId, "{\"modules\":[{\"name\":\"domain\"}],\"rules\":[]}");
        ArchitectureIntentService svc = serviceWith(repo);

        ArchitectureIntent loaded = svc.findByProjectId(projectId).orElseThrow();

        assertThat(loaded.modules()).hasSize(1);
        assertThat(loaded.modules().get(0).name()).isEqualTo("domain");
        assertThat(loaded.modules().get(0).globs()).isEmpty();
    }

    @Test
    @DisplayName("삭제 후 조회 — 빈 Optional")
    void delete_thenFindEmpty() {
        InMemoryRepository repo = new InMemoryRepository();
        ArchitectureIntentService svc = serviceWith(repo);
        svc.save(projectId, new ArchitectureIntent(
                List.of(new ArchitectureIntent.Module("domain", List.of("**/domain/**"))),
                List.of(new ArchitectureIntent.DependencyRule("domain", "infra"))));

        svc.delete(projectId);

        assertThat(svc.findByProjectId(projectId)).isEmpty();
    }

    // upsert된 JSON을 메모리에 그대로 보관하는 테스트 더블
    private static class InMemoryRepository implements ArchitectureIntentRepository {
        final Map<UUID, String> store = new HashMap<>();

        @Override
        public Optional<String> findJsonByProjectId(UUID projectId) {
            return Optional.ofNullable(store.get(projectId));
        }

        @Override
        public void upsert(UUID projectId, String intentJson) {
            store.put(projectId, intentJson);
        }

        @Override
        public void deleteByProjectId(UUID projectId) {
            store.remove(projectId);
        }
    }
}
