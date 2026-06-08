package com.mjiv.vis.interpreter;

import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectInstance {
    ClassInfo clazz;

    Map<FieldInfo, Value> fields =
        new LinkedHashMap<>();

    ObjectInstance(ClassInfo clazz) {
        this.clazz = clazz;
    }
}
