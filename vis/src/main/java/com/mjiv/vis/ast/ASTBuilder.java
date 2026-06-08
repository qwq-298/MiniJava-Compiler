package com.mjiv.vis.ast;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.*;

import java.util.ArrayList;
import java.util.List;

public class ASTBuilder {

    public ASTNode build(ParseTree tree) {
        return normalize(visit(tree));
    }

    // =========================
    // 1. ANTLR → RAW TREE
    // =========================
    private ASTNode visit(ParseTree tree) {

        if (tree == null) return null;

        ASTNode node = new ASTNode();

        // =========================
        // Terminal
        // =========================
        if (tree instanceof TerminalNode terminal) {

            String text = terminal.getText();
            if (isNoiseToken(text)) return null;

            Token token = terminal.getSymbol();

            node.name = "Token";
            node.text = text;

            node.startLine = token.getLine();
            node.startColumn = token.getCharPositionInLine();
            node.endLine = token.getLine();
            node.endColumn = node.startColumn + text.length();

            return node;
        }

        // =========================
        // Rule node
        // =========================
        ParserRuleContext ctx = (ParserRuleContext) tree;

        String name = tree.getClass()
                .getSimpleName()
                .replace("Context", "");

        node.name = name;
        node.children = new ArrayList<>();

        if (ctx.start != null) {
            node.startLine = ctx.start.getLine();
            node.startColumn = ctx.start.getCharPositionInLine();
        }

        if (ctx.stop != null) {
            node.endLine = ctx.stop.getLine();
            node.endColumn =
                    ctx.stop.getCharPositionInLine()
                            + ctx.stop.getText().length();
        }

        for (int i = 0; i < tree.getChildCount(); i++) {
            ASTNode child = visit(tree.getChild(i));

            if (child != null) {
                node.children.add(child);
            }
        }

        return node;
    }

    // =========================
    // 2. IDE级语义压缩（核心）
    // =========================
    private ASTNode normalize(ASTNode node) {

        if (node == null) return null;

        if (node.children != null) {
            List<ASTNode> newChildren = new ArrayList<>();
            for (ASTNode c : node.children) {
                ASTNode r = normalize(c);
                if (r != null) newChildren.add(r);
            }
            node.children = newChildren;
        }

        // =========================
        // 1. Class
        // =========================
        if ("ClassDeclaration".equals(node.name)) {
            return buildEntityNode("Class", node, 0);
        }

        // =========================
        // 0. CompilationUnit / Program → 穿透到第一个有效子节点
        // =========================
        if ("CompilationUnit".equals(node.name) || "Program".equals(node.name)) {
            for (ASTNode c : node.children) {
                if (c != null && !"Token".equals(c.name) && !"<EOF>".equals(c.text)) {
                    return c;
                }
            }
            return null;
        }

        // =========================
        // 2. Method
        // =========================
        if ("MethodDeclaration".equals(node.name)) {
            // buildEntityNode 已经包含了 Block 等所有子节点
            return buildEntityNode("Method", node, 0);
        }

        // =========================
        // 3. Field
        // =========================
        if ("FieldDeclaration".equals(node.name)) {
            return buildField(node);
        }

        // =========================
        // 4. Expression → Assign / Simplify
        // =========================
        if ("Expression".equals(node.name)) {

            // a = b
            if (node.children.size() == 3
                    && "=".equals(node.children.get(1).text)) {

                ASTNode assign = new ASTNode();
                assign.name = "Assign";
                assign.children = new ArrayList<>();

                assign.children.add(node.children.get(0));
                assign.children.add(node.children.get(2));

                copyPos(node, assign);
                return assign;
            }

            if (node.children.size() == 1) {
                return node.children.get(0);
            }
        }

        // =========================
        // 5. unwrap noise nodes
        // =========================
        if (isNoiseNode(node)) {
            if (node.children.size() == 1) {
                return node.children.get(0);
            }
        }

        if (node.children.isEmpty() && node.text == null) {
            return null;
        }

        return node;
    }

    // =========================
    // Entity builder（统一结构）
    // =========================
    private ASTNode buildEntityNode(String type, ASTNode node, int nameIndex) {

        ASTNode result = new ASTNode();
        result.name = type;
        result.children = new ArrayList<>();

        // 只从 Identifier 节点收集名称
        List<Integer> nameIndices = new ArrayList<>();
        List<String> names = new ArrayList<>();

        for (int i = 0; i < node.children.size(); i++) {
            ASTNode c = node.children.get(i);
            if ("Identifier".equals(c.name)) {
                String text = getIdentifierText(c);
                if (text != null) {
                    names.add(text);
                    nameIndices.add(i);
                }
            }
        }

        String name = (nameIndex < names.size()) ? names.get(nameIndex) : null;
        result.text = name;

        // 添加子节点：跳过名称对应的 Identifier 节点和关键字 Token
        for (int i = 0; i < node.children.size(); i++) {
            ASTNode child = node.children.get(i);
            if (nameIndices.contains(i)) continue;
            if ("Token".equals(child.name) && isKeyword(child.text)) continue;
            // 展开 ClassBody → 直接提级其子节点
            if ("ClassBody".equals(child.name) && child.children != null) {
                for (ASTNode gc : child.children) {
                    result.children.add(gc);
                }
                continue;
            }
            result.children.add(child);
        }

        copyPos(node, result);
        return result;
    }

    // =========================
    // Field 专用
    // =========================
    private ASTNode buildField(ASTNode node) {

        ASTNode field = new ASTNode();
        field.name = "Field";
        field.children = new ArrayList<>();

        String type = null;
        String name = null;

        for (ASTNode c : node.children) {
            if ("TypeType".equals(c.name)) {
                type = deepTokenText(c);
            } else if ("VariableDeclarator".equals(c.name)) {
                // VariableDeclarator → identifier ('=' variableInitializer)?
                for (ASTNode vc : c.children) {
                    if ("Identifier".equals(vc.name)) {
                        name = getIdentifierText(vc);
                        break;
                    }
                }
            } else if ("Identifier".equals(c.name)) {
                // 兜底：有些语法直接放 Identifier
                name = getIdentifierText(c);
            }
        }

        field.text = name;
        field.props = new java.util.HashMap<>();
        field.props.put("dataType", type == null ? "unknown" : type);

        copyPos(node, field);
        return field;
    }

    /** 只从 Identifier 规则节点中提取标识符文本（不处理裸 Token，避免匹配到关键字） */
    private String getIdentifierText(ASTNode node) {
        if (node == null) return null;
        if ("Identifier".equals(node.name) && !node.children.isEmpty()) {
            return node.children.get(0).text;
        }
        return null;
    }

    /** 递归获取最深层的 Token 文本 */
    private String deepTokenText(ASTNode node) {
        if (node == null) return null;
        if ("Token".equals(node.name)) return node.text;
        if (node.children != null && !node.children.isEmpty()) {
            return deepTokenText(node.children.get(0));
        }
        return null;
    }

    // =========================
    // utilities
    // =========================
    private boolean isNoiseNode(ASTNode node) {
        return "Statement".equals(node.name)
                || "BlockStatement".equals(node.name)
                || "ClassBodyDeclaration".equals(node.name)
                || "ClassBody".equals(node.name)
                || "Primary".equals(node.name)
                || "Expression".equals(node.name);
    }

    private void copyPos(ASTNode from, ASTNode to) {
        to.startLine = from.startLine;
        to.startColumn = from.startColumn;
        to.endLine = from.endLine;
        to.endColumn = from.endColumn;
    }

    private boolean isNoiseToken(String text) {
        return "{".equals(text)
                || "}".equals(text)
                || "(".equals(text)
                || ")".equals(text)
                || ";".equals(text)
                || ",".equals(text)
                || "[".equals(text)
                || "]".equals(text)
                || ".".equals(text);
    }

    private boolean isKeyword(String text) {
        if (text == null) return false;
        return "class".equals(text)
                || "public".equals(text)
                || "private".equals(text)
                || "protected".equals(text)
                || "static".equals(text)
                || "void".equals(text)
                || "int".equals(text)
                || "boolean".equals(text)
                || "char".equals(text)
                || "byte".equals(text)
                || "short".equals(text)
                || "long".equals(text)
                || "float".equals(text)
                || "double".equals(text)
                || "return".equals(text)
                || "if".equals(text)
                || "else".equals(text)
                || "for".equals(text)
                || "while".equals(text)
                || "new".equals(text)
                || "this".equals(text)
                || "super".equals(text)
                || "final".equals(text)
                || "abstract".equals(text)
                || "extends".equals(text)
                || "implements".equals(text)
                || "import".equals(text)
                || "package".equals(text)
                || "throws".equals(text)
                || "null".equals(text);
    }
}