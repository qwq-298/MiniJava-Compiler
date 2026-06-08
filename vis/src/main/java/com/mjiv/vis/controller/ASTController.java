package com.mjiv.vis.controller;

import com.mjiv.vis.ast.ASTBuilder;
import com.mjiv.vis.ast.ASTConverter;
import com.mjiv.vis.ast.ASTNode;

import com.mjiv.vis.interpreter.MiniJavaLexer;
import com.mjiv.vis.interpreter.MiniJavaParser;

import org.antlr.v4.runtime.*;
import org.antlr.v4.runtime.tree.ParseTree;

import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/ast")
@CrossOrigin
public class ASTController {

    @PostMapping
    public Map<String, Object> generateAST(@RequestBody String code) {
        try {
            CharStream input = CharStreams.fromString(code);

            MiniJavaLexer lexer = new MiniJavaLexer(input);
            CommonTokenStream tokens = new CommonTokenStream(lexer);

            MiniJavaParser parser = new MiniJavaParser(tokens);

            // 收集语法错误
            List<String> errors = new ArrayList<>();
            parser.removeErrorListeners();
            parser.addErrorListener(new BaseErrorListener() {
                @Override
                public void syntaxError(Recognizer<?, ?> recognizer,
                        Object offendingSymbol, int line, int charPositionInLine,
                        String msg, RecognitionException e) {
                    errors.add("Bug: line " + line + ":" + charPositionInLine + " " + msg);
                }
            });

            ParseTree tree = parser.compilationUnit();

            // 有语法错误 → 直接返回错误信息
            if (!errors.isEmpty()) {
                Map<String, Object> result = new LinkedHashMap<>();
                result.put("error", true);
                result.put("errors", errors);
                return result;
            }

            ASTNode root = new ASTBuilder().build(tree);
            return ASTConverter.toJson(root);

        } catch (Exception e) {
            e.printStackTrace();
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("error", true);
            result.put("errors", List.of("Bug: " + e.getMessage()));
            return result;
        }
    }
}