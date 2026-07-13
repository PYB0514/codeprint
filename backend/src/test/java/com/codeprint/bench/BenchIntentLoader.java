// 벤치 케이스 디렉터리의 intent.json(있으면)을 ArchitectureIntent로 로드 — LocalAnalyzer.loadIntent와 동일 스키마
package com.codeprint.bench;

import com.codeprint.domain.graph.ArchitectureIntent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class BenchIntentLoader {

    private BenchIntentLoader() {
    }

    // caseDir/intent.json이 있으면 로드, 없으면 null(=의도 없음, INTENT_DRIFT 미발화)
    public static ArchitectureIntent loadIfPresent(Path caseDir) {
        Path file = caseDir.resolve("intent.json");
        if (!Files.isRegularFile(file)) return null;
        try {
            JsonNode root = new ObjectMapper().readTree(Files.readString(file));
            List<ArchitectureIntent.Module> modules = new ArrayList<>();
            for (JsonNode m : root.path("modules")) {
                List<String> globs = new ArrayList<>();
                for (JsonNode g : m.path("globs")) globs.add(g.asText());
                modules.add(new ArchitectureIntent.Module(m.path("name").asText(), globs));
            }
            List<ArchitectureIntent.DependencyRule> rules = new ArrayList<>();
            for (JsonNode r : root.path("rules")) {
                rules.add(new ArchitectureIntent.DependencyRule(r.path("from").asText(), r.path("to").asText()));
            }
            List<ArchitectureIntent.IgnoreRule> ignores = new ArrayList<>();
            for (JsonNode g : root.path("ignore")) {
                ignores.add(new ArchitectureIntent.IgnoreRule(
                        g.path("type").asText(null), g.path("from").asText(null), g.path("to").asText(null)));
            }
            return new ArchitectureIntent(modules, rules, ignores);
        } catch (Exception e) {
            throw new IllegalStateException("intent.json 로드 실패: " + caseDir, e);
        }
    }
}
