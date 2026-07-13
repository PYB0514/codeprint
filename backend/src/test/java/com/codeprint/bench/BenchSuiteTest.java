// BENCH_SPEC.md §2 룰별 P/N 케이스 — bench/<RULE_TYPE>/<case-name>/ 를 전부 동적 탐색해 파이프라인으로 실행·검증
// §1 공통(룰 무관) 인프라 케이스는 개별 어서션이 필요해 BenchCommonCasesTest에서 별도로 다룬다(이 스위트 대상 아님).
package com.codeprint.bench;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;

class BenchSuiteTest {

    @TestFactory
    Stream<DynamicTest> 룰별_벤치_케이스() {
        List<Path> cases = BenchCaseLoader.discoverCases("rules");
        return cases.stream().map(caseDir -> dynamicTest(caseName(caseDir), () -> {
            List<Map<String, Object>> actual = BenchPipelineRunner.run(caseDir);
            BenchExpectation.assertMatches(caseDir, actual);
        }));
    }

    // 표시용 케이스 이름 — "<룰타입>/<케이스명>"
    private static String caseName(Path caseDir) {
        Path ruleDir = caseDir.getParent();
        return ruleDir.getFileName() + "/" + caseDir.getFileName();
    }
}
