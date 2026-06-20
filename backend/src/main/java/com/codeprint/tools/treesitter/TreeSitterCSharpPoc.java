// tree-sitter로 C# 함수·호출을 추출해 기존 정규식 결과와 A/B 비교하는 PoC CLI (회귀 검증용)
package com.codeprint.tools.treesitter;

import com.codeprint.infrastructure.analysis.LanguageDetector;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterCSharp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

public class TreeSitterCSharpPoc {

    // 디렉터리 내 C# 파일을 regex/tree-sitter 양쪽으로 분석해 함수·호출 차이를 출력
    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/java");
        System.out.println("분석 대상: " + rootDir.toAbsolutePath());

        // ① native 로드 검증 — 파서 1회 생성으로 .so/.dll 로드 성공 여부 확인
        long t0 = System.nanoTime();
        TSParser probe = new TSParser();
        probe.setLanguage(new TreeSitterCSharp());
        TSTree probeTree = probe.parseString(null,
                "class C { void M() { N(); } void N() {} }\n");
        System.out.println("native 로드 OK — 루트 노드 타입: " + probeTree.getRootNode().getType()
                + " (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");

        SourceFileWalker walker = new SourceFileWalker();
        StaticCodeAnalyzer regex = new StaticCodeAnalyzer();

        List<Path> files = walker.walk(rootDir).files().stream()
                .filter(f -> "C#".equals(LanguageDetector.detect(f.getFileName().toString()).orElse("")))
                .toList();
        System.out.println("C# 파일 수: " + files.size());

        int regexFnTotal = 0, tsFnTotal = 0;
        int filesWithFnDiff = 0;
        int tsOnlyFnTotal = 0, regexOnlyFnTotal = 0;
        int regexCallTotal = 0, tsCallTotal = 0;
        long tsParseNanos = 0;
        List<String> sampleDiffs = new ArrayList<>();
        // 노드 타입 히스토그램 — C# 그래머의 노드/필드명을 코드근거로 확인(method_declaration·invocation_expression 등)
        Map<String, Integer> nodeHist = new TreeMap<>();
        // 호출 차이가 가장 큰 파일을 추적 — 호출 귀속 차이의 정체를 코드근거로 확인
        String maxCallDiffFile = null;
        int maxCallDiff = -1;
        Map<String, java.util.Set<String>> maxRegexCalls = null, maxTsCalls = null;

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '\uFEFF') content = content.substring(1);

            ParsedFile rf = regex.analyze(file, rootDir, "C#");
            TreeSet<String> regexFns = new TreeSet<>(rf.functions());
            int regexCalls = rf.functionCalls().values().stream().mapToInt(List::size).sum();

            long ts0 = System.nanoTime();
            TsResult ts = extract(content, nodeHist);
            tsParseNanos += System.nanoTime() - ts0;
            TreeSet<String> tsFns = new TreeSet<>(ts.functions);
            int tsCalls = ts.calls.values().stream().mapToInt(java.util.Set::size).sum();

            regexFnTotal += regexFns.size();
            tsFnTotal += tsFns.size();
            regexCallTotal += regexCalls;
            tsCallTotal += tsCalls;

            int callDiff = Math.abs(regexCalls - tsCalls);
            if (callDiff > maxCallDiff) {
                maxCallDiff = callDiff;
                maxCallDiffFile = rf.filePath();
                Map<String, java.util.Set<String>> rc = new TreeMap<>();
                rf.functionCalls().forEach((k, v) -> rc.put(k, new TreeSet<>(v)));
                maxRegexCalls = rc;
                maxTsCalls = new TreeMap<>(ts.calls);
            }

            TreeSet<String> tsOnly = new TreeSet<>(tsFns);
            tsOnly.removeAll(regexFns);
            TreeSet<String> regexOnly = new TreeSet<>(regexFns);
            regexOnly.removeAll(tsFns);
            tsOnlyFnTotal += tsOnly.size();
            regexOnlyFnTotal += regexOnly.size();

            if (!tsOnly.isEmpty() || !regexOnly.isEmpty()) {
                filesWithFnDiff++;
                if (sampleDiffs.size() < 25) {
                    sampleDiffs.add(rf.filePath()
                            + "\n    ts-only(+" + tsOnly.size() + "): " + cap(tsOnly)
                            + "\n    regex-only(-" + regexOnly.size() + "): " + cap(regexOnly));
                }
            }
        }

        System.out.println("\n===== 노드 타입 히스토그램 (그래머 확인용) =====");
        nodeHist.forEach((type, count) -> System.out.println("  " + type + ": " + count));

        System.out.println("\n===== 함수 추출 A/B =====");
        System.out.println("regex 함수 합계: " + regexFnTotal);
        System.out.println("tree-sitter 함수 합계: " + tsFnTotal);
        System.out.println("ts-only(정규식이 놓친 함수): " + tsOnlyFnTotal);
        System.out.println("regex-only(tree-sitter가 놓친 함수): " + regexOnlyFnTotal);
        System.out.println("함수 집합이 다른 파일 수: " + filesWithFnDiff + " / " + files.size());

        System.out.println("\n===== 호출 추출 A/B (총 호출 엣지 후보 수) =====");
        System.out.println("regex 호출 합계: " + regexCallTotal);
        System.out.println("tree-sitter 호출 합계: " + tsCallTotal);

        System.out.println("\n===== tree-sitter 파싱 성능 =====");
        System.out.println("총 파싱 시간: " + tsParseNanos / 1_000_000 + "ms (" + files.size() + " 파일)");

        System.out.println("\n===== 차이 샘플 (최대 25파일) =====");
        sampleDiffs.forEach(s -> System.out.println("  " + s));

        System.out.println("\n===== 호출 차이 최대 파일: " + maxCallDiffFile + " (Δ" + maxCallDiff + ") =====");
        TreeSet<String> allFns = new TreeSet<>();
        if (maxRegexCalls != null) allFns.addAll(maxRegexCalls.keySet());
        if (maxTsCalls != null) allFns.addAll(maxTsCalls.keySet());
        for (String fn : allFns) {
            java.util.Set<String> r = maxRegexCalls.getOrDefault(fn, java.util.Set.of());
            java.util.Set<String> t = maxTsCalls.getOrDefault(fn, java.util.Set.of());
            TreeSet<String> rOnly = new TreeSet<>(r); rOnly.removeAll(t);
            TreeSet<String> tOnly = new TreeSet<>(t); tOnly.removeAll(r);
            if (rOnly.isEmpty() && tOnly.isEmpty()) continue;
            System.out.println("  " + fn + "()");
            if (!rOnly.isEmpty()) System.out.println("    regex-only: " + cap(rOnly));
            if (!tOnly.isEmpty()) System.out.println("    ts-only:    " + cap(tOnly));
        }
    }

    // 집합을 최대 8개까지 잘라서 표시
    private static String cap(java.util.Set<String> s) {
        List<String> l = new ArrayList<>(s);
        if (l.size() <= 8) return l.toString();
        return l.subList(0, 8) + " …+" + (l.size() - 8);
    }

    // tree-sitter 추출 결과 — 함수명 집합과 함수별 호출 집합
    private record TsResult(java.util.Set<String> functions, Map<String, java.util.Set<String>> calls) {}

    // C# 소스 1개를 tree-sitter로 파싱해 함수 정의와 호출을 추출
    private static TsResult extract(String content, Map<String, Integer> nodeHist) {
        byte[] src = content.getBytes(StandardCharsets.UTF_8);
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterCSharp());
        TSTree tree = parser.parseString(null, content);

        java.util.Set<String> functions = new LinkedHashSet<>();
        Map<String, java.util.Set<String>> calls = new TreeMap<>();
        walk(tree.getRootNode(), src, null, functions, calls, nodeHist);
        return new TsResult(functions, calls);
    }

    // 트리를 재귀 순회하며 함수 정의를 수집하고, 호출을 가장 가까운 정의에 귀속 (production 분석기와 동일 규칙)
    private static void walk(TSNode node, byte[] src, String enclosing,
                             java.util.Set<String> functions, Map<String, java.util.Set<String>> calls,
                             Map<String, Integer> nodeHist) {
        String type = node.getType();
        nodeHist.merge(type, 1, Integer::sum);
        String current = enclosing;

        if (type.equals("method_declaration")
                || type.equals("constructor_declaration")
                || type.equals("local_function_statement")) {
            TSNode nameNode = node.getChildByFieldName("name");
            if (nameNode != null && !nameNode.isNull()) {
                String name = text(nameNode, src);
                if (!name.isEmpty()) {
                    functions.add(name);
                    current = name;
                }
            }
        } else if (type.equals("invocation_expression") && current != null) {
            TSNode fn = node.getChildByFieldName("function");
            if (fn != null && !fn.isNull()) {
                String fnType = fn.getType();
                if (fnType.equals("identifier")) {
                    String name = text(fn, src);
                    if (!name.isEmpty() && !name.equals(current)) {
                        calls.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(name);
                    }
                } else if (fnType.equals("member_access_expression")) {
                    TSNode nameNode = fn.getChildByFieldName("name");
                    if (nameNode != null && !nameNode.isNull()) {
                        String method = text(nameNode, src);
                        if (!method.isEmpty()) {
                            TSNode expr = fn.getChildByFieldName("expression");
                            String recv = (expr != null && !expr.isNull() && expr.getType().equals("identifier"))
                                    ? text(expr, src) : "";
                            String callee = (!recv.isEmpty() && Character.isUpperCase(recv.charAt(0)))
                                    ? recv + "::" + method : method;
                            if (!callee.equals(current)) {
                                calls.computeIfAbsent(current, k -> new LinkedHashSet<>()).add(callee);
                            }
                        }
                    }
                }
            }
        }

        int n = node.getChildCount();
        for (int i = 0; i < n; i++) {
            walk(node.getChild(i), src, current, functions, calls, nodeHist);
        }
    }

    // 노드의 UTF-8 바이트 범위로 텍스트 추출 (한글 주석 등 멀티바이트 안전)
    private static String text(TSNode node, byte[] src) {
        int s = node.getStartByte();
        int e = node.getEndByte();
        if (s < 0 || e > src.length || s >= e) return "";
        return new String(src, s, e - s, StandardCharsets.UTF_8);
    }
}
