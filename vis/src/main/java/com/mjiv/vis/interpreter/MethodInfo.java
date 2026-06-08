package com.mjiv.vis.interpreter;

import java.util.List;

public class MethodInfo {

    String name;

    List<String> paramTypes;

    List<String> paramNames;

    String returnType;

    MiniJavaParser.MethodDeclarationContext ctx;

    ClassInfo ownerClass;
}
