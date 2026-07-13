// BENCH_SPEC.md §1 공통(룰 무관) 케이스 — 결정론·fingerprint 안정성·ignore opt-out·DDD/레이어드 상호배타 라우팅
package com.codeprint.bench;

import com.codeprint.domain.graph.ArchitectureIntent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BenchCommonCasesTest {

    private static final Set<String> DDD_TYPES = Set.of(
            "DB_LAYER_BYPASS", "CROSS_CONTEXT_IMPORT", "DOMAIN_IMPORTS_INFRA",
            "INTERFACES_IMPORTS_INFRA", "CROSS_DOMAIN_CALL");
    private static final Set<String> LAYERED_TYPES = Set.of(
            "LAYERED_REVERSE_DEPENDENCY", "LAYERED_BYPASS");

    @Test
    @DisplayName("c-determinism: 동일 픽스처 3회 연속 분석 시 경고 목록이 완전히 동일하다")
    void c_determinism() {
        Path fixture = fixture("cyclic-with-orphan");

        List<String> run1 = fingerprints(BenchPipelineRunner.run(fixture));
        List<String> run2 = fingerprints(BenchPipelineRunner.run(fixture));
        List<String> run3 = fingerprints(BenchPipelineRunner.run(fixture));

        assertThat(run1).isNotEmpty();
        assertThat(run2).isEqualTo(run1);
        assertThat(run3).isEqualTo(run1);
    }

    @Test
    @DisplayName("c-fingerprint-stable: 재분석해도 동일 경고의 fingerprint가 불변한다")
    void c_fingerprint_stable() {
        Path fixture = fixture("cyclic-with-orphan");

        Set<String> first = Set.copyOf(fingerprints(BenchPipelineRunner.run(fixture)));
        Set<String> second = Set.copyOf(fingerprints(BenchPipelineRunner.run(fixture)));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("c-ignore-optout: intent ignore 패턴이 매치 경고만 억제하고 나머지는 보존한다")
    void c_ignore_optout() {
        Path fixture = fixture("cyclic-with-orphan");

        List<Map<String, Object>> baseline = BenchPipelineRunner.run(fixture);
        assertThat(types(baseline)).contains("CYCLIC_IMPORT", "DEAD_CODE");

        ArchitectureIntent intent = new ArchitectureIntent(List.of(), List.of(),
                List.of(new ArchitectureIntent.IgnoreRule("CYCLIC_IMPORT", null, null)));
        List<Map<String, Object>> filtered = BenchPipelineRunner.run(fixture, intent);

        assertThat(types(filtered)).doesNotContain("CYCLIC_IMPORT").contains("DEAD_CODE");
    }

    @Test
    @DisplayName("c-ddd-routing: DDD 게이트 충족 픽스처는 DDD 5종만 발화하고 LAYERED_*는 미발화한다")
    void c_ddd_routing() {
        Path fixture = fixture("ddd-routing");

        List<Map<String, Object>> warnings = BenchPipelineRunner.run(fixture);

        assertThat(types(warnings)).contains("DB_LAYER_BYPASS");
        assertThat(types(warnings)).doesNotContainAnyElementsOf(LAYERED_TYPES);
    }

    @Test
    @DisplayName("c-layered-routing: 비DDD 레이어드 픽스처는 LAYERED_*만 발화하고 DDD 5종은 미발화한다")
    void c_layered_routing() {
        Path fixture = fixture("layered-routing");

        List<Map<String, Object>> warnings = BenchPipelineRunner.run(fixture);

        assertThat(types(warnings)).contains("LAYERED_REVERSE_DEPENDENCY");
        assertThat(types(warnings)).doesNotContainAnyElementsOf(DDD_TYPES);
    }

    private static List<String> fingerprints(List<Map<String, Object>> warnings) {
        return warnings.stream().map(w -> String.valueOf(w.get("fingerprint"))).toList();
    }

    private static Set<String> types(List<Map<String, Object>> warnings) {
        return warnings.stream().map(w -> String.valueOf(w.get("type"))).collect(Collectors.toSet());
    }

    private static Path fixture(String name) {
        URL url = BenchCommonCasesTest.class.getClassLoader().getResource("bench/common/" + name);
        if (url == null) throw new IllegalStateException("픽스처 없음: " + name);
        try {
            return Path.of(url.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }
}
