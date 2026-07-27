// 그래프 노드를 파일/함수 트리 마크다운으로 변환 — 웹 다운로드·MCP get_repo_map이 공유하는 단일 생성기
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.springframework.stereotype.Service;

import java.util.*;

// 프론트 downloadTreeText(graphLayout.ts)와 동일 포맷을 사양으로 이식 — 포맷을 이 한 곳에서만 바꾸면
// 웹 다운로드·MCP get_repo_map 양쪽에 동시 반영된다(§16.1 생성기 단일 소스화).
@Service
public class RepoMapService {

    // 노드 목록으로 "파일/함수 트리 + 한국어 주석" 마크다운 생성 (기본 level=full)
    public String generate(List<Node> nodes) {
        return generate(nodes, "full");
    }

    // grouping 없이 호출 — 기존 폴더 구조 트리(folder)
    public String generate(List<Node> nodes, String level) {
        return generate(nodes, level, "folder");
    }

    // grouping: "folder"(기존 — 물리적 폴더 구조) | "context"(바운디드 컨텍스트별 — 레이어드 폴더에 흩어진 같은
    // 기능 파일을 한데 모아 보여줌). context 감지 실패(DDD/레이어드 컨벤션 자체가 없는 레포)면 folder로 자동 폴백
    // + 안내 문구를 덧붙인다 — 감지할 구조가 없으면 컨텍스트별로 묶을 수 없다는 원리적 한계를 숨기지 않는다.
    public String generate(List<Node> nodes, String level, String grouping) {
        if ("context".equals(grouping)) {
            String byContext = generateByContext(nodes, level);
            if (byContext != null) return byContext;
            return "> ⚠️ 이 프로젝트에서 DDD/레이어드 컨텍스트 구조를 감지하지 못해 폴더 구조로 표시합니다.\n\n"
                    + generate(nodes, level, "folder");
        }
        return generateFolderTree(nodes, level);
    }

    // 컨텍스트별 그룹핑 — 감지된 바운디드 컨텍스트가 없으면(진짜 스파게티 등 구조 자체가 없는 레포) null 반환(폴백 신호)
    private String generateByContext(List<Node> nodes, String level) {
        boolean summary = "summary".equals(level);
        // 테스트·픽스처 경로는 컨텍스트 판정에서 제외 — 벤치/edge-audit처럼 의도적으로 다른 DDD 레이아웃을 심어둔
        // 합성 픽스처가 실제 코드와 섞이면 detectContextFirstContexts가 오인식할 수 있음(실측으로 확인, GATE_GAPS.md 참조)
        List<Node> fileNodes = nodes.stream()
                .filter(n -> n.getType() == NodeType.FILE)
                .filter(n -> !isTestOrFixturePath(n.getFilePath()))
                .toList();
        List<Node> funcNodes = summary ? List.of() : nodes.stream()
                .filter(n -> n.getType() == NodeType.FUNCTION)
                .filter(n -> !isTestOrFixturePath(n.getFilePath()))
                .toList();

        List<String> allPaths = fileNodes.stream().map(f -> normalize(f.getFilePath())).toList();
        Set<String> cfContexts = BoundedContextResolver.detectContextFirstContexts(allPaths);

        Map<String, List<Node>> byContext = new TreeMap<>();
        for (Node f : fileNodes) {
            String ctx = BoundedContextResolver.functionContextOf(normalize(f.getFilePath()), cfContexts);
            byContext.computeIfAbsent(ctx != null ? ctx : "(미분류)", k -> new ArrayList<>()).add(f);
        }
        // 전부 미분류면 컨텍스트 구조 자체가 없는 레포 — 폴백
        if (byContext.size() <= 1 && byContext.containsKey("(미분류)")) return null;

        String rootName = findCommonPrefix(allPaths);
        rootName = rootName.endsWith("/") ? rootName.substring(0, rootName.length() - 1) : rootName;
        rootName = lastSegment(rootName);
        if (rootName.isEmpty()) rootName = "project";

        StringBuilder sb = new StringBuilder("# " + rootName + " — 프로젝트 구조 (컨텍스트별)\n\n");
        for (Map.Entry<String, List<Node>> entry : byContext.entrySet()) {
            sb.append("## ").append(entry.getKey()).append(" 컨텍스트\n\n```\n");
            List<Node> files = entry.getValue().stream().sorted(Comparator.comparing(Node::getFilePath)).toList();
            for (int i = 0; i < files.size(); i++) {
                Node file = files.get(i);
                boolean isLast = i == files.size() - 1;
                sb.append(isLast ? "└── " : "├── ").append(label(file.getFilePath(), comment(file))).append("\n");
                List<Node> funcsInFile = funcNodes.stream()
                        .filter(fn -> file.getFilePath().equals(fn.getFilePath()))
                        .sorted(Comparator.comparing(Node::getName))
                        .toList();
                String childIndent = isLast ? "    " : "│   ";
                for (int fi = 0; fi < funcsInFile.size(); fi++) {
                    Node fn = funcsInFile.get(fi);
                    String fnBranch = fi == funcsInFile.size() - 1 ? "└── " : "├── ";
                    sb.append(childIndent).append(fnBranch).append(label(fn.getName(), comment(fn))).append("\n");
                }
            }
            sb.append("```\n\n");
        }
        return sb.toString();
    }

    // level: "summary"(파일까지만, 함수 생략 — 대형 레포 토큰 절감용) | "full"(기존 — 함수까지 포함)
    private String generateFolderTree(List<Node> nodes, String level) {
        boolean summary = "summary".equals(level);
        List<Node> fileNodes = nodes.stream().filter(n -> n.getType() == NodeType.FILE).toList();
        List<Node> funcNodes = summary ? List.of() : nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).toList();

        // 디렉터리 경로 → 그 디렉터리에 직접 속한 파일 경로 목록 (조상 디렉터리도 빈 목록으로 미리 등록해
        // 파일이 하나도 직속하지 않는 중간 디렉터리도 트리 순회 대상에 포함되게 한다)
        Map<String, List<String>> tree = new LinkedHashMap<>();
        Map<String, Node> fileByPath = new LinkedHashMap<>();
        for (Node f : fileNodes) {
            fileByPath.put(f.getFilePath(), f);
            List<String> parts = splitPath(f.getFilePath());
            for (int i = 0; i < parts.size() - 1; i++) {
                tree.computeIfAbsent(join(parts.subList(0, i + 1)), k -> new ArrayList<>());
            }
            String dir = parts.size() > 1 ? join(parts.subList(0, parts.size() - 1)) : "";
            tree.computeIfAbsent(dir, k -> new ArrayList<>()).add(f.getFilePath());
        }

        List<String> lines = new ArrayList<>();

        List<String> allPaths = fileNodes.stream().map(f -> normalize(f.getFilePath())).toList();
        String commonPrefix = findCommonPrefix(allPaths);
        String prefixNoSlash = commonPrefix.endsWith("/") ? commonPrefix.substring(0, commonPrefix.length() - 1) : commonPrefix;
        String rootName = lastSegment(prefixNoSlash);
        if (rootName.isEmpty()) rootName = "project";
        lines.add(rootName + "/");

        // 공통 조상 디렉터리 자체를 루트로 재귀 렌더링 — 그 디렉터리에 직속한 파일도 하위 디렉터리와
        // 같은 경로(tree.get(dirPath))로 자연스럽게 포함된다. (참고: 프론트 downloadTreeText는 "공통
        // 조상 한 단계 아래"만 별도로 순회해, 파일들이 공통 조상 디렉터리에 직접 있는 경우 — 예:
        // 모든 파일이 subdir 없이 한 폴더에 있는 레포 — 트리에서 통째로 누락되는 잠재 버그가 있음.
        // 백엔드 승격을 계기로 재귀 하나로 통일해 그 사각을 없앴다.)
        renderDir(prefixNoSlash, "", tree, fileByPath, funcNodes, lines);

        return "# " + rootName + " — 프로젝트 구조\n\n```\n" + String.join("\n", lines) + "\n```\n";
    }

    // 테스트·픽스처 경로 여부 — SourceFileWalker.isTestOrFixturePath와 같은 기준(레이어 분리로 별도 구현,
    // infrastructure 역참조 방지). 컨텍스트 그룹핑 전용(폴더 트리 모드는 기존대로 전체 표시 — 하위 호환 유지).
    private boolean isTestOrFixturePath(String path) {
        if (path == null) return false;
        String p = normalize(path);
        return p.contains("src/test/") || p.contains("__tests__/") || p.contains(".test.") || p.contains(".spec.");
    }

    // 디렉터리를 재귀적으로 트리 텍스트로 렌더링 — 하위 디렉터리 먼저, 그 다음 파일(+파일별 함수) 순으로 정렬
    private void renderDir(String dirPath, String indent, Map<String, List<String>> tree,
                           Map<String, Node> fileByPath, List<Node> funcNodes, List<String> lines) {
        List<String> childDirs = tree.keySet().stream()
                .filter(k -> !k.equals(dirPath) && parentOf(k).equals(dirPath))
                .sorted()
                .toList();

        List<Node> files = tree.getOrDefault(dirPath, List.of()).stream()
                .map(fileByPath::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Node::getName))
                .toList();

        List<String> allItems = new ArrayList<>(childDirs);
        files.forEach(f -> allItems.add(f.getFilePath()));

        for (int idx = 0; idx < allItems.size(); idx++) {
            String item = allItems.get(idx);
            boolean isLast = idx == allItems.size() - 1;
            String branch = isLast ? "└── " : "├── ";
            String childIndent = indent + (isLast ? "    " : "│   ");

            if (childDirs.contains(item)) {
                lines.add(indent + branch + lastSegment(item) + "/");
                renderDir(item, childIndent, tree, fileByPath, funcNodes, lines);
            } else {
                Node file = fileByPath.get(item);
                if (file == null) continue;
                lines.add(indent + branch + label(file.getName(), comment(file)));

                List<Node> funcsInFile = funcNodes.stream()
                        .filter(fn -> item.equals(fn.getFilePath()))
                        .sorted(Comparator.comparing(Node::getName))
                        .toList();
                for (int fi = 0; fi < funcsInFile.size(); fi++) {
                    Node fn = funcsInFile.get(fi);
                    String fnBranch = fi == funcsInFile.size() - 1 ? "└── " : "├── ";
                    lines.add(childIndent + fnBranch + label(fn.getName(), comment(fn)));
                }
            }
        }
    }

    private String comment(Node n) {
        return n.getMetadata() != null && n.getMetadata().get("comment") instanceof String s && !s.isBlank() ? s : null;
    }

    // 이름 — 주석 형태 (주석 없으면 이름만)
    private String label(String name, String comment) {
        return comment != null ? name + " — " + comment : name;
    }

    private String parentOf(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? "" : path.substring(0, idx);
    }

    private String lastSegment(String path) {
        int idx = path.lastIndexOf('/');
        return idx < 0 ? path : path.substring(idx + 1);
    }

    private String normalize(String path) {
        return path == null ? "" : path.replace("\\", "/");
    }

    private List<String> splitPath(String path) {
        List<String> parts = new ArrayList<>();
        for (String p : normalize(path).split("/")) {
            if (!p.isEmpty()) parts.add(p);
        }
        return parts;
    }

    private String join(List<String> parts) {
        return String.join("/", parts);
    }

    // 파일 경로 목록의 공통 조상 디렉터리 접두사 계산 (트레일링 '/' 포함)
    private String findCommonPrefix(List<String> paths) {
        if (paths.isEmpty()) return "";
        String[] parts = paths.get(0).split("/");
        String prefix = "";
        for (int depth = 1; depth <= parts.length; depth++) {
            String candidate = String.join("/", Arrays.copyOfRange(parts, 0, depth)) + "/";
            boolean allMatch = paths.stream().allMatch(p -> p.startsWith(candidate));
            if (allMatch) prefix = candidate;
            else break;
        }
        return prefix;
    }
}
