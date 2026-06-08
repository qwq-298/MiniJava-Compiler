package com.mjiv.vis.interpreter;
import java.util.List;
enum Type{
    INT,CHAR,STRING,BOOL,VOID,ARRAY,NULL,OBJECT,SUPER
}
public class Value {

    public final Type type;
    public final Object value;
    public String arrayBaseType=null;
    public boolean isLiteral = false;
    public static final Value CONTINUE = new Value(Type.VOID, null);
    public static final Value BREAK = new Value(Type.VOID, null);
    public boolean isDecimalLiteral = false;
    public boolean isfuncReturnValue=false;
    public ClassInfo declaredClass=null;
    public Value(Type type, Object value) {
        this.type = type;
        this.value = value;
    }

    // ===== 工厂方法（推荐） =====

    public static Value ofInt(int v) {
        return new Value(Type.INT, v);
    }

    public static Value ofBool(boolean v) {
        return new Value(Type.BOOL, v);
    }

    public static Value ofChar(char v) {
        return new Value(Type.CHAR, v);
    }

    public static Value ofString(String v) {
        return new Value(Type.STRING, v);
    }

    public static Value ofArray(List<Value> arr,String baseType){
        Value v = new Value(Type.ARRAY,arr);
        v.arrayBaseType = baseType;
        v.declaredClass = null;
        return v;
    }

    public static Value ofNull(String type){
        Value v=new Value(Type.NULL,null);
         if (type != null && type.endsWith("[]")) {
         v.arrayBaseType = type;
         } else {
         v.arrayBaseType = null;
         }
         return v;
    }

    public static Value ofObject(ObjectInstance obj,ClassInfo declaredClass){
        Value v = new Value(Type.OBJECT, obj);
        v.declaredClass = declaredClass;
        v.arrayBaseType =null;
        return v;
    }
    
    public static Value ofSuper(ObjectInstance obj,ClassInfo declaredClass){
        Value v = new Value(Type.SUPER,obj);
        v.declaredClass = declaredClass;
        return v;
    }

    public int asInt() {
        if (type != Type.INT)  {throw new RuntimeException("Value is not an int");
        }return (int) value;
    }

    public boolean asBool() {
        if (type != Type.BOOL)
            {throw new RuntimeException("Value is not a boolean");}
        return (boolean) value;
    }

    public char asChar() {
        if (type != Type.CHAR)
             {throw new RuntimeException("Value is not a char");}
        return (char) value;
    }

    public String asString() {
        if (type != Type.STRING)
             {throw new RuntimeException("Value is not a string");}
        return (String) value;
    }

    public List<Value> asArray(){
        if (type != Type.ARRAY)
             {throw new RuntimeException("Value is not an array");}
        return (List<Value>) value;
    }   

    public ObjectInstance asObject() {
        if (type != Type.OBJECT && type != Type.SUPER)
             {throw new RuntimeException("Value is not an object");}
        return (ObjectInstance) value;
    }
    
    public ObjectInstance asSuper(){
        if(type != Type.SUPER){
            throw new RuntimeException("Value is not a super reference");
        }
        return (ObjectInstance) value;
    }
    @Override
    public String toString() {
        return value.toString();
    }
}
