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

    public void updatePosition(double x, double y) {
        this.posX = x;
        this.posY = y;
    }

    public void toggleHidden() {
        this.isHidden = !this.isHidden;
    }

    public void updateMetadata(Map<String, Object> metadata) {
        this.metadata = metadata;
    }

    public NodeId getNodeId() {
        return NodeId.of(id);
    }
}
