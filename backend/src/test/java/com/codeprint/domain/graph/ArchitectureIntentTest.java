// ArchitectureIntent 단위 테스트 — 글로브 매칭·모듈 판별·금지 규칙(INTENT_DRIFT 토대) 회귀 방지
package com.codeprint.domain.graph;

import com.codeprint.domain.graph.ArchitectureIntent.DependencyRule;
import com.codeprint.domain.graph.ArchitectureIntent.Module;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.codeprint.domain.graph.ArchitectureIntent.globMatches;
import static org.assertj.core.api.Assertions.assertThat;

class ArchitectureIntentTest {

    @Test
    @DisplayName("글로브 ** — 슬래시 포함 임의 경로 매칭")
    void glob_doubleStar_crossesSlash() {
        assertThat(globMatches("**/domain/**", "src/main/domain/User.java")).isTrue();
        assertThat(globMatches("**/domain/**", "a/b/domain/c/d.java")).isTrue();
        // 선행 세그먼트가 domain이면 앞 슬래시가 없어 **/domain/** 에 매칭되지 않음
        assertThat(globMatches("**/domain/**", "domain/User.java")).isFalse();
        assertThat(globMatches("**/application/**", "src/domain/User.java")).isFalse();
    }

    @Test
    @DisplayName("글로브 * — 슬래시를 넘지 않음")
    void glob_singleStar_stopsAtSlash() {
        assertThat(globMatches("*.java", "User.java")).isTrue();
        assertThat(globMatches("*.java", "a/User.java")).isFalse();
        assertThat(globMatches("**/*.java", "a/User.java")).isTrue();
        // 루트 레벨 파일은 앞에 슬래시가 없어 **/*.java 에 매칭되지 않음
        assertThat(globMatches("**/*.java", "User.java")).isFalse();
    }

    @Test
    @DisplayName("글로브 ? — 슬래시 제외 한 글자")
    void glob_question_singleChar() {
        assertThat(globMatches("?.java", "A.java")).isTrue();
        assertThat(globMatches("?.java", "AB.java")).isFalse();
        assertThat(globMatches("?.java", "/.java")).isFalse();
    }

    @Test
    @DisplayName("글로브 — 정규식 특수문자는 리터럴로 이스케이프")
    void glob_escapesRegexSpecials() {
        assertThat(globMatches("a.b.txt", "a.b.txt")).isTrue();
        assertThat(globMatches("a.b.txt", "aXbYtxt")).isFalse();   // 점은 리터럴
        assertThat(globMatches("a+b", "a+b")).isTrue();
        assertThat(globMatches("a+b", "aaab")).isFalse();           // +는 리터럴(정규식 수량자 아님)
    }

    private ArchitectureIntent sampleIntent() {
        return new ArchitectureIntent(
                List.of(new Module("domain", List.of("**/domain/**")),
                        new Module("app", List.of("**/application/**"))),
                List.of(new DependencyRule("domain", "app")));
    }

    @Test
    @DisplayName("moduleOf — 글로브로 파일의 모듈 판별, 없으면 null")
    void moduleOf_matchesByGlob() {
        ArchitectureIntent intent = sampleIntent();
        assertThat(intent.moduleOf("src/domain/User.java")).isEqualTo("domain");
        assertThat(intent.moduleOf("src/application/Svc.java")).isEqualTo("app");
        assertThat(intent.moduleOf("src/web/Ctrl.java")).isNull();
        assertThat(intent.moduleOf(null)).isNull();
        assertThat(intent.moduleOf("")).isNull();
    }

    @Test
    @DisplayName("moduleOf — 여러 모듈이 겹치면 먼저 선언된 모듈 우선")
    void moduleOf_firstDeclaredWins() {
        ArchitectureIntent overlap = new ArchitectureIntent(
                List.of(new Module("first", List.of("**/x/**")),
                        new Module("second", List.of("**/x/**"))),
                List.of());
        assertThat(overlap.moduleOf("a/x/b.java")).isEqualTo("first");
    }

    @Test
    @DisplayName("isForbidden — from→to 금지 규칙 매칭, 방향 구분")
    void isForbidden_directional() {
        ArchitectureIntent intent = sampleIntent();
        assertThat(intent.isForbidden("domain", "app")).isTrue();
        assertThat(intent.isForbidden("app", "domain")).isFalse();   // 반대 방향은 금지 아님
        assertThat(intent.isForbidden("domain", "infra")).isFalse();
    }

    @Test
    @DisplayName("isForbidden(edgeType) — 규칙의 엣지 타입과 일치해야 매칭")
    void isForbidden_edgeTypeSpecific() {
        ArchitectureIntent intent = new ArchitectureIntent(
                List.of(new Module("domain", List.of("**/domain/**")), new Module("app", List.of("**/application/**"))),
                List.of(new DependencyRule("domain", "app", "FUNCTION_CALL")));

        assertThat(intent.isForbidden("domain", "app", EdgeType.FUNCTION_CALL)).isTrue();
        assertThat(intent.isForbidden("domain", "app", EdgeType.IMPORT)).isFalse();
        // 2-arg 오버로드는 IMPORT 기준 하위호환 — FUNCTION_CALL 전용 규칙엔 매칭 안 됨
        assertThat(intent.isForbidden("domain", "app")).isFalse();
    }

    @Test
    @DisplayName("effectiveEdgeType — 미지정·빈값·알 수 없는 값은 IMPORT로 안전 폴백")
    void effectiveEdgeType_fallsBackToImport() {
        assertThat(new DependencyRule("a", "b").effectiveEdgeType()).isEqualTo(EdgeType.IMPORT);
        assertThat(new DependencyRule("a", "b", null).effectiveEdgeType()).isEqualTo(EdgeType.IMPORT);
        assertThat(new DependencyRule("a", "b", "").effectiveEdgeType()).isEqualTo(EdgeType.IMPORT);
        assertThat(new DependencyRule("a", "b", "NOT_A_REAL_TYPE").effectiveEdgeType()).isEqualTo(EdgeType.IMPORT);
        assertThat(new DependencyRule("a", "b", "FUNCTION_CALL").effectiveEdgeType()).isEqualTo(EdgeType.FUNCTION_CALL);
    }

    @Test
    @DisplayName("isEmpty — 모듈 또는 규칙이 비면 빈 의도")
    void isEmpty_guardsNullAndEmpty() {
        assertThat(new ArchitectureIntent(null, null).isEmpty()).isTrue();
        assertThat(new ArchitectureIntent(List.of(), List.of()).isEmpty()).isTrue();
        // 규칙이 비면 검사할 게 없으므로 빈 의도
        assertThat(new ArchitectureIntent(List.of(new Module("m", List.of("*"))), List.of()).isEmpty()).isTrue();
        assertThat(sampleIntent().isEmpty()).isFalse();
    }
}
