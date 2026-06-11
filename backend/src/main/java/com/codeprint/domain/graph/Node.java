// 그래프 노드 엔티티 (FILE / FUNCTION / DB_TABLE / API_ENDPOINT)
package com.codeprint.domain.graph;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "nodes")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Node {

    @Id
    @Column(columnDefinition = "uuid")
    private UUID id;

    @Column(name = "graph_id", nullable = false, columnDefinition = "uuid")
    private UUID graphId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private NodeType type;

    @Column(nullable = false, length = 500)
    private String name;

    @Column(name = "file_path", length = 1000)
    private String filePath;

    @Column(length = 50)
    private String language;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;

    @Column(name = "pos_x", nullable = false)
    private double posX;

    @Column(name = "pos_y", nullable = false)
    private double posY;

    @Column(name = "is_hidden", nullable = false)
    private boolean isHidden;

    @Column(name = "user_label", length = 200)
    private String userLabel;

    @Column(name = "user_note", columnDefinition = "text")
    private String userNote;

    // 그래프에 속하는 새 노드 인스턴스 생성
    public static Node create(UUID graphId, NodeType type, String name, String filePath, String language) {
        Node node = new Node();
        node.id = UUID.randomUUID();
        node.graphId = graphId;
        node.type = type;
        node.name = name;
        node.filePath = filePath;
        node.language = language;
        node.posX = 0;
        node.posY = 0;
        node.isHidden = false;
        return node;
    }

    // 노드의 화면 좌표를 업데이트
    public void updatePosition(double x, double y) {
        this.posX = x;
        this.posY = y;
    }

    // 노드의 공개/숨김 상태를 토글
    public void toggleHidden() {
        this.isHidden = !this.isHidden;
    }

    // 노드의 메타데이터(함수 시그니처, 주석 등)를 갱신
    public void updateMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    // 사용자 정의 레이블과 메모를 저장
    public void updateAnnotation(String userLabel, String userNote) {
        this.userLabel = userLabel != null && userLabel.isBlank() ? null : userLabel;
        this.userNote = userNote != null && userNote.isBlank() ? null : userNote;
    }

    // UUID를 NodeId Value Object로 변환하여 반환
    public NodeId getNodeId() {
        return NodeId.of(id);
    }
}
