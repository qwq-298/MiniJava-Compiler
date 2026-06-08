package com.mjiv.vis.ast;

import java.util.*;

public class ASTConverter {

    public static Map<String, Object> toJson(ASTNode node) {
        if (node == null) return null;
        return convert(node);
    }

    private static Map<String, Object> convert(ASTNode node) {
        if (node == null) return null;

        String type = node.name;

        // =========================
        // 语义节点分发
        // =========================
        switch (type) {
            case "Class":   return convertClass(node);
            case "Field":   return convertField(node);
            case "Method":  return convertMethod(node);
            case "Assign":  return convertAssign(node);
        }

        // =========================
        // 噪声节点 → 穿透
        // =========================
        if (isNoise(type)) {
            return unwrapFirst(node);
        }

        // =========================
        // Token / 叶子
        // =========================
        if ("Token".equals(type)) {
            return leaf(node.text);
        }

        // =========================
        // 兜底：保留原始结构
        // =========================
        Map<String, Object> map = baseMap(node);
        map.put("children", convertChildren(node));
        return map;
    }

    // ==========================================
    // 各类型转换
    // ==========================================

    private static Map<String, Object> convertClass(ASTNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "Class");
        map.put("name", node.text);
        map.put("children", convertChildren(node));
        return map;
    }

    private static Map<String, Object> convertField(ASTNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "Field");
        map.put("name", node.text);
        if (node.props != null && node.props.containsKey("dataType")) {
            map.put("dataType", node.props.get("dataType"));
        }
        return map;
    }

    private static Map<String, Object> convertMethod(ASTNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "Method");
        map.put("name", node.text);

        List<Map<String, Object>> body = new ArrayList<>();
        List<Map<String, Object>> children = new ArrayList<>();

        for (ASTNode child : node.children) {
            if ("Block".equals(child.name)) {
                // Block → 展开所有语句
                body.addAll(flattenBlock(child));
            } else {
                Map<String, Object> c = convert(child);
                if (c != null) children.add(c);
            }
        }

        if (!body.isEmpty()) {
            map.put("body", body);
        }
        if (!children.isEmpty()) {
            map.put("children", children);
        }
        return map;
    }

    private static Map<String, Object> convertAssign(ASTNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "Assign");

        // left
        if (node.children.size() > 0) {
            ASTNode left = node.children.get(0);
            map.put("left", extractValue(left));
        }
        // right
        if (node.children.size() > 1) {
            ASTNode right = node.children.get(1);
            map.put("right", extractValue(right));
        }
        return map;
    }

    // ==========================================
    // 工具方法
    // ==========================================

    /** 展开 Block → 跳过噪声节点 → 得到语句列表 */
    private static List<Map<String, Object>> flattenBlock(ASTNode block) {
        List<Map<String, Object>> stmts = new ArrayList<>();
        for (ASTNode child : block.children) {
            ASTNode unwrapped = skipNoise(child);
            if (unwrapped != null) {
                Map<String, Object> m = convert(unwrapped);
                if (m != null) stmts.add(m);
            }
        }
        return stmts;
    }

    /** 跳过 Statement / BlockStatement 等噪声包装 */
    private static ASTNode skipNoise(ASTNode node) {
        if (node == null) return null;
        while (isNoise(node.name) && node.children != null && node.children.size() == 1) {
            node = node.children.get(0);
        }
        return node;
    }

    /** 提取叶子值（数字返回数值，标识符返回字符串） */
    private static Object extractValue(ASTNode node) {
        if (node == null) return null;
        // Identifier → 提取标识符名称
        if ("Identifier".equals(node.name)) {
            return getFirstTokenText(node);
        }
        // Literal / Number → 提取数值
        if ("Literal".equals(node.name) || "IntegerLiteral".equals(node.name)
                || "FloatLiteral".equals(node.name) || "Number".equals(node.name)) {
            String text = getFirstTokenText(node);
            if (text != null) {
                try {
                    if (text.contains(".")) return Double.parseDouble(text);
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {}
                return text;
            }
            return null;
        }
        // 裸 Token
        if ("Token".equals(node.name)) {
            String text = node.text;
            if (text != null) {
                try {
                    if (text.contains(".")) return Double.parseDouble(text);
                    return Integer.parseInt(text);
                } catch (NumberFormatException ignored) {}
                return text;
            }
            return null;
        }
        // 其他节点 → 递归提取第一个 Token 文本
        String text = getFirstTokenText(node);
        if (text != null) {
            try {
                if (text.contains(".")) return Double.parseDouble(text);
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {}
            return text;
        }
        return convert(node);
    }

    /** 递归获取第一个 Token 子节点的文本 */
    private static String getFirstTokenText(ASTNode node) {
        if (node == null) return null;
        if ("Token".equals(node.name)) return node.text;
        if (node.children != null) {
            for (ASTNode child : node.children) {
                String text = getFirstTokenText(child);
                if (text != null) return text;
            }
        }
        return null;
    }

    /** 噪声节点 → 返回第一个有效 child */
    private static Map<String, Object> unwrapFirst(ASTNode node) {
        if (node.children != null) {
            for (ASTNode child : node.children) {
                Map<String, Object> c = convert(child);
                if (c != null) return c;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> convertChildren(ASTNode node) {
        List<Map<String, Object>> list = new ArrayList<>();
        if (node.children != null) {
            for (ASTNode child : node.children) {
                Map<String, Object> c = convert(child);
                if (c != null) list.add(c);
            }
        }
        return list;
    }

    private static Map<String, Object> leaf(String text) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("text", text);
        return m;
    }

    private static Map<String, Object> baseMap(ASTNode node) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", node.name);
        if (node.text != null) map.put("name", node.text);
        return map;
    }

    private static boolean isNoise(String type) {
        return "Statement".equals(type)
            || "BlockStatement".equals(type)
            || "ClassBodyDeclaration".equals(type)
            || "ClassBody".equals(type)
            || "Primary".equals(type)
            || "Expression".equals(type);
    }
}