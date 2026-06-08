package com.mjiv.vis.ast;

import com.mjiv.vis.interpreter.MiniJavaLexer;
import com.mjiv.vis.interpreter.MiniJavaParser;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ASTConverterTest {

    @Test
    void testSimpleClassParsing() {
        String code = """
            class Main { 
               int x; 
               void test(){ 
               x = 1; } 
               }
            """;

        // Parse
        MiniJavaLexer lexer = new MiniJavaLexer(CharStreams.fromString(code));
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MiniJavaParser parser = new MiniJavaParser(tokens);
        ParseTree tree = parser.compilationUnit();

        // Build AST
        ASTNode root = new ASTBuilder().build(tree);

        // Convert to JSON
        @SuppressWarnings("unchecked")
        Map<String, Object> json = (Map<String, Object>) ASTConverter.toJson(root);

        // Debug: print actual JSON
        System.out.println("=== ACTUAL JSON OUTPUT ===");
        System.out.println(toPrettyString(json));
        System.out.println("=========================");

        // Verify top-level
        assertEquals("Class", json.get("type"), "Top-level type should be 'Class'");
        assertEquals("Main", json.get("name"), "Class name should be 'Main'");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> children = (List<Map<String, Object>>) json.get("children");
        assertNotNull(children, "Class should have children");
        assertEquals(2, children.size(), "Class should have 2 children (Field + Method)");

        // Verify Field
        Map<String, Object> field = children.get(0);
        assertEquals("Field", field.get("type"));
        assertEquals("x", field.get("name"));
        assertEquals("int", field.get("dataType"));

        // Verify Method
        Map<String, Object> method = children.get(1);
        assertEquals("Method", method.get("type"));
        assertEquals("test", method.get("name"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> body = (List<Map<String, Object>>) method.get("body");
        assertNotNull(body, "Method should have a body");
        assertEquals(1, body.size(), "Method body should have 1 statement");

        // Verify Assign
        Map<String, Object> assign = body.get(0);
        assertEquals("Assign", assign.get("type"));
        assertEquals("x", assign.get("left"));
        assertEquals(1, assign.get("right"));
    }

    /** Simple pretty-printer for debugging */
    private static String toPrettyString(Object obj) {
        if (obj == null) return "null";
        StringBuilder sb = new StringBuilder();
        toPretty(sb, obj, 0);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void toPretty(StringBuilder sb, Object obj, int indent) {
        String pad = "  ".repeat(indent);
        if (obj instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) obj;
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<String, Object> e : map.entrySet()) {
                sb.append(pad).append("  \"").append(e.getKey()).append("\": ");
                toPretty(sb, e.getValue(), indent + 1);
                if (++i < map.size()) sb.append(",");
                sb.append("\n");
            }
            sb.append(pad).append("}");
        } else if (obj instanceof List) {
            List<Object> list = (List<Object>) obj;
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                sb.append(pad).append("  ");
                toPretty(sb, list.get(i), indent + 1);
                if (i < list.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(pad).append("]");
        } else if (obj instanceof String) {
            sb.append("\"").append(obj).append("\"");
        } else {
            sb.append(obj);
        }
    }
}
