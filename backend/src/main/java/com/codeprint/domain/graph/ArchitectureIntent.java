// 사용자가 선언한 의도 아키텍처 — 모듈(경로 글로브)과 모듈 간 금지 의존 규칙
package com.codeprint.domain.graph;

import java.util.List;
import java.util.regex.Pattern;

// 의도↔실제 conformance 검사의 선언부. 모듈은 파일 경로 글로브로 정의하고,
// 규칙은 "from 모듈이 to 모듈에 의존하면 안 된다"는 금지(FORBID) 관계다.
// 방향성(A→B만 허용)은 반대 방향을 FORBID로 선언해 표현한다(최소 원시형).
public record ArchitectureIntent(List<Module> modules, List<DependencyRule> rules, List<IgnoreRule> ignores) {

    // 하위호환 생성자 — ignores 없이 생성하던 기존 호출부(LocalAnalyzer·테스트) churn 0
    public ArchitectureIntent(List<Module> modules, List<DependencyRule> rules) {
        this(modules, rules, List.of());
    }

    // 모듈 — 이름 + 소속 파일을 판별하는 경로 글로브 목록 (예: ["**/domain/**"])
    public record Module(String name, List<String> globs) {}

    // 금지 의존 규칙 — from 모듈에서 to 모듈로의 의존(import·호출)을 금지
    public record DependencyRule(String from, String to) {}

    // 경고 예외(억제) 규칙 — 의도된 위반 패턴을 글로브로 선언해 그룹 억제한다(opt-out 모델).
    // type 비면 모든 경고 타입, fromGlob 비면 모든 출발 파일, toGlob 비면 모든 도착 파일에 매치(와일드카드).
    public record IgnoreRule(String type, String fromGlob, String toGlob) {
        // 주어진 경고(타입·출발파일·도착파일)가 이 규칙에 매치되어 억제 대상인지
        public boolean matches(String warningType, String fromFile, String toFile) {
            if (isSet(type) && !type.equals(warningType)) return false;
            if (isSet(fromGlob) && !globMatches(fromGlob, fromFile == null ? "" : fromFile)) return false;
            if (isSet(toGlob) && !globMatches(toGlob, toFile == null ? "" : toFile)) return false;
            return true;
        }

        private static boolean isSet(String s) {
            return s != null && !s.isBlank() && !"*".equals(s);
        }
    }

    // 검사할 규칙이 없으면 빈 의도 — 탐지기는 즉시 스킵 (ignores는 별도로 적용되므로 여기에 포함하지 않음)
    public boolean isEmpty() {
        return modules == null || modules.isEmpty() || rules == null || rules.isEmpty();
    }

    // 주어진 경고가 어떤 ignore 규칙에든 매치되면 억제 대상 — 하나라도 매치하면 true
    public boolean isIgnored(String warningType, String fromFile, String toFile) {
        if (ignores == null) return false;
        for (IgnoreRule r : ignores) {
            if (r.matches(warningType, fromFile, toFile)) return true;
        }
        return false;
    }

    // 파일 경로가 속한 모듈명 — 선언 순서상 첫 매칭(없으면 null). 한 파일이 여러 글로브에 걸리면 먼저 선언된 모듈 우선.
    public String moduleOf(String filePath) {
        if (filePath == null || filePath.isEmpty() || modules == null) return null;
        for (Module m : modules) {
            if (m.globs() == null) continue;
            for (String glob : m.globs()) {
                if (globMatches(glob, filePath)) return m.name();
            }
        }
        return null;
    }

    // from→to 의존이 금지돼 있는지
    public boolean isForbidden(String fromModule, String toModule) {
        if (rules == null) return false;
        for (DependencyRule r : rules) {
            if (r.from().equals(fromModule) && r.to().equals(toModule)) return true;
        }
        return false;
    }

    // 글로브를 '/' 구분 경로에 매칭 — ** = 임의 경로(/ 포함), * = / 제외 임의 문자, ? = / 제외 한 글자
    static boolean globMatches(String glob, String path) {
        return globToPattern(glob).matcher(path).matches();
    }

    // 글로브를 정규식으로 변환 (앵커 포함)
    private static Pattern globToPattern(String glob) {
        StringBuilder re = new StringBuilder("^");
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    re.append(".*");
                    i++;
                } else {
                    re.append("[^/]*");
                }
            } else if (c == '?') {
                re.append("[^/]");
            } else if ("\\.[]{}()+-^$|".indexOf(c) >= 0) {
                re.append('\\').append(c);
            } else {
                re.append(c);
            }
        }
        re.append("$");
        return Pattern.compile(re.toString());
    }
}
