package com.mjiv.vis.interpreter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClassInfo {
    public String name;

    public String parentName;

    public ClassInfo parent;

    List<ConstructorInfo> constructors = new ArrayList<>();
    
    Map<String, FieldInfo> fields = new LinkedHashMap<>();

    Map<String, List<MethodInfo>> methods = new LinkedHashMap<>();

    public ClassInfo(String name, String parentName) {
        this.name = name;
        this.parentName = parentName;
    }
}
