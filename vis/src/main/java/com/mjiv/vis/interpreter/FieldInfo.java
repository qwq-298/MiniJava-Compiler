package com.mjiv.vis.interpreter;

public class FieldInfo {
    String name;
    String typeName;
    boolean isArray;
    MiniJavaParser.ExpressionContext initializer;
    ClassInfo ownerClass;
    FieldInfo(
            String name,
            String typeName,
            boolean isArray,
            MiniJavaParser.ExpressionContext initializer,
            ClassInfo ownerClass) {

        this.name = name;
        this.typeName = typeName;
        this.isArray = isArray;
        this.initializer = initializer;
        this.ownerClass = ownerClass;
    }
}
