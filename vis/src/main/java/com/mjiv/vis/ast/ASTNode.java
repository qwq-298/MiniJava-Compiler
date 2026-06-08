package com.mjiv.vis.ast;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ASTNode {

    public String id;
    public String name;
    public String text;

    public List<ASTNode> children = new ArrayList<>();

    /** 额外属性（如 Field 的 dataType、Assign 的 left/right 等） */
    public Map<String, Object> props;

    public int startLine;
    public int startColumn;
    public int endLine;
    public int endColumn;

    public ASTNode() {
        this.id = UUID.randomUUID().toString();
    }

    public ASTNode(String name) {
        this();
        this.name = name;
    }

    public void addChild(ASTNode child) {
        if (child != null) {
            children.add(child);
        }
    }
}