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

    // 노드 목록으로 "파일/함수 트리 + 한국어 주석" 마크다운 생성
    public String generate(List<Node> nodes) {
        List<Node> fileNodes = nodes.stream().filter(n -> n.getType() == NodeType.FILE).toList();
        List<Node> funcNodes = nodes.stream().filter(n -> n.getType() == NodeType.FUNCTION).toList();

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
