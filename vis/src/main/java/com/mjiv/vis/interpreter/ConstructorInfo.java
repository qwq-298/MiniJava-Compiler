package com.mjiv.vis.interpreter;

import java.util.ArrayList;
import java.util.List;
public class ConstructorInfo {

    String name;

    ClassInfo ownerClass;

    MiniJavaParser.ConstructorDeclarationContext ctx;

    List<String> paramTypes = new ArrayList<>();

    List<String> paramNames = new ArrayList<>();
    
}