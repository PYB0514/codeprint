// RepoMapService 회귀 테스트 — 프론트 downloadTreeText(graphLayout.ts)와 동일 포맷 유지 검증
package com.codeprint.application.graph;

import com.codeprint.domain.graph.Node;
import com.codeprint.domain.graph.NodeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RepoMapServiceTest {

    private final RepoMapService service = new RepoMapService();
    private final UUID graphId = UUID.randomUUID();

    private Node fileNode(String path, String comment) {
        Node n = Node.create(graphId, NodeType.FILE, lastSegment(path), path, "Java");
        if (comment != null) n.updateMetadata(Map.of("comment", comment));
        return n;
    }

    private Node funcNode(String path, String name, String comment) {
        Node n = Node.create(graphId, NodeType.FUNCTION, name, path, "Java");
        if (comment != null) n.updateMetadata(Map.of("comment", comment));
        return n;
    }

    private String lastSegment(String path) {
        int i = path.lastIndexOf('/');
        return i < 0 ? path : path.substring(i + 1);
    }

    @Test
    @DisplayName("루트 이름은 파일 경로들의 공통 조상 디렉터리 마지막 세그먼트로 결정된다")
    void 루트_이름_공통조상_마지막세그먼트() {
        List<Node> nodes = List.of(
                fileNode("backend/src/main/java/com/codeprint/UserService.java", null),
                fileNode("backend/src/main/java/com/codeprint/UserController.java", null)
        );

        String md = service.generate(nodes);

        assertThat(md).startsWith("# codeprint — 프로젝트 구조");
        assertThat(md).contains("codeprint/");
    }

    @Test
    @DisplayName("파일에 주석이 있으면 '이름 — 주석' 형태로 표시된다")
    void 파일_주석_포함_표시() {
        List<Node> nodes = List.of(
                fileNode("src/UserService.java", "사용자 조회 서비스")
        );

        String md = service.generate(nodes);

        assertThat(md).contains("UserService.java — 사용자 조회 서비스");
    }

    @Test
    @DisplayName("파일에 주석이 없으면 이름만 표시된다")
    void 파일_주석_없으면_이름만() {
        List<Node> nodes = List.of(
                fileNode("src/UserService.java", null)
        );

        String md = service.generate(nodes);

        assertThat(md).contains("└── UserService.java");
        assertThat(md).doesNotContain("UserService.java —");
    }

    @Test
    @DisplayName("한 파일에 속한 함수들은 이름 알파벳순으로 정렬되어 파일 아래에 나열된다")
    void 함수_알파벳순_정렬_표시() {
        List<Node> file = List.of(fileNode("src/UserService.java", null));
        List<Node> funcs = List.of(
                funcNode("src/UserService.java", "save", null),
                funcNode("src/UserService.java", "findById", "ID로 조회"),
                funcNode("src/UserService.java", "delete", null)
        );
        List<Node> nodes = new java.util.ArrayList<>(file);
        nodes.addAll(funcs);

        String md = service.generate(nodes);

        int idxFind = md.indexOf("findById — ID로 조회");
        int idxSave = md.indexOf("save");
        int idxDelete = md.indexOf("delete");
        assertThat(idxFind).isPositive();
        // delete < findById < save (알파벳순)
        assertThat(idxDelete).isLessThan(idxFind);
        assertThat(idxFind).isLessThan(idxSave);
    }

    @Test
    @DisplayName("여러 하위 디렉터리가 있으면 각각 트리 가지로 렌더링되고 마지막 항목만 └── 마커를 쓴다")
    void 여러_디렉터리_트리_렌더링() {
        List<Node> nodes = List.of(
                fileNode("proj/domain/User.java", null),
                fileNode("proj/infra/UserRepositoryImpl.java", null)
        );

        String md = service.generate(nodes);

        assertThat(md).contains("├── domain/");
        assertThat(md).contains("└── infra/");
    }

    @Test
    @DisplayName("DB_TABLE 등 FILE/FUNCTION이 아닌 노드는 트리에 나타나지 않는다")
    void 기타_노드타입_제외() {
        Node table = Node.create(graphId, NodeType.DB_TABLE, "users", "src/User.java", "Java");
        List<Node> nodes = List.of(
                fileNode("src/User.java", null),
                table
        );

        String md = service.generate(nodes);

        assertThat(md).doesNotContain("users");
    }

    @Test
    @DisplayName("파일 노드가 없으면 'project' 를 루트 이름으로 사용한다")
    void 파일없으면_루트이름_project() {
        String md = service.generate(List.of());

        assertThat(md).startsWith("# project — 프로젝트 구조");
    }

    @Test
    @DisplayName("level=summary면 파일까지만 표시하고 함수는 생략한다")
    void level_summary_함수생략() {
        List<Node> nodes = List.of(
                fileNode("src/UserService.java", null),
                funcNode("src/UserService.java", "findById", null)
        );

        String md = service.generate(nodes, "summary");

        assertThat(md).contains("UserService.java");
        assertThat(md).doesNotContain("findById");
    }

    @Test
    @DisplayName("level=full(기본값)이면 기존과 동일하게 함수까지 표시한다")
    void level_full_함수포함() {
        List<Node> nodes = List.of(
                fileNode("src/UserService.java", null),
                funcNode("src/UserService.java", "findById", null)
        );

        assertThat(service.generate(nodes, "full")).contains("findById");
        assertThat(service.generate(nodes)).contains("findById");
    }
}
