// tree-sitter로 C++ 함수·호출을 추출해 출력하는 PoC CLI (C++는 정규식 미지원이라 A/B 대신 추출량·노드 진단)
package com.codeprint.tools.treesitter;

import com.codeprint.infrastructure.analysis.LanguageDetector;
import com.codeprint.infrastructure.analysis.ParsedFile;
import com.codeprint.infrastructure.analysis.SourceFileWalker;
import com.codeprint.infrastructure.analysis.StaticCodeAnalyzer;
import org.treesitter.TSNode;
import org.treesitter.TSParser;
import org.treesitter.TSTree;
import org.treesitter.TreeSitterCpp;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class TreeSitterCppPoc {

    // 디렉터리 내 C++ 파일을 분석해 함수·호출 추출량과 노드 히스토그램을 출력
    public static void main(String[] args) throws Exception {
        Path rootDir = args.length > 0 ? Path.of(args[0]) : Path.of("src/main/java");
        System.out.println("분석 대상: " + rootDir.toAbsolutePath());

        // ① native 로드 검증
        long t0 = System.nanoTime();
        TSParser probe = new TSParser();
        probe.setLanguage(new TreeSitterCpp());
        TSTree probeTree = probe.parseString(null, "struct S { int m(){ return n(); } int n(){ return 0; } };\n");
        System.out.println("native 로드 OK — 루트 노드 타입: " + probeTree.getRootNode().getType()
                + " (" + (System.nanoTime() - t0) / 1_000_000 + "ms)");

        SourceFileWalker walker = new SourceFileWalker();
        StaticCodeAnalyzer analyzer = new StaticCodeAnalyzer();

        List<Path> files = walker.walk(rootDir).files().stream()
                .filter(f -> "C++".equals(LanguageDetector.detect(f.getFileName().toString()).orElse("")))
                .toList();
        System.out.println("C++ 파일 수: " + files.size());

        int fnTotal = 0, callTotal = 0, filesWithFns = 0;
        long parseNanos = 0;
        Map<String, Integer> nodeHist = new TreeMap<>();
        List<String> samples = new ArrayList<>();

        for (Path file : files) {
            String content = Files.readString(file, StandardCharsets.UTF_8);
            if (!content.isEmpty() && content.charAt(0) == '﻿') content = content.substring(1);

            ParsedFile pf = analyzer.analyze(file, rootDir, "C++");
            int fns = pf.functions().size();
            int calls = pf.functionCalls().values().stream().mapToInt(List::size).sum();
            fnTotal += fns;
            callTotal += calls;
            if (fns > 0) filesWithFns++;

            long t1 = System.nanoTime();
            histogram(content, nodeHist);
            parseNanos += System.nanoTime() - t1;

            if (samples.size() < 15 && fns > 0) {
                samples.add(pf.filePath() + " — 함수 " + fns + "개, 호출 " + calls + "개: "
                        + (pf.functions().size() > 6 ? pf.functions().subList(0, 6) + " …" : pf.functions()));
            }
        }

        System.out.println("\n===== 노드 타입 히스토그램 (그래머 확인용) =====");
        nodeHist.forEach((type, count) -> {
            if (type.contains("function") || type.contains("call") || type.contains("declarator")
                    || type.contains("operator") || type.contains("destructor") || type.contains("qualified"))
                System.out.println("  " + type + ": " + count);
        });

        System.out.println("\n===== 추출량 =====");
        System.out.println("함수 합계: " + fnTotal);
        System.out.println("호출 합계: " + callTotal);
        System.out.println("함수가 추출된 파일: " + filesWithFns + " / " + files.size());
        System.out.println("파싱 시간: " + parseNanos / 1_000_000 + "ms");

        System.out.println("\n===== 샘플 (최대 15파일) =====");
        samples.forEach(s -> System.out.println("  " + s));
    }

    // 노드 타입 히스토그램 수집 — 그래머 노드명 코드근거 확인용
    private static void histogram(String content, Map<String, Integer> nodeHist) {
        TSParser parser = new TSParser();
        parser.setLanguage(new TreeSitterCpp());
        TSTree tree = parser.parseString(null, content);
        walk(tree.getRootNode(), nodeHist);
    }

    private static void walk(TSNode node, Map<String, Integer> nodeHist) {
        nodeHist.merge(node.getType(), 1, Integer::sum);
        for (int i = 0; i < node.getChildCount(); i++) walk(node.getChild(i), nodeHist);
    }
}
