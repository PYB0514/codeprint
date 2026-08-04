// 최소 unified diff 파서+적용기 — 트윈 검증(패치 후 재분석)을 위해 LLM이 만든 diff를 원문에 적용
package com.codeprint.application.graph;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UnifiedDiffUtil {

    private static final Pattern HUNK_HEADER = Pattern.compile("^@@ -(\\d+)(?:,\\d+)? \\+\\d+(?:,\\d+)? @@");

    private UnifiedDiffUtil() {
    }

    // 원문에 diff를 순서대로 적용 — 컨텍스트/삭제 줄이 원문과 어긋나면 즉시 예외(퍼지 매칭 없음, 트윈 검증의 "패치 적용 실패" 판정 그대로 노출)
    static String apply(String original, String diff) {
        boolean trailingNewline = original.endsWith("\n");
        List<String> src = splitLines(trailingNewline ? original.substring(0, original.length() - 1) : original);
        List<String> result = new ArrayList<>();
        List<String> diffLines = splitLines(diff);

        int srcIdx = 0;
        int i = 0;
        boolean sawHunk = false;
        while (i < diffLines.size()) {
            Matcher m = HUNK_HEADER.matcher(diffLines.get(i));
            if (!m.find()) {
                i++;
                continue;
            }
            sawHunk = true;
            int oldStart = Integer.parseInt(m.group(1)) - 1;
            if (oldStart < srcIdx || oldStart > src.size()) {
                throw new IllegalStateException("diff 헝크 시작줄이 원문 범위를 벗어남: " + (oldStart + 1));
            }
            while (srcIdx < oldStart) result.add(src.get(srcIdx++));
            i++;
            while (i < diffLines.size() && !diffLines.get(i).startsWith("@@")) {
                String hl = diffLines.get(i);
                // 빈 줄은 마커 공백이 트리밍된 blank 컨텍스트 줄로 간주(흔한 LLM/마크다운 출력 손실) — 건너뛰지 않고
                // 반드시 원문과 대조 후 srcIdx를 소비한다. 그 외 인식 못 할 마커는 예외로 실패(적대적 검증에서 발견된
                // 문제: 예전엔 마커 없는 중간 줄을 조용히 건너뛰어 삭제/컨텍스트가 유실된 채로도 "적용 성공"을 반환했음).
                char marker = hl.isEmpty() ? ' ' : hl.charAt(0);
                String content = hl.isEmpty() ? "" : (hl.length() > 1 ? hl.substring(1) : "");
                if (marker == ' ') {
                    expectContext(src, srcIdx, content);
                    result.add(src.get(srcIdx++));
                } else if (marker == '-') {
                    expectContext(src, srcIdx, content);
                    srcIdx++;
                } else if (marker == '+') {
                    result.add(content);
                } else {
                    throw new IllegalStateException("diff 헝크 내부에 알 수 없는 마커: '" + hl + "'");
                }
                i++;
            }
        }
        if (!sawHunk) throw new IllegalStateException("diff에 @@ 헝크가 없음");
        while (srcIdx < src.size()) result.add(src.get(srcIdx++));

        String joined = String.join("\n", result);
        return trailingNewline ? joined + "\n" : joined;
    }

    private static void expectContext(List<String> src, int idx, String expected) {
        String actual = idx < src.size() ? src.get(idx) : null;
        if (!expected.equals(actual)) {
            throw new IllegalStateException(
                    "diff 컨텍스트 불일치(원문 줄 " + (idx + 1) + "): 기대='" + expected + "' 실제='" + actual + "'");
        }
    }

    private static List<String> splitLines(String text) {
        if (text.isEmpty()) return new ArrayList<>();
        return new ArrayList<>(Arrays.asList(text.split("\n", -1)));
    }
}
