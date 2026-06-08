package com.mjiv.vis.interpreter;
import java.util.*;

import org.antlr.v4.runtime.BailErrorStrategy;
import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.antlr.v4.runtime.tree.ParseTree;

import com.mjiv.vis.run.Runresult;

public class Interpreter extends MiniJavaParserBaseVisitor<Value> {
    static class ReturnException extends RuntimeException {
        Value value=null;
        ReturnException(Value v) {
            this.value = v;
        }
    }    
    Stack<Map<String, Value>> scopes = new Stack<>();
    int loopDepth = 0;
    Stack<Integer> functionBases = new Stack<>();
    Map<String, List<MiniJavaParser.MethodDeclarationContext>> methods = new HashMap<>();
    Map<String, ClassInfo> classes = new HashMap<>();
    Stack<ObjectInstance> currentThis = new Stack<>();
    private Deque<ConstructorInfo> constructorStack = new ArrayDeque<>();
    Stack<MethodInfo> currentMethod = new Stack<>();
    @Override
    public Value visitCompilationUnit(MiniJavaParser.CompilationUnitContext ctx) {
        scopes.clear();
        methods.clear();
        classes.clear();
        for (MiniJavaParser.ClassDeclarationContext c: ctx.classDeclaration()) {
        visit(c);
        }
        for (MiniJavaParser.ClassDeclarationContext c: ctx.classDeclaration()) {
        parseClassMembers(c);
        }
        linkInheritance();
        validateOverrides();
        for (MiniJavaParser.MethodDeclarationContext m : ctx.methodDeclaration()) {
            String name = m.identifier().getText();
            methods.computeIfAbsent(name, k -> new ArrayList<>()).add(m);
        }
        for (Map.Entry<String, List<MiniJavaParser.MethodDeclarationContext>> entry : methods.entrySet()) {
        List<MiniJavaParser.MethodDeclarationContext> methodList = entry.getValue();
        for (int i = 0; i < methodList.size(); i++) {
        for (int j = i + 1; j < methodList.size(); j++) {
            if (hasSameSignature(methodList.get(i), methodList.get(j))) {
                throw new RuntimeException("Duplicate method signature");
            }
        }
        }
        }
        if (!methods.containsKey("main")) {
            throw new RuntimeException("No main function found");
        }
        MiniJavaParser.MethodDeclarationContext mainMethod = findMain();
        List<Value> args = new ArrayList<>();
        boolean hasArgs = mainMethod.formalParameters().formalParameterList() != null;
        if (hasArgs) {
            args.add(Value.ofArray(new ArrayList<>(), "string[]"));
        }
        return callFunctionDirect(mainMethod, args);
    }
    @Override
    public Value visitMethodDeclaration(MiniJavaParser.MethodDeclarationContext ctx) {
        scopes.push(new HashMap<>()); 
        try {
            visit(ctx.block());
        } catch (ReturnException r) {
            scopes.pop();
            return r.value;
        }
        scopes.pop();
        return null;
    }
    @Override
    public Value visitClassDeclaration( MiniJavaParser.ClassDeclarationContext ctx) {
    String className = ctx.identifier().getText();
    String parentName = null;
    if (ctx.parentClassDeclaration() != null) {
        parentName = ctx.parentClassDeclaration().identifier().getText();
    }
    if (classes.containsKey(className)) {
        throw new RuntimeException("Duplicate class: " + className);
    }
    ClassInfo clazz =new ClassInfo(className, parentName);
    classes.put(className, clazz);
    return null;
    }
    @Override
    public Value visitExpression(MiniJavaParser.ExpressionContext ctx) {
        if(ctx.NEW()!=null){
            return handleNew(ctx.creator());
        }
        if (ctx.bop != null && ctx.bop.getText().equals(".")) {
            if(ctx.methodCall()!=null){
               Value left = visit(ctx.expression(0));
               if (left.type == Type.NULL) {throw new RuntimeException("Null pointer");}
               if (left.type != Type.OBJECT && left.type != Type.SUPER) {
                  throw new RuntimeException("Method call on non-object");
               }
              ObjectInstance obj = null;
              if(left.type == Type.OBJECT){
                 obj = left.asObject();
              }else if(left.type == Type.SUPER){
                 obj = left.asSuper();
              }
              String methodName = ctx.methodCall().identifier().getText();
              List<Value> args = new ArrayList<>();
              if (ctx.methodCall().arguments().expressionList() != null) {
              for (MiniJavaParser.ExpressionContext e: ctx.methodCall().arguments().expressionList().expression()) {
              args.add(visit(e));
              }
              }
              if(left.type == Type.SUPER){
                return invokeSuperMethod(left,methodName,args);
              }
              return invokeMethod(left,methodName,args);
            }
           /*  Value left = visit(ctx.expression(0));
            if (ctx.identifier() != null) {
            if (left.type != Type.OBJECT&&left.type != Type.SUPER) {throw new RuntimeException( "Field access on non-object");}
            ObjectInstance obj = null;
            if(left.type == Type.OBJECT){
                 obj = left.asObject();
            }else if(left.type == Type.SUPER){
                 obj = left.asSuper();
            }
            String fieldName = ctx.identifier().getText();
            if(left.type == Type.SUPER){
                return getSuperField(obj,fieldName);
            }
            return getField(obj,fieldName);
            } */
            Value objVal = visit(ctx.expression(0));
            if (objVal.type != Type.OBJECT && objVal.type != Type.SUPER)
            {
            throw new RuntimeException("Field access on non-object");
            }
            String fieldName = ctx.identifier().getText();
            if (objVal.type == Type.SUPER) {
            return getSuperField(objVal,fieldName);
            }
            return getField(objVal,fieldName);
        }
        if (ctx.methodCall() != null) {
            return visit(ctx.methodCall());
        }
        if (ctx.bop != null && isAssignment(ctx.bop.getText())) {
            String op = ctx.bop.getText();
            MiniJavaParser.ExpressionContext lhs = ctx.expression(0);
            Value right = visit(ctx.expression(1));

            boolean isVar = lhs.primary() != null && lhs.primary().identifier() != null;
            boolean isArrayAccess = lhs.getChildCount() == 4 && lhs.getChild(1).getText().equals("[");
            boolean isFieldAccess = lhs.bop != null && lhs.bop.getText().equals(".") && lhs.identifier() != null;
            //boolean isFieldAccess =lhs.getChildCount() >= 3&&lhs.getChild(1).getText().equals(".")&&lhs.expression(1).identifier() != null;
            if (!isVar && !isArrayAccess && !isFieldAccess) {
                throw new RuntimeException("Left-hand side must be variable or array element");
            }
             
            if (isVar) {
                String varname = lhs.primary().identifier().getText();
                Map<String, Value> scope = findScope(varname);
                if(scope!=null){
                Value left = scope.get(varname);
                /*if (left.arrayBaseType != null&& scope.containsKey(varname))*/
                if (left.type == Type.ARRAY || (left.type == Type.NULL && left.arrayBaseType != null))
                 {
                    if (right.type == Type.NULL) {
                        scope.put(varname, Value.ofNull(left.arrayBaseType));
                        return right;
                    }
                    if (right.type != Type.ARRAY) {
                        throw new RuntimeException("Type mismatch: expected array");
                    }
                    if (!left.arrayBaseType.equals(right.arrayBaseType)) {
                        throw new RuntimeException("Array type mismatch");
                    }
                    scope.put(varname, right);
                    return right;
                }
                Value result = applyAssign(op, left, right);
                if (left.type == Type.OBJECT && result.type == Type.OBJECT) {
                result = Value.ofObject(result.asObject(),left.declaredClass);
                }
                scope.put(varname, result);
                return result;
                }
                else if (!currentThis.isEmpty()) {
                    Value thisVal = Value.ofObject(currentThis.peek(),currentThis.peek().clazz);
                    if (hasField(thisVal.declaredClass, varname)) {
                        Value old = getField(thisVal, varname);
                        if (right.type == Type.NULL) {
                        if (!isReferenceValue(old)) {throw new RuntimeException("Cannot assign null");}
                        setField(thisVal,varname,right );
                        return right;
                        }
                    Value result = applyAssign(op, old, right);
                    if(old.type == Type.OBJECT && result.type == Type.OBJECT){
                        result = Value.ofObject(result.asObject(),old.declaredClass);
                    }   
                    setField( thisVal, varname, result );
                    return result;
                  }
               }
               else{
                throw new RuntimeException("Variable not found: " + varname);
               }
            }

            if (isArrayAccess) {
                Value arr = visit(lhs.expression(0));
                Value index = visit(lhs.expression(1));

                if (arr.type == Type.NULL) {
                    throw new RuntimeException("Null pointer");
                }
                if (arr.type != Type.ARRAY) {
                    throw new RuntimeException("Not an array");
                }
                if (index.type == Type.CHAR) {
                    index = Value.ofInt(index.asChar()); // char → int
                }
                if (index.type != Type.INT) {
                    throw new RuntimeException("Index must be int");
                }

                int i = index.asInt();
                if (i < 0 || i >= arr.asArray().size()) {
                    throw new RuntimeException("Array out-of-bounds");
                }

                if (right.type == Type.NULL) {
                    if(arr.arrayBaseType.equals("int[]") || arr.arrayBaseType.equals("char[]") || arr.arrayBaseType.equals("boolean[]")){
                        throw new RuntimeException("Cannot assign null to array of primitive type");
                    }
                    arr.asArray().set(i, Value.ofNull(arr.arrayBaseType));
                    return right; 
                }

                if (arr.arrayBaseType.equals("int[]")) {
                    if(right.type == Type.CHAR && right.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char to int");
                    right = docast("int", right);
                } else if (arr.arrayBaseType.equals("char[]")) {
                    if(right.type == Type.INT && right.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char to int");
                } else if (arr.arrayBaseType.equals("boolean[]")) {
                    right = docast("boolean", right);
                } else if (arr.arrayBaseType.equals("string[]")) {
                    right = docast("string", right);
                }
                if (right.type == Type.OBJECT) {
                String elemType = arr.arrayBaseType.replace("[]", "");
                ClassInfo declClass = classes.get(elemType);
                if (declClass != null) {
                right = Value.ofObject(right.asObject(),declClass);
                }
                }
                arr.asArray().set(i, right);
                return right;
            }

            if(isFieldAccess){
                Value objVal = visit(lhs.expression(0));
                if (objVal.type == Type.NULL) {
                    throw new RuntimeException("Null pointer");
                }
                if (objVal.type != Type.OBJECT&&objVal.type != Type.SUPER) {
                    throw new RuntimeException( "Field assignment on non-object");
                }
                ObjectInstance obj = null;
                if (objVal.type == Type.SUPER) {
                    obj = objVal.asSuper();
                } else {
                    obj = objVal.asObject();
                }
                String fieldName = lhs.identifier().getText();
                ClassInfo lookupStart;
                if (objVal.type == Type.SUPER) {
                //lookupStart = obj.clazz.parent;
                lookupStart = objVal.declaredClass.parent;
                } else {
                //lookupStart = obj.clazz;
                lookupStart = objVal.declaredClass;
                }
                FieldInfo field =findFieldFromClass(lookupStart,fieldName);
                if (field == null) {throw new RuntimeException("Unknown field: " + fieldName);}
                /* if (!hasFieldFromClass(lookupStart,fieldName)) {
                    throw new RuntimeException("Unknown field: " + fieldName);
                } */
                //Value old = obj.fields.get(field);
                Value old = objVal.asObject().fields.get(field);
                if (right.type == Type.NULL) {
                if (!isReferenceValue(old)) {throw new RuntimeException("Cannot assign null");}
                objVal.asObject().fields.put(field, right);
                return right;
                }
                //if (old.type == Type.OBJECT||old.type == Type.NULL) {
                if (right.type == Type.OBJECT) {
                ClassInfo fieldClass = classes.get(field.typeName);
                if (fieldClass != null) {
                    right = Value.ofObject(right.asObject(),fieldClass);
                }
                //right = Value.ofObject(right.asObject(),old.declaredClass);
                objVal.asObject().fields.put(field, right);
                return right;
                }
                //}
                Value result = applyAssign(op, old, right);
                objVal.asObject().fields.put(field,result);
                return result;
            }
        }

        if (ctx.getChildCount() == 4 && ctx.getChild(1).getText().equals("[")) {
            Value arr = visit(ctx.expression(0));
            Value index = visit(ctx.expression(1));  
            if(arr.type==Type.NULL){
                throw new RuntimeException("Null pointer exception: cannot access array element of null");
            }  
            if(arr.type != Type.ARRAY){
                throw new RuntimeException("Type mismatch: expected array");
            }
            int idx;
            if (index.type == Type.INT) {
                idx = index.asInt();
            } else if (index.type == Type.CHAR) {
                idx = (int) index.asChar(); 
            } else {
                throw new RuntimeException("Index must be int");
            }
            List<Value> list = arr.asArray();
            if (idx < 0 || idx >= list.size()) {
                throw new RuntimeException("Array out-of-bounds");
            }
            return list.get(idx);
        }

        if (ctx.getChildCount() == 4 && ctx.getChild(0).getText().equals("(") && ctx.getChild(2).getText().equals(")") && ctx.typeType() != null) {
            String type = ctx.typeType().getText();
            Value val = visit(ctx.expression(0));
            return docast(type, val);
        }

        if (ctx.postfix != null) {
            MiniJavaParser.ExpressionContext lhs = ctx.expression(0);
            Value left = visit(lhs);
            if(left.type != Type.CHAR && left.type != Type.INT){
                throw new RuntimeException("Operand of increment/decrement operator must be an integer or char.");
            }
            
            Value result;
            int a = left.type == Type.INT ? left.asInt() : (int)left.asChar();
            if(ctx.postfix.getText().equals("++")){
                result = left.type == Type.INT ? Value.ofInt(a + 1) : Value.ofChar((char)(a + 1));
            } else if(ctx.postfix.getText().equals("--")){
                result = left.type == Type.INT ? Value.ofInt(a - 1) : Value.ofChar((char)(a - 1));
            } else {
                throw new RuntimeException("Invalid postfix operator: " + ctx.postfix.getText());
            }

            if(lhs.primary() != null && lhs.primary().identifier() != null) {
                String varname = lhs.primary().identifier().getText();
                Map<String, Value> scope = findScope(varname);
                if(scope != null) {
                scope.put(varname, result);
                return left;
                }
                if(!currentThis.isEmpty()) {
                 ObjectInstance obj = currentThis.peek();
                 ClassInfo declClass;
                if (!currentMethod.isEmpty()) {
                declClass = currentMethod.peek().ownerClass;
                } else {
                declClass = obj.clazz;
                }
                Value thisVal = Value.ofObject(obj, declClass);
                if(hasField(thisVal.declaredClass, varname)) {
                setField(thisVal, varname, result);
                return left;
                }
                }
                throw new RuntimeException("Variable not found: " + varname);
            } else if (lhs.getChildCount() == 4 && lhs.getChild(1).getText().equals("[")) {
                Value arr = visit(lhs.expression(0));
                Value index = visit(lhs.expression(1));
                arr.asArray().set(index.type == Type.CHAR ? index.asChar() : index.asInt(), result);
                return left;
            } else {
                throw new RuntimeException("Operand must be a variable or array element.");
            }
        }
            
        if (ctx.bop != null && ctx.bop.getText().equals("?")) {
            /* Value cond = visit(ctx.expression(0));
            if (cond.type != Type.BOOL) {
                throw new RuntimeException("Condition of ternary operator must be boolean.");
            }
            return cond.asBool() ? visit(ctx.expression(1)) : visit(ctx.expression(2)); */
            Value cond = visit(ctx.expression(0));

    if (cond.type != Type.BOOL) {
        throw new RuntimeException("Condition of ternary operator must be boolean.");
    }
    Value trueVal = visit(ctx.expression(1));
    Value falseVal = visit(ctx.expression(2));
    Type resultType;

    if (trueVal.type == falseVal.type) {
        resultType = trueVal.type;
    }
    else if (
        (trueVal.type == Type.INT && falseVal.type == Type.CHAR) ||
        (trueVal.type == Type.CHAR && falseVal.type == Type.INT)
    ) {
        resultType = Type.INT;

        if (trueVal.type == Type.CHAR) {
            trueVal = Value.ofInt(trueVal.asChar());
        }

        if (falseVal.type == Type.CHAR) {
            falseVal = Value.ofInt(falseVal.asChar());
        }
    }
    else {
        throw new RuntimeException("Type mismatch in ternary expression");
    }

    return cond.asBool() ? trueVal : falseVal;
        }

        if (ctx.primary() != null) {
            return visit(ctx.primary());
        } else if (ctx.expression().size() == 1&& ctx.typeType() == null) {
            Value v = visit(ctx.expression(0));
            String op = ctx.getChild(0).getText();
            if(op.equals("not") || op.equals("NOT") || op.equals("!")){
                if(v.type != Type.BOOL) throw new RuntimeException("Expected bool for not");
                return Value.ofBool(!v.asBool());
            }
            if(op.equals("~")){
                if(v.type == Type.CHAR) v = Value.ofInt(v.asChar());
                if(v.type != Type.INT) throw new RuntimeException("Expected int for ~");
                return Value.ofInt(~v.asInt()); 
            }
            if(op.equals("++") || op.equals("--")){
                MiniJavaParser.ExpressionContext lhs = ctx.expression(0);
                if(v.type != Type.CHAR && v.type != Type.INT){
                    throw new RuntimeException("Operand must be integer or char.");
                }
                int a = v.type == Type.INT ? v.asInt() : (int)v.asChar();
                Value result;
                if(op.equals("++")){
                    result = v.type == Type.INT ? Value.ofInt(a + 1) : Value.ofChar((char)(a + 1));
                } else {
                    result = v.type == Type.INT ? Value.ofInt(a - 1) : Value.ofChar((char)(a - 1));
                }
                
                if(lhs.primary() != null && lhs.primary().identifier() != null) {
                    String varname = lhs.primary().identifier().getText();
                    Map<String, Value> scope = findScope(varname);
                    if(scope!=null){
                    scope.put(varname, result);
                    return result; // Prefix returns new value
                    }
                    if(!currentThis.isEmpty()) {
                     ObjectInstance obj = currentThis.peek();
                     ClassInfo declClass;
                     if (!currentMethod.isEmpty()) {
                     declClass = currentMethod.peek().ownerClass;
                     } else {
                     declClass = obj.clazz;
                    }
                    Value thisVal = Value.ofObject(obj, declClass);
                    if(hasField(thisVal.declaredClass, varname)) {
                    setField(thisVal, varname, result);
                    return result;
                    }
                    }
                    throw new RuntimeException("wrong vari");
                } else if (lhs.getChildCount() == 4 && lhs.getChild(1).getText().equals("[")) {
                    Value arr = visit(lhs.expression(0));
                    Value index = visit(lhs.expression(1));
                    arr.asArray().set(index.type == Type.CHAR ? index.asChar() : index.asInt(), result);
                    return result;
                } else {
                    throw new RuntimeException("Operand must be a variable or array element.");
                }
            }
            if (op.equals("-")) {
                if(v.type == Type.CHAR) v = Value.ofInt(v.asChar());
                if(v.type != Type.INT) throw new RuntimeException("Expected int for -");
                return Value.ofInt(-v.asInt());
            } 
            if(op.equals("+")){
                if(v.type == Type.CHAR) v = Value.ofInt(v.asChar());
                if(v.type != Type.INT) throw new RuntimeException("Expected int for +");
                return Value.ofInt(+v.asInt());
            }
            throw new RuntimeException("Unknown unary operator: " + op);
        } else {
            Value vleft = visit(ctx.expression(0));
            String op = ctx.getChild(1).getText();
            if (op.equals("instanceof")) {
            String targetType = ctx.typeType().getText();
            if (!classes.containsKey(targetType)) {
            throw new RuntimeException("instanceof target must be class");
            }
            if (vleft.type == Type.NULL) {
            return Value.ofBool(false);
            }
            if (vleft.type != Type.OBJECT) {
            throw new RuntimeException("instanceof on non-object");
            }
            ClassInfo decl = vleft.declaredClass;
            ClassInfo target = classes.get(targetType);
            if (!isSameTree(decl, target)) {
            throw new RuntimeException(
            "Unrelated class types"
            );
            }
            ClassInfo real = vleft.asObject().clazz;
            return Value.ofBool(isAssignable(real, target));
            }
            if (op.equals("and") || op.equals("&&")) {
                if (vleft.type != Type.BOOL) throw new RuntimeException("Expected bool for AND");
                if (!vleft.asBool()) return Value.ofBool(false);
            }
            if (op.equals("or") || op.equals("||")) {
                if (vleft.type != Type.BOOL) throw new RuntimeException("Expected bool for OR");
                if (vleft.asBool()) return Value.ofBool(true);
            }
            
            Value vright = visit(ctx.expression(1));
            switch (op) {
                case "+":
                    if(vleft.type == Type.STRING || vright.type == Type.STRING){
                        return Value.ofString(formatValue(vleft) + formatValue(vright));
                    }
                    if(vleft.type == Type.INT && vright.type == Type.INT){
                        return Value.ofInt(vleft.asInt() + vright.asInt());
                    } else if((vleft.type == Type.INT && vright.type == Type.CHAR)){
                        return Value.ofInt(vleft.asInt() + vright.asChar());
                    } else if((vleft.type == Type.CHAR && vright.type == Type.INT)){
                        return Value.ofInt(vleft.asChar() + vright.asInt());
                    } else if(vleft.type == Type.CHAR && vright.type == Type.CHAR){
                        return Value.ofInt(vleft.asChar() + vright.asChar());
                    }
                    throw new RuntimeException("Invalid operand types for +");
                case "-":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int for -");
                    return Value.ofInt(vleft.asInt() - vright.asInt());
                case "*":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int for *");
                    return Value.ofInt(vleft.asInt() * vright.asInt());
                case "/":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int for /");
                    if(vright.asInt() == 0) throw new RuntimeException("Division by zero");   
                    return Value.ofInt(vleft.asInt() / vright.asInt());
                case "%":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int for %");
                    if(vright.asInt() == 0) throw new RuntimeException("Modulo by zero");
                    return Value.ofInt(vleft.asInt() % vright.asInt());
                case "<<":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt(vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt(vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() << (vright.asInt() & 0x1F));
                case ">>":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() >> (vright.asInt() & 0x1F));
                case ">>>":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() >>> (vright.asInt() & 0x1F));
                case ">":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofBool(vleft.asInt() > vright.asInt());
                case "<":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofBool(vleft.asInt() < vright.asInt());
                case ">=":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofBool(vleft.asInt() >= vright.asInt());
                case "<=":
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofBool(vleft.asInt() <= vright.asInt());
                case "==":
                    if (vleft.type == Type.OBJECT || vleft.type == Type.NULL ||
                       vright.type == Type.OBJECT || vright.type == Type.NULL) {
                    if ((vleft.type == Type.OBJECT || vleft.type == Type.NULL) &&
                    (vright.type == Type.OBJECT || vright.type == Type.NULL)) {
                    if (vleft.type == Type.NULL && vright.type == Type.NULL)
                        return Value.ofBool(true);
                    if (vleft.type == Type.NULL || vright.type == Type.NULL)
                        return Value.ofBool(false);
                    ClassInfo c1 = vleft.declaredClass;
                    ClassInfo c2 = vright.declaredClass;
                    if (!isSameTree(c1, c2)) {
                    throw new RuntimeException("Type mismatch");
                    }
                    return Value.ofBool(vleft.asObject() == vright.asObject());
                    }
                    throw new RuntimeException("Type mismatch");
                    }
                    if(vleft.type==Type.ARRAY||vleft.type==Type.NULL){
                        if(vright.type!=Type.ARRAY&&vright.type!=Type.NULL) throw new RuntimeException("Type mismatch");
                        if(vleft.type==Type.NULL&&vright.type==Type.NULL) return Value.ofBool(true);
                        if(vleft.type==Type.NULL||vright.type==Type.NULL) return Value.ofBool(false);
                        return Value.ofBool(vleft.value == vright.value); 
                    }
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != vright.type) throw new RuntimeException("Type mismatch");
                    switch (vleft.type) {
                        case INT: return Value.ofBool(vleft.asInt() == vright.asInt());
                        case BOOL: return Value.ofBool(vleft.asBool() == vright.asBool());
                        case STRING: return Value.ofBool(vleft.asString().equals(vright.asString()));
                        default: throw new RuntimeException("Invalid types");
                    }
                case "!=":
                    if (vleft.type == Type.OBJECT || vleft.type == Type.NULL ||
                       vright.type == Type.OBJECT || vright.type == Type.NULL) {
                    if ((vleft.type == Type.OBJECT || vleft.type == Type.NULL) &&
                    (vright.type == Type.OBJECT || vright.type == Type.NULL)) {
                    if (vleft.type == Type.NULL && vright.type == Type.NULL)
                        return Value.ofBool(false);
                    if (vleft.type == Type.NULL || vright.type == Type.NULL)
                        return Value.ofBool(true);
                    ClassInfo c1 = vleft.declaredClass;
                    ClassInfo c2 = vright.declaredClass;
                    if (!isSameTree(c1, c2)) {
                    throw new RuntimeException("Type mismatch");
                    }
                    return Value.ofBool(vleft.asObject() != vright.asObject());
                    }
                    throw new RuntimeException("Type mismatch");
                    }
                    if(vleft.type==Type.ARRAY||vleft.type==Type.NULL){
                        if(vright.type!=Type.ARRAY&&vright.type!=Type.NULL) throw new RuntimeException("Type mismatch");
                        if(vleft.type==Type.NULL&&vright.type==Type.NULL) return Value.ofBool(false);
                        if(vleft.type==Type.NULL||vright.type==Type.NULL) return Value.ofBool(true);
                        return Value.ofBool(vleft.value != vright.value);
                    }
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != vright.type) throw new RuntimeException("Type mismatch");
                    switch (vleft.type) {
                        case INT: return Value.ofBool(vleft.asInt() != vright.asInt());
                        case BOOL: return Value.ofBool(vleft.asBool() != vright.asBool());
                        case STRING: return Value.ofBool(!vleft.asString().equals(vright.asString()));
                        default: throw new RuntimeException("Invalid types");
                    }
                case "^":
                    if(vleft.type == Type.BOOL && vright.type == Type.BOOL) return Value.ofBool(vleft.asBool() ^ vright.asBool());
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() ^ vright.asInt());
                case "&":
                    if(vleft.type == Type.BOOL && vright.type == Type.BOOL) return Value.ofBool(vleft.asBool() & vright.asBool());
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() & vright.asInt());
                case "|":
                    if(vleft.type == Type.BOOL && vright.type == Type.BOOL) return Value.ofBool(vleft.asBool() | vright.asBool());
                    if(vleft.type==Type.CHAR) vleft = Value.ofInt((int)vleft.asChar());
                    if(vright.type==Type.CHAR) vright = Value.ofInt((int)vright.asChar());
                    if(vleft.type != Type.INT || vright.type != Type.INT) throw new RuntimeException("Expected int");
                    return Value.ofInt(vleft.asInt() | vright.asInt());
                case "and": case "&&":
                    if(vleft.type != Type.BOOL || vright.type != Type.BOOL) throw new RuntimeException("Expected bool");
                    return Value.ofBool(vleft.asBool() && vright.asBool());
                case "or": case "||":
                    if(vleft.type != Type.BOOL || vright.type != Type.BOOL) throw new RuntimeException("Expected bool");
                    return Value.ofBool(vleft.asBool() || vright.asBool());
                default:
                    throw new RuntimeException("Unknown operator: " + op);
            }
        }
    }
    @Override
    public Value visitPrimary(MiniJavaParser.PrimaryContext ctx) {
        if (ctx.expression() != null) return visit(ctx.expression());  
        if (ctx.literal() != null) return visit(ctx.literal());
        if(ctx.identifier() != null) {
            String name = ctx.identifier().getText();
            Map<String, Value> scope = findScope(name);
            if (scope != null && scope.containsKey(name)) {return scope.get(name);}
            if (!currentThis.isEmpty()) {
            ObjectInstance obj = currentThis.peek();
           /*  if (hasField(obj.clazz, name)) {
                return getField(obj, name);
            } */
            ClassInfo accessClass;
            if (!currentMethod.isEmpty()) {
                accessClass = currentMethod.peek().ownerClass;
            } else {
                accessClass = obj.clazz;
            }
            if (hasField(accessClass, name)) {
            Value thisVal = Value.ofObject(obj,accessClass);
            return getField(thisVal, name);
            }
            }
        throw new RuntimeException(
            "Variable not found: " + name
        );
        }
        if(ctx.THIS()!= null){
            if (currentThis.isEmpty()) {
                throw new RuntimeException("this used outside of any class");
            }
            ObjectInstance obj = currentThis.peek();
            ClassInfo declClass;
            if (!currentMethod.isEmpty()) {
            declClass = currentMethod.peek().ownerClass;
            } else {
            declClass = obj.clazz;
            }
            return Value.ofObject(obj, declClass);  
        }
        if (ctx.SUPER() != null) {
        if (currentThis.isEmpty()) {throw new RuntimeException( "super outside method");}
        ObjectInstance obj = currentThis.peek();
        ClassInfo declClass;
        if (!currentMethod.isEmpty()) {
        declClass = currentMethod.peek().ownerClass;
        } else {
        declClass = obj.clazz;
        }
        return Value.ofSuper(obj, declClass);
        }
        throw new RuntimeException("Invalid primary expression.");
    }
    @Override
    public Value visitLiteral(MiniJavaParser.LiteralContext ctx) {
        if (ctx.DECIMAL_LITERAL() != null) {
            Value v = Value.ofInt((int)Long.parseLong(ctx.DECIMAL_LITERAL().getText()));
            v.isLiteral = true;
            v.isDecimalLiteral = true;
            return v;
        } else if (ctx.CHAR_LITERAL() != null) {
            String text = ctx.CHAR_LITERAL().getText();
            if (text.length() == 4 && text.charAt(1) == '\\') {
                char c = text.charAt(2);
                if (c == 'n') return Value.ofChar('\n');
                if (c == 't') return Value.ofChar('\t');
                if (c == '\'') return Value.ofChar('\'');
                if (c == '\\') return Value.ofChar('\\');
                if (c == '\"') return Value.ofChar('\"');
                if(c  =='0') return Value.ofChar('\0');
            }
            return Value.ofChar(text.charAt(1)); 
        } 
       else if (ctx.STRING_LITERAL() != null) {
            String text = ctx.STRING_LITERAL().getText();
            String inner = text.substring(1, text.length() - 1);
            inner = inner.replace("\\n", "\n").replace("\\t", "\t")
                         .replace("\\\"", "\"").replace("\\\\", "\\");
            return Value.ofString(inner);
        } else if (ctx.BOOL_LITERAL() != null) {
            return Value.ofBool(ctx.BOOL_LITERAL().getText().equals("true"));    
        } else if(ctx.NULL_LITERAL()!=null) {
            return Value.ofNull("unknown");
        } else {
            throw new RuntimeException("none of ctx literal");
        } 
    }
    @Override
    public Value visitBlock(MiniJavaParser.BlockContext ctx) {
        scopes.push(new HashMap<>());
        try {
            for (MiniJavaParser.BlockStatementContext stmtCtx : ctx.blockStatement()) {
                Value v = visit(stmtCtx);
                if (v == Value.BREAK || v == Value.CONTINUE) return v;
            }
            return null;
        } finally {
            scopes.pop(); 
        }
    }
    @Override
    public Value visitLocalVariableDeclaration(MiniJavaParser.LocalVariableDeclarationContext ctx) {
        if (ctx.VAR() != null) {
            String name = ctx.identifier().getText();
            Value val = visit(ctx.expression());
            if (val.type == Type.NULL) throw new RuntimeException("Cannot infer type from null");
            if (val.type == Type.ARRAY && val.arrayBaseType != null && val.arrayBaseType.contains("unknown")) {
                throw new RuntimeException("Cannot infer type from empty array");
            }
            scopes.peek().put(name, val);
            return val;
        }
        
        String type = ctx.typeType().getText();
        MiniJavaParser.VariableDeclaratorContext var = ctx.variableDeclarator();
        String name = var.identifier().getText();
        Value val;
        
        if (var.variableInitializer() != null) {
            MiniJavaParser.VariableInitializerContext init = var.variableInitializer();
            if (init.arrayInitializer() != null) {
                val = evalArrayWithType(init.arrayInitializer(), type);
                if (val.arrayBaseType != null && val.arrayBaseType.contains("unknown")) {
                    val = fixUnknownArray(val, type);
                }
            } else {
                val = visit(init.expression());
                if(val.type == Type.NULL){
                     if (!isReferenceType(type)) { throw new RuntimeException("null cannot be assigned to "+ type);}
                      val = Value.ofNull(type);
                      if (!type.endsWith("[]")&& classes.containsKey(type)) {
                          val.declaredClass = classes.get(type);
                      }
                } else {
                if (classes.containsKey(type)) {
                if (val.type == Type.NULL) {
                    val = Value.ofNull(type);
                }
                else {
                    if (val.type != Type.OBJECT) {
                    throw new RuntimeException("Expected object type: " + type);
                    }
                    ObjectInstance obj = val.asObject();
                    ClassInfo declClass = classes.get(type);
                    if (!isAssignable(obj.clazz, declClass)) {
                    throw new RuntimeException("Type mismatch");
                    }
                    val = Value.ofObject(obj, declClass);
                }    
                }
                else{
                if(type.equals("int") && val.type == Type.CHAR && val.isfuncReturnValue) {throw new RuntimeException("Cannot implicitly convert func return char to int");}
                if (type.equals("char")&& val.type == Type.INT&& !val.isDecimalLiteral) {throw new RuntimeException("Cannot implicitly convert int to char without explicit cast");}
                val = docast(type, val);
                }
                }
            }
        } else {
            if(type.endsWith("[]")){
                val = Value.ofNull(type); 
            }
            else if (classes.containsKey(type)) {
                val = Value.ofNull(type);
                val.declaredClass = classes.get(type);
            } 
            else {
                switch (type) {
                    case "int": val = Value.ofInt(0); break;
                    case "char": val = Value.ofChar((char)0); break;
                    case "boolean": val = Value.ofBool(false); break;
                    case "string": val = Value.ofString(""); break;
                    default: throw new RuntimeException("Unknown type: " + type);
                }
            }
        }
        
        Map<String, Value> currentScope = scopes.peek();
        if(currentScope.containsKey(name)) throw new RuntimeException("Variable already declared");
        currentScope.put(name, val);
        return val;
    }
    @Override
    public Value visitArrayInitializer(MiniJavaParser.ArrayInitializerContext ctx) {
        List<Value> list = new ArrayList<>();
        if (ctx.variableInitializer() == null || ctx.variableInitializer().isEmpty()) {
            return Value.ofArray(list, "unknown[]");
        }
        for (MiniJavaParser.VariableInitializerContext vctx : ctx.variableInitializer()) {
            Value v = visit(vctx);
            if (v.type == Type.ARRAY && v.arrayBaseType == null) {
                v.arrayBaseType = inferArrayBaseType(v.asArray());
            }
            list.add(v); 
        }
        String type = inferArrayBaseType(list);
        return Value.ofArray(list, type + "[]");
        //return Value.ofArray(list, "unknown[]");
    } 
    @Override
    public Value visitStatement(MiniJavaParser.StatementContext ctx) {
        Value result = null;
        if (ctx.block() != null) result = visit(ctx.block());
        else if (ctx.IF() != null) result = handleIf(ctx);
        else if(ctx.WHILE() !=null) result = handleWhile(ctx);
        else if(ctx.FOR()!=null) result = handleFor(ctx);
        else if (ctx.BREAK() != null) {
            if(loopDepth == 0) throw new RuntimeException("Break statement not within a loop.");
            return Value.BREAK;
        } else if (ctx.CONTINUE() != null) {
            if(loopDepth == 0) throw new RuntimeException("Continue statement not within a loop.");
            return Value.CONTINUE;
        } else if (ctx.RETURN() != null) {
            Value v = null;
            /*  if (constructorDepth > 0&&ctx.expression() != null) {
                throw new RuntimeException( "Constructor cannot return a value");
            } */
            if (ctx.expression() != null) v = visit(ctx.expression());
            throw new ReturnException(v);
        } else if (ctx.expression() != null) {
            result = visit(ctx.expression());
        }
        return result;
    }
    @Override
    public Value visitMethodCall(MiniJavaParser.MethodCallContext ctx) {
        String name = ctx.identifier().getText();
        List<Value> args = new ArrayList<>();
        if (ctx.arguments().expressionList() != null) {
            for (MiniJavaParser.ExpressionContext e : ctx.arguments().expressionList().expression()) {
                args.add(visit(e));
            }
        }
        if (!currentThis.isEmpty()) {
        ObjectInstance obj = currentThis.peek();
        List<String> argTypes = new ArrayList<>();
        for (Value v : args) {
            argTypes.add(valueTypeName(v));
        }
        MethodInfo method = findMethod(obj.clazz,name,argTypes);
        if (method != null) {
            return callMethod(obj,method,args);
        }
        }
        return callFunction(name, args);
    }
    private boolean toBool(Value v) {
        if (v.type != Type.BOOL) throw new RuntimeException("Condition expression must be boolean.");
        return v.asBool();
    }

    private Value handleIf(MiniJavaParser.StatementContext ctx) {
        boolean cond = toBool(visit(ctx.parExpression().expression()));
        if (cond) {
            Value result = visit(ctx.statement(0));
            if(result == Value.BREAK || result == Value.CONTINUE) return result;
            return result;
        }
        if (ctx.statement().size() > 1) {
            MiniJavaParser.StatementContext elseStmt = ctx.statement(1);
            if (elseStmt.IF() != null) return handleIf(elseStmt);
            Value result = visit(elseStmt);
            if(result == Value.BREAK || result == Value.CONTINUE) return result;
        }
        return null;
    }

    private Value handleWhile(MiniJavaParser.StatementContext ctx) {
        loopDepth++;
        try {
            while (true) {
                boolean cond = toBool(visit(ctx.parExpression().expression()));
                if (!cond) break;
                Value result = visit(ctx.statement(0));
                if (result == Value.BREAK) break;
                if (result == Value.CONTINUE) continue;
            }
        } finally {
            loopDepth--;
        }
        return null;
    }

    private Value handleFor(MiniJavaParser.StatementContext ctx) {   
        loopDepth++;
        MiniJavaParser.ForInitContext init = ctx.forControl().forInit();
        boolean newscope = false;
        
        if(init != null && init.localVariableDeclaration() != null) {
            newscope = true;
            scopes.push(new HashMap<>());
        } 
        
        try {
            if(init != null) {
                if(init.localVariableDeclaration() != null) {
                    visit(init.localVariableDeclaration());
                } else if (init.expressionList() != null){
                    for(MiniJavaParser.ExpressionContext exprCtx : init.expressionList().expression()) visit(exprCtx);
                }
            }
            while (true) {
                if(ctx.forControl().expression()!=null){
                    Value cond = visit(ctx.forControl().expression());
                    if (!toBool(cond)) break;
                }
                Value result = visit(ctx.statement(0));
                
                if(result == Value.BREAK) break;
                if (result == Value.CONTINUE) {
                    if (ctx.forControl().forUpdate != null) {
                        for (MiniJavaParser.ExpressionContext exprCtx : ctx.forControl().forUpdate.expression()) visit(exprCtx);
                    }
                    continue;
                }
                if(ctx.forControl().forUpdate != null){
                    for(MiniJavaParser.ExpressionContext exprCtx : ctx.forControl().forUpdate.expression()) visit(exprCtx);
                }
            }
        } finally {
            loopDepth--;
            if(newscope) scopes.pop();  
        }
        return null;
    }

    private Value handleNew(MiniJavaParser.CreatorContext ctx) {
        String baseType = ctx.createdName().getText();
        if (ctx.classCreatorRest() != null) {
        List<Value> args = new ArrayList<>();
        if (ctx.classCreatorRest().expressionList() != null) {
            for (MiniJavaParser.ExpressionContext e: ctx.classCreatorRest().expressionList().expression()) {
                args.add(visit(e));
            }
        }
        return createObject(baseType,args);
        }
        MiniJavaParser.ArrayCreatorRestContext rest = ctx.arrayCreatorRest();
        if (rest.arrayInitializer() != null) {
            Value arr = visit(rest.arrayInitializer());
            if (arr.arrayBaseType != null && arr.arrayBaseType.contains("unknown")) {
                arr.arrayBaseType = arr.arrayBaseType.replace("unknown", baseType);
            }
        String actualBaseType = arr.arrayBaseType.replace("[]", "");
        if (baseType.equals("int") && actualBaseType.equals("char")) {

            List<Value> converted = convertCharArrayToInt(arr.asArray());

             return Value.ofArray(converted, "int[]");
        }
        else if (baseType.equals("char") && actualBaseType.equals("int")) {
            List<Value> converted = convertIntArrayToChar(arr.asArray());
            return Value.ofArray(converted, "char[]");
        }
            if (!actualBaseType.equals(baseType)) {
                throw new RuntimeException("Array base type mismatch: expected " + baseType + ", but got " + actualBaseType);
            } 
        return arr;
        }
        List<MiniJavaParser.ExpressionContext> dims = rest.expression();
        int emptyDims = 0;
        for (int i = 0; i < rest.getChildCount(); i++) {
            if (rest.getChild(i).getText().equals("]") && rest.getChild(i-1).getText().equals("[")) emptyDims++;
        }
        int totaldims = dims.size() + emptyDims;
        return buildArray(baseType, dims, 0, totaldims);
    }

    private Value buildArray(String baseType, List<MiniJavaParser.ExpressionContext> dims, int idx, int totaldims) {
        int size = visit(dims.get(idx)).asInt();
        List<Value> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            if (idx == dims.size() - 1) { 
                if (dims.size() < totaldims) {
                    String elementBaseType = baseType;
                    for(int j=0; j < totaldims - idx - 1; j++) elementBaseType += "[]";
                    list.add(Value.ofNull(elementBaseType));
                } else {
                    list.add(defaultValue(baseType));
                }
            } else {
                list.add(buildArray(baseType, dims, idx + 1, totaldims));
            }
        }
        String arrayType = baseType;
        for (int i = 0; i < totaldims - idx; i++) arrayType += "[]";
        return Value.ofArray(list, arrayType);
    }

    private Value defaultValue(String type) {
        if (type.endsWith("[]")) {
        return Value.ofNull(type);
        }
        switch (type) {
            case "int": return Value.ofInt(0);
            case "char": return Value.ofChar((char)0);
            case "boolean": return Value.ofBool(false);
            case "string": return Value.ofString("");
            default: 
            if (classes.containsKey(type)) {
                Value val = Value.ofNull(type);
                val.declaredClass = classes.get(type);
                return val;
            } else {
                throw new RuntimeException("Unknown type: " + type);
            }
        }
    }

    private Value docast(String type, Value val) {
        if (type.endsWith("[]")) {
            if (val.type == Type.NULL) return val; 
            if (val.type != Type.ARRAY) throw new RuntimeException("Type mismatch: expected array");
            return val;
        }
        switch (type) {
            case "int":
                if (val.type == Type.INT) return val;
                else if (val.type == Type.CHAR) {
                    return Value.ofInt(val.asChar());
                } 
                else throw new RuntimeException("Invalid cast");
            case "char":
                 if (val.type == Type.CHAR) return val;
                 else if (val.type == Type.INT) {
                    if(val.asInt()<-127||val.asInt()>128) throw new RuntimeException("int value out of char range");
                    int x = val.asInt() & 0xFF;   
                        if (x >= 128) x -= 256;       
                        return Value.ofChar((char)x);
                }  
                else throw new RuntimeException("Invalid cast");
            case "boolean":
                if (val.type == Type.BOOL) return val;
                else throw new RuntimeException("Invalid cast");
            case "string":
                if (val.type == Type.STRING) return val;
                else throw new RuntimeException("Invalid cast");
            default:
                
            if (!classes.containsKey(type)) {
                throw new RuntimeException("Unknown type: " + type);
            }
            if (val.type == Type.NULL) {
                return Value.ofNull(type);
            }
            if (val.type != Type.OBJECT) {
                throw new RuntimeException("Invalid class cast");
            }
            ClassInfo targetClass = classes.get(type);
            ClassInfo declClass = val.declaredClass;
            ClassInfo realClass = val.asObject().clazz;
            boolean related = isAssignable(realClass, targetClass) || isAssignable(targetClass, realClass);
            if (!related) {
                throw new RuntimeException("Unrelated class cast");
            }
            if (!isAssignable(realClass, targetClass)) {
                throw new RuntimeException("Invalid downcast");
            }
            return Value.ofObject(val.asObject(),targetClass);
        }
    }   

    private Map<String, Value> findScope(String name){
        int base = functionBases.isEmpty() ? 0 : functionBases.peek();
        for(int i=scopes.size()-1;i>=base;i--){
            if(scopes.get(i).containsKey(name)) return scopes.get(i);
        }
        return null;
    }

    private boolean isAssignment(String op) {
        return op.equals("=") || op.equals("+=") || op.equals("-=") || op.equals("*=") || op.equals("/=") || op.equals("%=") || op.equals("&=") || op.equals("|=") || op.equals("^=") || op.equals("<<=") || op.equals(">>=") || op.equals(">>>=");
    }

    private Value applyAssign(String op, Value left, Value right){
        if (left.type == Type.NULL) {
        if (!op.equals("=")) {throw new RuntimeException("Invalid assignment operation: " + op);}
        if (right.type == Type.NULL) {
        return right;
        }
        if (left.declaredClass != null) {
        if (right.type != Type.OBJECT) {
            throw new RuntimeException("Type mismatch");
        }
        return Value.ofObject(right.asObject(),left.declaredClass);
        }
        if (left.arrayBaseType != null) {
        if (right.type != Type.ARRAY) {
            throw new RuntimeException("Type mismatch");
        }
        if (!left.arrayBaseType.equals(right.arrayBaseType)) {
            throw new RuntimeException("Array type mismatch");
        }
        return right;
        }
        throw new RuntimeException("Type mismatch");
        }
        if(left.type == Type.INT){
           if(right.type == Type.CHAR){
            if(right.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char to int"); 
            right = Value.ofInt(right.asChar());
          }
           if(right.type != Type.INT) throw new RuntimeException("Invalid assignment operation: " + op);
            int a = left.asInt(), b = right.asInt();
            switch(op){
                case "=": return Value.ofInt(b);
                case "+=": return Value.ofInt(a+b);
                case "-=": return Value.ofInt(a-b);
                case "*=": return Value.ofInt(a*b);
                case "/=": if(b == 0) throw new RuntimeException("Division by zero."); return Value.ofInt(a/b);
                case "%=": if(b == 0) throw new RuntimeException("Division by zero."); return Value.ofInt(a%b);
                case "&=": return Value.ofInt(a&b);
                case "|=": return Value.ofInt(a|b);
                case "^=": return Value.ofInt(a^b);
                case "<<=": return Value.ofInt(a << (b & 0x1F));
                case ">>=": return Value.ofInt(a >> (b & 0x1F));
                case ">>>=": return Value.ofInt(a >>> (b & 0x1F));
                default: throw new RuntimeException("Invalid assignment operation: " + op);
            }   
        } else if(left.type == Type.CHAR){
           if(right.type == Type.INT || right.type == Type.CHAR){
              if(right.type == Type.INT && right.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return int to char");
              left = Value.ofInt(left.asChar());
              right = Value.ofInt(right.type == Type.CHAR ? right.asChar() : right.asInt());
              int a = left.asInt(), b = right.asInt();
              Value result;
              switch(op){
                  case "=": result = Value.ofInt(b); break;
                  case "+=": result = Value.ofInt(a+b); break;
                  case "-=": result = Value.ofInt(a-b); break;
                  case "*=": result = Value.ofInt(a*b); break;
                  case "/=": if(b == 0) throw new RuntimeException("Division by zero."); result = Value.ofInt(a/b); break;
                  case "%=": if(b == 0) throw new RuntimeException("Division by zero."); result = Value.ofInt(a%b); break;
                  case "&=": result = Value.ofInt(a&b); break;
                  case "|=": result = Value.ofInt(a|b); break;
                  case "^=": result = Value.ofInt(a^b); break;
                  case "<<=": result = Value.ofInt(a << (b & 0x1F)); break;
                  case ">>=": result = Value.ofInt(a >> (b & 0x1F)); break;
                  case ">>>=": result = Value.ofInt(a >>> (b & 0x1F)); break;
                  default: throw new RuntimeException("Invalid assignment operation: " + op);
               }
               return Value.ofChar((char)result.asInt());
            } else throw new RuntimeException("Invalid assignment operation: " + op);
        } else if(left.type == Type.BOOL){
            if(right.type != Type.BOOL) throw new RuntimeException("Invalid assignment operation: " + op);
            boolean b = right.asBool();
            if (op.equals("=")) return Value.ofBool(b);
            throw new RuntimeException("Invalid assignment operation: " + op);
        } else if(left.type == Type.STRING){
            if(right.type != Type.STRING && (right.type == Type.INT || right.type == Type.BOOL || right.type == Type.CHAR)){
                right = Value.ofString(formatValue(right));
            }
            String a = left.asString(), b = right.asString();
            if (op.equals("=")) return Value.ofString(b);
            if (op.equals("+=")) return Value.ofString(a + b);
            throw new RuntimeException("Invalid assignment operation: " + op);
        }else if(left.type == Type.OBJECT){
            if (!op.equals("=")) {
            throw new RuntimeException("Invalid assignment operation: " + op);
            }
            if (right.type == Type.NULL) {
            return right;
            }
            if (right.type != Type.OBJECT) {
            throw new RuntimeException("Type mismatch");
            }
            return right;
        }else if(left.type == Type.ARRAY){
        if (!op.equals("=")) {
        throw new RuntimeException("Invalid assignment operation: " + op);
        }
        if (right.type == Type.NULL) {
        return right;
        }
        if (right.type != Type.ARRAY||!left.arrayBaseType.equals(right.arrayBaseType)) {
        throw new RuntimeException("Type mismatch");
        }
        return right;
        }
        else {
        throw new RuntimeException("Invalid assignment operation: " + op);
        }
    }

    private String valueToString(Value val) {
        switch (val.type) {
            case INT: return Integer.toString(val.asInt());
            case BOOL: return Boolean.toString(val.asBool());
            case CHAR: return Character.toString(val.asChar());
            case STRING: return val.asString();
            default: throw new RuntimeException("ToString failed");
        }
    }

    boolean isPrimitive(String type) {
    return type.equals("int") ||
           type.equals("char") ||
           type.equals("boolean");
    }

    private Value callFunction(String name, List<Value> args) {
        if (isBuiltin(name)) return callBuiltin(name, args);
        if(name.equals("main")) throw new RuntimeException("double main");

        MiniJavaParser.MethodDeclarationContext method = resolveOverload(name, args);
        String returnType = method.VOID() != null ? "void" : method.typeType().getText();
        
        functionBases.push(scopes.size());

        Map<String, Value> newScope = new HashMap<>();
        scopes.push(newScope);

        MiniJavaParser.FormalParametersContext params = method.formalParameters();
        if (params.formalParameterList() != null) {
            List<MiniJavaParser.FormalParameterContext> paramList = params.formalParameterList().formalParameter();
            if (paramList.size() != args.size()) throw new RuntimeException("Argument count mismatch");
            
            for (int i = 0; i < paramList.size(); i++) {
                String paramName = paramList.get(i).identifier().getText();
                String paramType = paramList.get(i).typeType().getText();
                Value arg = args.get(i);
                if (classes.containsKey(paramType)) {
                if (arg.type == Type.NULL) {
                newScope.put(paramName, arg);
                }
                else if (arg.type == Type.OBJECT) {
                ClassInfo expected = classes.get(paramType);
                if (!isAssignable(arg.declaredClass, expected)) {
                throw new RuntimeException("Type mismatch");
                }
                arg = Value.ofObject(arg.asObject(), expected);
                newScope.put(paramName, arg);
                }
                else {
                throw new RuntimeException("Expected object");
                }
                }
                else if (paramType.endsWith("[]")) {
                    if (arg.type == Type.NULL) newScope.put(paramName, arg);
                    else if (arg.type == Type.ARRAY) {
                        checkArrayType(paramType, arg); 
                        newScope.put(paramName, arg);
                    } else throw new RuntimeException("Expected array");
                } else {
                    Value argVal = docast(paramType, args.get(i));
                    newScope.put(paramName, argVal);
                }
            }
        } else {
            if (args.size() != 0) throw new RuntimeException("Argument count mismatch");
        }
    
        int oldLoopDepth = loopDepth;
        loopDepth = 0;
        Value returnValue = null;
        boolean hasreturn = false;
        
        try {
            visit(method.block());
        } catch (ReturnException r) {
            hasreturn = true;
            returnValue = r.value;
        } finally {
            scopes.pop();
            functionBases.pop();
            loopDepth = oldLoopDepth;
        }

        if(returnType.equals("void")){
            if(hasreturn && returnValue != null) throw new RuntimeException("Void function should not return a value.");
            return null;
        }
        if(!hasreturn) throw new RuntimeException("Non-void function must return a value.");   
        if (returnValue == null) throw new RuntimeException("Return value missing.");
        
        if (returnValue.type == Type.NULL) {
            if (!returnType.endsWith("[]")) throw new RuntimeException("Invalid return type: null");
            return returnValue;
        }
        
        if (returnType.endsWith("[]")) {
    if (returnValue.type != Type.ARRAY && returnValue.type != Type.NULL)
        throw new RuntimeException("Invalid return type: expected array");
    if (returnValue.type == Type.ARRAY && !returnValue.arrayBaseType.equals(returnType))
        throw new RuntimeException("Invalid return type: expected " + returnType + " but got " + returnValue.arrayBaseType);
        }

        if(returnType.endsWith("[]")){
            if(returnType.equals("int[]")&& !returnValue.arrayBaseType.equals("int[]")){
                throw new RuntimeException("Invalid return type: expected int array");
            }
                if(returnType.equals("char[]")&& !returnValue.arrayBaseType.equals("char[]")){
                    throw new RuntimeException("Invalid return type: expected char array");
                }
                if(returnType.equals("boolean[]")&& !returnValue.arrayBaseType.equals("boolean[]")){
                    throw new RuntimeException("Invalid return type: expected boolean array");
                }
                if(returnType.equals("string[]")&& !returnValue.arrayBaseType.equals("string[]")){
                    throw new RuntimeException("Invalid return type: expected string array");
                }
            if(returnValue.type!=Type.ARRAY && returnValue.type!=Type.NULL) throw new RuntimeException("Invalid return type: expected array");
        } else {
            if (returnValue.type == Type.INT && returnType.equals("char")) {
    if (returnValue.isLiteral) {
        int x = returnValue.asInt();
        if (x < -128 || x > 127) throw new RuntimeException("Literal out of char range");
        int y = x & 0xFF;
        if (y >= 128) y -= 256;
        returnValue = Value.ofChar((char)y);
    } else {
        throw new RuntimeException("wrong type in return");
    }
} else if (returnValue.type == Type.INT && !returnType.equals("int")) {
    throw new RuntimeException("wrong type in return");
}else if (returnValue.type == Type.CHAR && returnType.equals("int")) {
    char x= returnValue.asChar();
    returnValue = Value.ofInt((int)x);
}else {
    // 其他类型检查
    if (returnValue.type == Type.CHAR && !returnType.equals("char") && !returnType.equals("int")) {
        throw new RuntimeException("wrong type in return");
    } else if (returnValue.type == Type.BOOL && !returnType.equals("boolean")) {
        throw new RuntimeException("wrong type in return");
    } else if (returnValue.type == Type.STRING && !returnType.equals("string")) {
        throw new RuntimeException("wrong type in return");
    }
}
        } 
        returnValue.isfuncReturnValue = true;
        return returnValue;
    }

    private Value callBuiltin(String name, List<Value> args) {
        switch (name) {
            case "print": return builtinPrint(args);
            case "println": return builtinPrintln(args);
            case "assert": return builtinAssert(args);
            case "length": return builtinLength(args);
            case "to_char_array": return builtinToCharArray(args);
            case "to_string": return builtinToString(args);
            case "atoi": return builtinAtoi(args);
            case "itoa": return builtinItoa(args);
            default: return null;
        }
    }

    private Value callFunctionDirect(MiniJavaParser.MethodDeclarationContext method, List<Value> args) {
        Map<String, Value> newScope = new HashMap<>();
        functionBases.push(scopes.size());
        scopes.push(newScope);
        MiniJavaParser.FormalParametersContext params = method.formalParameters();
        if (params.formalParameterList() != null && args.size() > 0) {
            String paramName = params.formalParameterList().formalParameter(0).identifier().getText();
            newScope.put(paramName, args.get(0));
        }

        int oldLoopDepth = loopDepth;
        loopDepth = 0;
        Value returnValue = null;

        try {
            visit(method.block());
        } catch (ReturnException r) {
            returnValue = r.value;
        } finally {
            loopDepth = oldLoopDepth;
            scopes.pop();
            functionBases.pop();
        }

        if (returnValue == null || returnValue.type != Type.INT) {
            throw new RuntimeException("main must return integer");
        }
        runresult.result  += "Process exits with " + returnValue.asInt() + "." + "\n";
        return returnValue;
    }

    private Value checkArrayType(String type, Value arrVal) {
        if (arrVal.type == Type.NULL) return arrVal;
        if (arrVal.type != Type.ARRAY) throw new RuntimeException("should be array");

        String baseType = type.substring(0, type.length()-2);
        List<Value> list = arrVal.asArray();

        for (int i = 0; i < list.size(); i++) {
            Value v = list.get(i);
            if (v.type == Type.NULL){
                if(!baseType.endsWith("[]")){
                    if (baseType.equals("int") || baseType.equals("char") || baseType.equals("boolean")) throw new RuntimeException("type error");
                }
                continue;
            }

            if (v.type == Type.ARRAY) {
                String elementType = type.substring(0, type.length() - 2); 
                checkArrayType(elementType, v);
                continue;
            } 

            Value newV = v;
            switch (baseType) {
                case "int":
                    if (v.type == Type.CHAR) {
                        if (v.isfuncReturnValue) throw new RuntimeException("not match array type:int");
                        newV = Value.ofInt(v.asChar());
                    } else if (v.type != Type.INT) throw new RuntimeException("not match array type:int");
                    break;
                case "char":
                    if (v.type == Type.INT) {
                        if (v.isfuncReturnValue) throw new RuntimeException("not match array type:char");
                        int x = v.asInt();
                        if (x < -128 || x > 127) throw new RuntimeException("not match array type:char");
                        newV = Value.ofChar((char)x);
                    } else if (v.type != Type.CHAR) throw new RuntimeException("not match array type:char");
                    break;
                case "boolean":
                    if (v.type != Type.BOOL) throw new RuntimeException("not match array type:bool");
                    break;
                case "string":
                    if (v.type != Type.STRING) throw new RuntimeException("not match array type:string");
                    break;
            }
            list.set(i, newV);
        }
        arrVal.arrayBaseType = type;
        return arrVal;
    }

    private String inferArrayBaseType(List<Value> list) {
        String base = null;
        for (Value v : list) {
            //System.out.println("Inferring type for array element: " + v.type + (v.type == Type.ARRAY ? (" with base " + v.arrayBaseType) : ""));
            if (v.type == Type.NULL) continue;
            String t = (v.type == Type.ARRAY) ? v.arrayBaseType : valueTypeToString(v.type);
            //System.out.println("Inferred type for this element: " + t);
            if (base == null || base.contains("unknown")){ 
                base = t;
                if(t.equals("char")){
                    if(v.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char to int in array initializer");
                    if(v.type != Type.ARRAY){
                        Value newV = Value.ofInt((int)v.asChar());
                        list.set(list.indexOf(v), newV);
                    }
                    base = "int";
                }
                if(t.equals("char[]")){
                    if(v.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char array to int array in array initializer");
                    List<Value> converted = convertCharArrayToInt(v.asArray());
                    list.set(list.indexOf(v), Value.ofArray(converted, "int[]"));
                    base = "int[]";
                }
                continue;
            }
            if(base.equals(t)){
                continue;
            }
            // char + int => int
             if ((base.equals("int") && t.equals("char"))) {
                 if(v.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char to int in array initializer");
                 if(v.type != Type.ARRAY){
                    Value newV = Value.ofInt((int)v.asChar());
                    list.set(list.indexOf(v), newV);
                 }
                 base = "int";
                 continue;
            }

            // int[] + char[] => int[]
            if ((base.equals("int[]") && t.equals("char[]"))) {
                    if(v.isfuncReturnValue) throw new RuntimeException("Cannot implicitly convert func return char array to int array in array initializer");
                    List<Value> converted = convertCharArrayToInt(v.asArray());
                    list.set(list.indexOf(v), Value.ofArray(converted, "int[]"));
                    base = "int[]";
                    continue;
            }
            //遇到char int 全统一为int，其他不兼容类型直接报错
            throw new RuntimeException("Inconsistent array element types");
        }
        return base == null ? "unknown" : base;
    }

    private String valueTypeToString(Type t) {
        switch (t) {
            case INT: return "int";
            case CHAR: return "char";
            case BOOL: return "boolean";
            case STRING: return "string";
            default: throw new RuntimeException("Unknown type");
        }
    }

    private MiniJavaParser.MethodDeclarationContext resolveOverload(String name, List<Value> args) {
        List<MiniJavaParser.MethodDeclarationContext> candidates = methods.get(name);
        if (candidates == null) throw new RuntimeException("Function not found: " + name);

        MiniJavaParser.MethodDeclarationContext best = null;
        int bestCost = Integer.MAX_VALUE;
        boolean ambiguous = false;

        for (MiniJavaParser.MethodDeclarationContext m : candidates) {
            MiniJavaParser.FormalParametersContext params = m.formalParameters();
            List<MiniJavaParser.FormalParameterContext> paramList = (params.formalParameterList() == null) ? new ArrayList<>() : params.formalParameterList().formalParameter();
            if (paramList.size() != args.size()) continue;

            int cost = 0;
            boolean ok = true;

            for (int i = 0; i < args.size(); i++) {
                String paramType = paramList.get(i).typeType().getText();
                Value arg = args.get(i);
                int c = conversionCost(paramType, arg);
                if (c == -1) { ok = false; break; }
                cost += c;
            }
            if (!ok) continue;
            if (cost < bestCost) {
                best = m;
                bestCost = cost;
                ambiguous = false;
            } else if (cost == bestCost) {
                ambiguous = true;
            }
        }

        if (best == null) throw new RuntimeException("No matching function: " + name);
        if (ambiguous) throw new RuntimeException("Ambiguous call: " + name);
        return best;
    }

    private int conversionCost(String paramType, Value arg) {
        if (classes.containsKey(paramType)) {
        if (arg.type == Type.NULL) {
            return 1;
        }
        if (arg.type != Type.OBJECT) {
            return -1;
        }
        ClassInfo expected = classes.get(paramType);
        ClassInfo actual = arg.declaredClass;
        if (actual == null) {
            return -1;
        }
        if (!isAssignable(actual, expected)) {
            return -1;
        }
        if (actual == expected) {
            return 0;
        }
        return 1;
        }
        if (arg.type == Type.NULL) return paramType.endsWith("[]") ? 1: -1;//////////////
        if (paramType.endsWith("[]")) {
             if (arg.type == Type.NULL)
                return 1;

            if (arg.type != Type.ARRAY)
                return -1;

            return paramType.equals(arg.arrayBaseType) ? 0 : -1;
        }
        switch (paramType) {
            case "int":  return arg.type == Type.INT ? 0 : (arg.type == Type.CHAR ? 1 : -1);
            case "char": return arg.type == Type.CHAR ? 0 : -1;
            case "boolean": return arg.type == Type.BOOL ? 0 : -1;
            case "string": return arg.type == Type.STRING ? 0 : -1;
            default: return -1;
        }
    }

    private MiniJavaParser.MethodDeclarationContext findMain() {
        List<MiniJavaParser.MethodDeclarationContext> list = methods.get("main");
        if (list == null || list.isEmpty()) throw new RuntimeException("No main function found");

        MiniJavaParser.MethodDeclarationContext targetMain = null;
        boolean hasVoidMain = false;
        for (MiniJavaParser.MethodDeclarationContext m : list) {
            boolean noParams = (m.formalParameters().formalParameterList() == null);
            boolean hasStringArrayParam = false;
            if (!noParams && m.formalParameters().formalParameterList().formalParameter().size() == 1) {
                String typeStr = m.formalParameters().formalParameterList().formalParameter(0).typeType().getText();
                if (typeStr.equals("string[]") || typeStr.equals("String[]")) hasStringArrayParam = true;
            }

            if (!noParams && !hasStringArrayParam) continue;
            
            if (m.typeType() != null && m.typeType().getText().equals("int")) {
                if (targetMain != null) throw new RuntimeException("Multiple main() found");
                targetMain = m;
            } else if (m.VOID() != null) {
            hasVoidMain = true;
            }
        }
         if (targetMain != null && hasVoidMain) {
        throw new RuntimeException("Ambiguous main() call: both void and int main exist");
        }
        if (targetMain == null) throw new RuntimeException("No valid int main() found");
        return targetMain;
    }

    private Value builtinPrint(List<Value> args) {
        if (args.size() != 1) throw new RuntimeException("print expects 1 argument");
        System.out.print(formatValue(args.get(0)));
        runresult.result += formatValue(args.get(0));
        return null;
    }

    private Value builtinPrintln(List<Value> args) {
        if (args.size() == 0) {
            System.out.println();
            runresult.result += "\n";
            return null;
        }
        if (args.size() != 1) throw new RuntimeException("println expects 0 or 1 argument");
        System.out.println(formatValue(args.get(0)));
        runresult.result += formatValue(args.get(0)) +"\n";
        return null;
    }

    private String formatValue(Value v) {
        if (v.type == Type.NULL) return "null";
        if (v.type == Type.ARRAY) return formatArray(v);
        if (v.type == Type.OBJECT) return formatObject(v);
        return valueToString(v);
    }

    private String formatArray(Value arr) {
        if (arr == null || arr.type == Type.NULL) return "null";
        List<Value> list = arr.asArray();
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatValue(list.get(i)));
        }
        sb.append("]");
        return sb.toString(); 
    }
    private String formatObject(Value v) {
    if (v.type == Type.NULL) {
        return "null";
    }
    ObjectInstance obj = v.asObject();
    ClassInfo declClass = v.declaredClass;
    ClassInfo realClass = obj.clazz;
    MethodInfo declMethod = resolveMethodByDeclType(declClass,"to_string",new ArrayList<>());
    if (declMethod == null) {
        return realClass.name;
    }
    if (!declMethod.returnType.equals("string")) {
        return realClass.name;
    }
    MethodInfo runtimeMethod = resolveMethodByRuntimeType(obj,declMethod);
    Value ret = callMethod(obj,runtimeMethod,new ArrayList<>());
    if (ret == null || ret.type != Type.STRING) {
        throw new RuntimeException(
            "to_string must return string"
        );
    }
    return ret.asString();
    }
    private Value builtinAssert(List<Value> args) {
        if (args.size() != 1 || args.get(0).type != Type.BOOL) throw new RuntimeException("assert expects boolean");
        if (!args.get(0).asBool()) {
            runresult.result += "Process exits with 33."+"\n";
            //System.exit(33);
        }
        return null;
    }

    private Value builtinLength(List<Value> args) {
        if (args.size() != 1) throw new RuntimeException("length expects 1 argument");
        Value v = args.get(0);
        if (v.type == Type.NULL) throw new RuntimeException("Null pointer");
        if (v.type == Type.ARRAY) return Value.ofInt(v.asArray().size());
        if (v.type == Type.STRING) return Value.ofInt(v.asString().length());
        throw new RuntimeException("length expects array or string");
    }

    private Value builtinToCharArray(List<Value> args) {
        if (args.size() != 1 || args.get(0).type != Type.STRING) throw new RuntimeException("to_char_array expects string");
        String s = args.get(0).asString();
        List<Value> list = new ArrayList<>();
        for (char c : s.toCharArray()) list.add(Value.ofChar(c));
        return Value.ofArray(list,"char[]");
    }

    private Value builtinToString(List<Value> args) {
        if (args.size() != 1) throw new RuntimeException("to_string expects 1 argument");
        Value v = args.get(0);
        if (v.type == Type.NULL) throw new RuntimeException("Null pointer");
        if (v.type != Type.ARRAY || !"char[]".equals(v.arrayBaseType)) throw new RuntimeException("to_string expects char[]");
        StringBuilder sb = new StringBuilder();
        for (Value c : v.asArray()) sb.append(c.asChar());
        return Value.ofString(sb.toString());
    }

    private Value builtinAtoi(List<Value> args) {
        if (args.size() != 1 || (args.get(0).type != Type.STRING)) throw new RuntimeException("atoi expects string");
        if(args.get(0).asString().equals("")){
            return Value.ofInt(0);
        }
        try{
            return Value.ofInt(Integer.parseInt(args.get(0).asString().trim()));
        } catch(NumberFormatException e){
            throw new RuntimeException("Invalid integer format");
        }
    } 
    private Value builtinItoa(List<Value> args) {
        if (args.size() != 1) throw new RuntimeException("itoa expects 1 argument");
        Value v = args.get(0);
        
        if (v.type == Type.CHAR) {
            return Value.ofString(Integer.toString((byte)v.asChar()));
        }
        if (v.type == Type.INT) {
            return Value.ofString(Integer.toString(v.asInt()));
        }
        throw new RuntimeException("itoa expects int");
    }
    private boolean isBuiltin(String name) {
        return name.equals("print") || name.equals("println") || name.equals("assert") || name.equals("length") || name.equals("to_char_array") || name.equals("to_string") || name.equals("atoi") || name.equals("itoa");
    }
    private boolean isReferenceType(String type) {
    if (type.endsWith("[]")) {
        return true;
    }
    if (classes.containsKey(type)) {
        return true;
    }
    return false;
    }
    private boolean isReferenceValue(Value v) {
    return v.type == Type.OBJECT||v.type == Type.ARRAY||v.type == Type.NULL;
}
    private Value evalArrayWithType(MiniJavaParser.ArrayInitializerContext ctx, String type) {
        List<Value> list = new ArrayList<>();
        String baseType = type.replace("[]", "");
        String baseType1 = type.substring(0, type.length() - 2);
        for (MiniJavaParser.VariableInitializerContext vctx : ctx.variableInitializer()) {
        Value v;
        if (vctx.arrayInitializer() != null) {
            v = evalArrayWithType(vctx.arrayInitializer(), baseType + "[]");
        } else {
            v = visit(vctx.expression());
            /* if (v.type != Type.NULL) {
                v = checkSingleValue1(baseType, v); 
            } */
           if (v.type == Type.NULL) {
                if (!baseType1.endsWith("[]")) {
                    throw new RuntimeException("Type error: null cannot be assigned to primitive " + baseType);
                }
            } else if (baseType1.endsWith("[]")) {
                if(v.type != Type.ARRAY) {
                    throw new RuntimeException("Type error: expected array but got " + v.type);
                }
                if (!v.arrayBaseType.equals(baseType1)) {
                    throw new RuntimeException("Type error: expected " + baseType1 + " but got " + v.arrayBaseType);
                }
            } else {
                v = checkSingleValue1(baseType, v); 
            }
        }
        list.add(v); 
        }
        return checkArrayType(type, Value.ofArray(list, type));
    }
    private Value checkSingleValue1(String baseType, Value v) {
    if (v.type == Type.NULL) return v;
    switch (baseType) {
        case "int":
            if (v.type == Type.INT) return v;
            if (v.type == Type.CHAR) {
                if (v.isfuncReturnValue) throw new RuntimeException("type error");
                return Value.ofInt(v.asChar());
            }
            throw new RuntimeException("type error");
        case "char":
            if (v.type == Type.CHAR) return v;
            if (v.type == Type.INT) {
                if (v.isfuncReturnValue) throw new RuntimeException("type error");
                int x = v.asInt();
                if (x < -128 || x > 127) throw new RuntimeException("Literal out of char range");
                int y = x & 0xFF;
                if (y >= 128) y -= 256;
                return Value.ofChar((char)y);
            }
            throw new RuntimeException("type error");
        case "boolean":
            if (v.type != Type.BOOL) throw new RuntimeException("type error");
            return v;
        case "string":
            if (v.type != Type.STRING) throw new RuntimeException("type error");
            return v;
        default: throw new RuntimeException("Unknown base type");
    }
    }
    private Value fixUnknownArray(Value arr, String type) {
        if (arr.type == Type.NULL) return arr;
        if (arr.type != Type.ARRAY) throw new RuntimeException("Type mismatch");
        List<Value> list = arr.asArray();
        String baseType = type.replace("[]", "");
        for (int i = 0; i < list.size(); i++) {
            Value v = list.get(i);
            if (v == null || v.type == Type.NULL) continue;
            if (v.type == Type.ARRAY) list.set(i, fixUnknownArray(v, baseType + "[]"));
        }
        arr.arrayBaseType = type;
        return arr;
    }
    private List<Value> convertCharArrayToInt(List<Value> list){
    List<Value> result = new ArrayList<>();
    for(Value v : list){
        if(v.type == Type.ARRAY){
            List<Value> sub = convertCharArrayToInt(v.asArray());
            String newType =
                v.arrayBaseType.replace("char", "int");
            result.add(Value.ofArray(sub, newType));
            continue;
        }
        if(v.type == Type.NULL){
            result.add(v);
            continue;
        }
        if(v.type == Type.CHAR){
            result.add(Value.ofInt(v.asChar()));
            continue;
        }
        if(v.type == Type.INT){
            result.add(v);
            continue;
        }
        throw new RuntimeException("Type mismatch");
    }
    return result;
   }
    private List<Value> convertIntArrayToChar(List<Value> list){
    List<Value> result = new ArrayList<>();
    for(Value v : list){
        if(v.type == Type.ARRAY){
            List<Value> sub = convertIntArrayToChar(v.asArray());
            String newType =
                v.arrayBaseType.replace("int", "char");
            result.add(Value.ofArray(sub, newType));
            continue;
        }
        if(v.type == Type.NULL){
            result.add(v);
            continue;
        }
        if(v.type == Type.INT){
            result.add(Value.ofChar((char)v.asInt()));
            continue;
        }
        if(v.type == Type.CHAR){
            result.add(v);
            continue;
        }
        throw new RuntimeException("Type mismatch");
    }
    return result;
   }
    private boolean hasSameSignature(MiniJavaParser.MethodDeclarationContext m1, MiniJavaParser.MethodDeclarationContext m2) {
    MiniJavaParser.FormalParametersContext p1 = m1.formalParameters();
    MiniJavaParser.FormalParametersContext p2 = m2.formalParameters();
    
    List<MiniJavaParser.FormalParameterContext> params1 = (p1.formalParameterList() == null) ? new ArrayList<>() : p1.formalParameterList().formalParameter();
    List<MiniJavaParser.FormalParameterContext> params2 = (p2.formalParameterList() == null) ? new ArrayList<>() : p2.formalParameterList().formalParameter();
    
    if (params1.size() != params2.size()) return false;
    
    for (int i = 0; i < params1.size(); i++) {
        if (!params1.get(i).typeType().getText().equals(params2.get(i).typeType().getText())) {
            return false;
        }
    }
    return true;
}
    private void registerField(ClassInfo clazz,MiniJavaParser.FieldDeclarationContext ctx) {
    String typeName = ctx.typeType().getText();
    boolean isArray = typeName.endsWith("[]");
    MiniJavaParser.VariableDeclaratorContext varDecl = ctx.variableDeclarator();
    String fieldName = varDecl.identifier().getText();
    if (clazz.fields.containsKey(fieldName)) {
        throw new RuntimeException("Duplicate field: " + fieldName);
    }
    MiniJavaParser.ExpressionContext init = null;
    if (varDecl.variableInitializer() != null&&varDecl.variableInitializer().expression()!= null) {
        init =varDecl.variableInitializer().expression();
    }
    FieldInfo field = new FieldInfo(fieldName,typeName,isArray,init,clazz);
    clazz.fields.put(fieldName, field);
  }
    private void registerMethod(ClassInfo clazz,MiniJavaParser.MethodDeclarationContext ctx) {
    MethodInfo method = new MethodInfo();
    method.name =ctx.identifier().getText();
    method.ownerClass = clazz;
    method.ctx = ctx;
    if (ctx.typeType() != null) {
        method.returnType = ctx.typeType().getText();
    } else {
        method.returnType = "void";
    }
    method.paramTypes = new ArrayList<>();
    method.paramNames = new ArrayList<>();
    if (ctx.formalParameters().formalParameterList() != null) {
        for (MiniJavaParser.FormalParameterContext p: ctx.formalParameters().formalParameterList().formalParameter()) {
            method.paramTypes.add(p.typeType().getText());
            method.paramNames.add(p.identifier().getText());
        }
    }
    List<MethodInfo> overloads =clazz.methods.computeIfAbsent(method.name,k -> new ArrayList<>());
    for (MethodInfo existing : overloads) {
        if (sameSignature(existing, method)) {
            throw new RuntimeException("Duplicate method signature: "+ method.name
            );
        }
    }
    overloads.add(method);
    }
    private void registerConstructor(ClassInfo clazz,MiniJavaParser.ConstructorDeclarationContext ctx) {
    ConstructorInfo ctor = new ConstructorInfo();
    ctor.ownerClass = clazz;
    ctor.ctx = ctx;
    if (ctx.formalParameters().formalParameterList() != null) {
        for (MiniJavaParser.FormalParameterContext p: ctx.formalParameters().formalParameterList().formalParameter()) {
            ctor.paramTypes.add(
                p.typeType().getText()
            );
            ctor.paramNames.add(
                p.identifier().getText()
            );
        }
    }
    for (ConstructorInfo existing: clazz.constructors) {
        if (sameConstructorSignature(existing,ctor)) {
            throw new RuntimeException("Duplicate constructor");
        }
    }
    clazz.constructors.add(ctor);
    }
    private boolean sameSignature(MethodInfo a,MethodInfo b) {
    if (!a.name.equals(b.name)) {
        return false;
    }
    if (a.paramTypes.size()!= b.paramTypes.size()) {
        return false;
    }
    for (int i = 0;i < a.paramTypes.size();i++) {
        if (!a.paramTypes.get(i)
                .equals(b.paramTypes.get(i))) {

            return false;
        }
    }
    return true;
    }
    MethodInfo findMethod(ClassInfo clazz,String name,List<String> argTypes) {
    List<MethodInfo> overloads = clazz.methods.get(name);
    if (overloads != null) {
        for (MethodInfo m : overloads) {
            if (matchArgs(m, argTypes)) {
                return m;
            }
        }
    }
    if (clazz.parent != null) {
        return findMethod(clazz.parent,name,argTypes);
    }
    return null;
    } 
    private FieldInfo findField(ClassInfo clazz,String name) {
    ClassInfo cur = clazz;
    while (cur != null) {
        if (cur.fields.containsKey(name)) {
            return cur.fields.get(name);
        }
        cur = cur.parent;
    }
    return null;
    }
    private FieldInfo findSuperField(ClassInfo clazz,String name) {
    ClassInfo cur = clazz.parent;
    while (cur != null) {
        if (cur.fields.containsKey(name)) {
            return cur.fields.get(name);
        }
        cur = cur.parent;
    }
    return null;
    }
    private ConstructorInfo findConstructor(ClassInfo clazz, List<String> argTypes) {
    for (ConstructorInfo ctor : clazz.constructors) {
        if (ctor.paramTypes.size() != argTypes.size()) {
            continue;
        }
        boolean ok = true;
        for (int i = 0; i < argTypes.size(); i++) {
            String expected = ctor.paramTypes.get(i);
            String actual = argTypes.get(i);
            if (expected.equals(actual)) {
                continue;
            }
            if (actual.equals("null")) {
                if (expected.equals("int")|| expected.equals("char")|| expected.equals("boolean")) {
                    ok = false;
                }
                continue;
            }
            ClassInfo actualClass = classes.get(actual);
            ClassInfo expectedClass = classes.get(expected);
            if (actualClass != null && expectedClass != null && isAssignable(actualClass, expectedClass)) {
                continue;
            }
            ok = false;
            break;
        }
        if (ok) {
            return ctor;
        }
    }
    return null;
    }
    private void callConstructor(ObjectInstance obj,ConstructorInfo ctor,List<Value> args,boolean delegated) {
    if (constructorStack.contains(ctor)) {
        throw new RuntimeException(
            "Recursive constructor invocation"
        );
    }
    constructorStack.push(ctor);
    currentThis.push(obj);
    functionBases.push(scopes.size());
    scopes.push(new HashMap<>());
    try{
    for (int i = 0;i < ctor.paramNames.size();i++) {
        scopes.peek().put(ctor.paramNames.get(i),args.get(i));
    }
    if (ctor.ctx == null) {
       if (!delegated) {
           if (ctor.ownerClass.parent != null) {
                ConstructorInfo superCtor =findConstructor(ctor.ownerClass.parent,new ArrayList<>());
                    if (superCtor == null) {
                        throw new RuntimeException(
                            "Default super constructor not found"
                        );
                    }
                    callConstructor(obj,superCtor,new ArrayList<>(),false
                    );
                }
                initializeOwnFields(obj,ctor.ownerClass);
            }
            return;
    }
    try {
        List<MiniJavaParser.BlockStatementContext> stmts = ctor.ctx.constructorBody.blockStatement();
        for (int i = 0; i < stmts.size(); i++) {
        MiniJavaParser.BlockStatementContext stmt = stmts.get(i);
        if (stmt.statement() == null) {continue;}
        if (stmt.statement().expression() == null) {continue;}
        MiniJavaParser.ExpressionContext expr = stmt.statement().expression();
        if (expr.methodCall() == null) {continue;}
        boolean isThisCall = expr.methodCall().THIS() != null;
        boolean isSuperCall = expr.methodCall().SUPER() != null;
        if ((isThisCall || isSuperCall)&& i != 0) {
        throw new RuntimeException(
            "Constructor invocation must be first statement"
        );
        }
        }
        int startIndex = 0;
        boolean delegatedToThis = false;
        if (!stmts.isEmpty()) {
            MiniJavaParser.BlockStatementContext first = stmts.get(0);
            if (first.statement() != null&&first.statement().expression()!= null) {
                MiniJavaParser.ExpressionContext expr = first.statement().expression();
                if (expr.methodCall() != null&&expr.methodCall().THIS()!= null) {
                    delegatedToThis = true;
                    List<Value> callArgs = evalArguments(expr.methodCall().arguments());
                    List<String> types = new ArrayList<>();
                    for (Value v : callArgs) {
                        types.add(valueTypeName(v));
                    }
                    ConstructorInfo target = findConstructor(ctor.ownerClass,types);
                    if (target == null) {
                        throw new RuntimeException("Constructor not found");
                    }
                    callConstructor(obj,target,callArgs,true);
                    startIndex = 1;
                }
                else if (expr.methodCall() != null&&expr.methodCall().SUPER()!= null) {
                    if (ctor.ownerClass.parent == null) {
                        throw new RuntimeException("No superclass");
                    }
                    List<Value> callArgs = evalArguments(expr.methodCall().arguments());
                    List<String> types = new ArrayList<>();
                    for (Value v : callArgs) {
                        types.add(valueTypeName(v));
                    }
                    ConstructorInfo target = findConstructor(ctor.ownerClass.parent,types);
                    if (target == null) {
                        throw new RuntimeException(
                            "Super constructor not found"
                        );
                    }
                    callConstructor(obj,target,callArgs,false);
                    startIndex = 1;
                }
            }
        }
        if (!delegatedToThis&&startIndex == 0&&ctor.ownerClass.parent != null) {
            ConstructorInfo superCtor = findConstructor(ctor.ownerClass.parent,new ArrayList<>());
            if (superCtor == null) {
                throw new RuntimeException(
                    "Default super constructor not found"
                );
            }
            callConstructor(obj,superCtor,new ArrayList<>(),false);
        }
        if (!delegatedToThis) {
            initializeOwnFields(obj,ctor.ownerClass);
        }
        for (int i = startIndex; i < stmts.size();i++) {
            visit(stmts.get(i));
        }
    }catch (ReturnException r) {
        if (r.value != null) {
            throw new RuntimeException(
                "Constructor cannot return a value"
            );
        }
    } 
    /* finally {
        scopes.pop();
        functionBases.pop();
        currentThis.pop();
    } */
    }finally{
        constructorStack.pop();
        scopes.pop();
        functionBases.pop();
        currentThis.pop();
    }
    }
    private List<Value> evalArguments(MiniJavaParser.ArgumentsContext ctx) {
    List<Value> args = new ArrayList<>();
    if (ctx.expressionList() != null) {
        for (MiniJavaParser.ExpressionContext e: ctx.expressionList().expression()) {
            args.add(visit(e));
        }
    }
    return args;
    }
    private Value invokeMethod(Value objVal,String methodName,List<Value> args) {
    if (objVal == null) {
        throw new RuntimeException("null pointer");
    }
    List<String> argTypes = new ArrayList<>();
    for (Value v : args) {
        argTypes.add(valueTypeName(v));
    }
    /* MethodInfo method =findMethod(obj.clazz,methodName,argTypes);
    if (method == null) {throw new RuntimeException("Method not found: "+ methodName );
    }
    return callMethod(obj,method,args); */
    MethodInfo sig = resolveMethodByDeclType(objVal.declaredClass, methodName, argTypes);
    if (sig == null) {
        throw new RuntimeException("Method not found: " + methodName);
    }
    MethodInfo target = resolveMethodByRuntimeType(objVal.asObject(), sig);
    return callMethod(objVal.asObject(), target, args);
    }
    private Value invokeSuperMethod(Value objVal,String methodName,List<Value> args) {
    ObjectInstance obj = objVal.asSuper();
    ClassInfo declClass = objVal.declaredClass;
    if (declClass.parent == null) {
        throw new RuntimeException(
            "No superclass"
        );
    }
    List<String> argTypes = new ArrayList<>();
    for (Value v : args) {
        argTypes.add(valueTypeName(v));
    }
    MethodInfo method = resolveMethodByDeclType(declClass.parent,methodName,argTypes);
    if (method == null) {
        throw new RuntimeException("Super method not found: "+ methodName);
    }
    return callMethod(obj,method,args);
    }
    private Value callMethod(ObjectInstance obj,MethodInfo method,List<Value> args) {
    currentThis.push(obj);
    functionBases.push(scopes.size());
    scopes.push(new HashMap<>());
    for (int i = 0;i < method.paramNames.size();i++) {
        Value arg = args.get(i);
        String paramType = method.paramTypes.get(i);
        if (classes.containsKey(paramType)&& arg.type == Type.OBJECT){
        arg = Value.ofObject(arg.asObject(),classes.get(paramType));
        }
        scopes.peek().put(method.paramNames.get(i),arg);
    }
    currentMethod.push(method);
    try {
        visit(method.ctx.methodBody);
    } catch (ReturnException r) {
        return r.value;
    }finally{
        scopes.pop();
        functionBases.pop();
        currentThis.pop();
        currentMethod.pop();
    }
    return null;
    }
    private String valueTypeName(Value v) {
    switch (v.type) {
        case INT:
            return "int";
        case CHAR:
            return "char";
        case BOOL:
            return "boolean";
        case STRING:
            return "string";
        case ARRAY:
            return v.arrayBaseType;
        case OBJECT:
            return v.declaredClass.name;
        case NULL:
            return "null";
        default:
            throw new RuntimeException("Unknown value type" );
    }
    }
    private boolean matchArgs(MethodInfo method,List<String> argTypes) {
    if (method.paramTypes.size()
        != argTypes.size()) {
        return false;
    }
    for (int i = 0; i < argTypes.size(); i++) {
        String expected = method.paramTypes.get(i);
        String actual = argTypes.get(i);
        if (expected.equals(actual)) {
            continue;
        }
        ClassInfo actualClass = classes.get(actual);
        ClassInfo expectedClass = classes.get(expected);
        if (actualClass == null || expectedClass == null) {
            return false;
        }
        if (!isAssignable(actualClass,expectedClass)) {
            return false;
        }
    }
    return true;
    }
    private void linkInheritance() {
    for (ClassInfo clazz : classes.values()) {
        if (clazz.parentName == null) {
            continue;
        }
        ClassInfo parent = classes.get(clazz.parentName);
        if (parent == null) {
            throw new RuntimeException("Unknown parent class: "+ clazz.parentName);
        }
        clazz.parent = parent;
    }
    }
    private void parseClassMembers(MiniJavaParser.ClassDeclarationContext ctx) {
    String className = ctx.identifier().getText();
    ClassInfo clazz = classes.get(className);
    for (MiniJavaParser.ClassBodyDeclarationContext body: ctx.classBody().classBodyDeclaration()) {
        if (body.fieldDeclaration() != null) {
            registerField(clazz,body.fieldDeclaration());
        }
        else if (body.methodDeclaration() != null) {
            registerMethod(clazz,body.methodDeclaration());
        }
        else if (body.constructorDeclaration() != null) {
            registerConstructor(clazz,body.constructorDeclaration());
        }
        else {
    
        }
    }
     if (clazz.constructors.isEmpty()) {
        ConstructorInfo defaultCtor = new ConstructorInfo();
        defaultCtor.ownerClass = clazz;
        clazz.constructors.add(defaultCtor);
    }
    }
    private Value createObject(String className,List<Value> args) {
    ClassInfo clazz = classes.get(className);
    if (clazz == null) {
        throw new RuntimeException("Unknown class: " + className);
    }
    ObjectInstance obj = new ObjectInstance(clazz);
    zeroInitializeFields(obj, clazz);
    List<String> argTypes = new ArrayList<>();
    for (Value v : args) {
        argTypes.add(valueTypeName(v));
    }
    ConstructorInfo ctor = findConstructor(clazz,argTypes);
    if (ctor == null) {
        throw new RuntimeException("Constructor not found");
    }
    callConstructor(obj,ctor,args,false);
    return Value.ofObject(obj,clazz);
    }
    private void zeroInitializeFields(ObjectInstance obj,ClassInfo clazz) {
    if (clazz.parent != null) {zeroInitializeFields(obj,clazz.parent);}
    for (FieldInfo field : clazz.fields.values()) {
        obj.fields.put(field,defaultValue(field.typeName));
    }
    }
    private void initializeOwnFields(ObjectInstance obj, ClassInfo clazz) {
    for (FieldInfo field : clazz.fields.values()) {
        if (field.initializer != null) {
            Value value = visit(field.initializer);
            // Preserve declared class for class-typed fields
            if (classes.containsKey(field.typeName)) {
                ClassInfo fieldClass = classes.get(field.typeName);
                if (value.type == Type.NULL) {
                    value = Value.ofNull(field.typeName);
                    value.declaredClass = fieldClass;
                } else if (value.type == Type.OBJECT) {
                    value = Value.ofObject(value.asObject(), fieldClass);
                }
            }
            obj.fields.put(field, value);
        }
    }
    }
    private Value getField(Value objVal,String fieldName) {
    ClassInfo declClass = objVal.declaredClass;
    FieldInfo field = findField(declClass, fieldName);
    if (field == null) {
        throw new RuntimeException("Unknown field: " + fieldName);
    }
    Value v = objVal.asObject().fields.get(field);
    if (v.type == Type.OBJECT) {
        ClassInfo fieldDeclClass = classes.get(field.typeName);
        if (fieldDeclClass != null) {
            return Value.ofObject(v.asObject(),fieldDeclClass);
        }
    }
    return v;
    }
    private Value getSuperField(Value objVal,String fieldName) {
    ClassInfo declClass = objVal.declaredClass;
    if (declClass.parent == null) {
        throw new RuntimeException(
            "No superclass"
        );
    }
    FieldInfo field = findSuperField(declClass,fieldName);
    if (field == null) {
        throw new RuntimeException(
            "Unknown super field: "
            + fieldName
        );
    }
    Value v = objVal.asObject().fields.get(field);
    if (v.type == Type.OBJECT) {
        ClassInfo fieldDeclClass = classes.get(field.typeName);
        if (fieldDeclClass != null) {
            return Value.ofObject(v.asObject(),fieldDeclClass);
        }
    }
    return v;
    }
    private FieldInfo findFieldFromClass(ClassInfo clazz,String fieldName) {
    ClassInfo cur = clazz;
    while (cur != null) {
        if (cur.fields.containsKey(fieldName)) {
            return cur.fields.get(fieldName);
        }
        cur = cur.parent;
    }
    return null;
    }
    private boolean hasField(ClassInfo clazz,String name) {
    if (clazz.fields.containsKey(name)) {
        return true;
    }
    if (clazz.parent != null) {
        return hasField(clazz.parent, name);
    }
    return false;
    }
    private void setField(Value objVal,String name,Value value) {
    ClassInfo declClass = objVal.declaredClass;
    FieldInfo field = findField(declClass, name);
    if (field == null) {
        throw new RuntimeException(
            "Unknown field: " + name
        );
    }
    objVal.asObject().fields.put(field, value);
    }
    private boolean isAssignable(ClassInfo actual, ClassInfo expected) {
    ClassInfo cur = actual;
    while (cur != null) {
        if (cur == expected) {
            return true;
        }
        cur = cur.parent;
    }
    return false;
    }
    private boolean sameConstructorSignature(ConstructorInfo a,ConstructorInfo b) {
    if (a.paramTypes.size() != b.paramTypes.size()) {
        return false;
    }
    for (int i = 0;i < a.paramTypes.size();i++) {
        if (!a.paramTypes.get(i).equals(b.paramTypes.get(i))) {
            return false;
        }
    }
    return true;
   }
    private boolean sameMethodSignature(MethodInfo a,MethodInfo b) {
    if (!a.name.equals(b.name)) {
        return false;
    }
    if (a.paramTypes.size()!= b.paramTypes.size()) {
        return false;
    }
    for (int i = 0;i < a.paramTypes.size();i++) {
        if (!a.paramTypes.get(i).equals(b.paramTypes.get(i))) {
            return false;
        }
    }
    return true;
    }
    private void validateOverrides() {
    for (ClassInfo clazz : classes.values()) {
        if (clazz.parent == null) {
            continue;
        }
        for (List<MethodInfo> overloads: clazz.methods.values()) {
            for (MethodInfo method : overloads) {
                MethodInfo parentMethod = findMethodInParent(clazz.parent,method);
                if (parentMethod == null) {
                    continue;
                }
                if (!method.returnType.equals(parentMethod.returnType)) {
                    throw new RuntimeException("Illegal override of method: "+ method.name);
                }
            }
        }
    }
    }
    private MethodInfo findMethodInParent(ClassInfo clazz,MethodInfo target) {
    ClassInfo cur = clazz;
    while (cur != null) {
        List<MethodInfo> overloads = cur.methods.get(target.name);
        if (overloads != null) {
            for (MethodInfo m : overloads) {
                if (sameMethodSignature(m, target)) {
                    return m;
                }
            }
        }
        cur = cur.parent;
    }
    return null;
    }
    private MethodInfo resolveMethodByDeclType(ClassInfo declClass,String methodName,List<String> argTypes) {
    List<MethodInfo> candidates = new ArrayList<>();
    ClassInfo cur = declClass;
    while (cur != null) {
        List<MethodInfo> methods = cur.methods.get(methodName);
        if (methods != null) {
            candidates.addAll(methods);
        }
        cur = cur.parent;
    }
    MethodInfo best = null;
    int bestCost = Integer.MAX_VALUE;
    for (MethodInfo m : candidates) {
        if (m.paramTypes.size() != argTypes.size()) {
            continue;
        }
        boolean ok = true;
        int cost = 0;
        for (int i = 0; i < argTypes.size(); i++) {
            String expected = m.paramTypes.get(i);
            String actual = argTypes.get(i);
            if (expected.equals(actual)) {
                continue;
            }
            if (actual.equals("null")) {
                if (classes.containsKey(expected)|| expected.endsWith("[]")) {
                    cost += 100;
                    continue;
                }
                ok = false;
                break;
            }
            ClassInfo actualClass = classes.get(actual);
            ClassInfo expectedClass = classes.get(expected);
            if (actualClass != null && expectedClass != null) {
                int dist = inheritanceDistance(actualClass, expectedClass);
                if (dist >= 0) {
                    cost += dist;
                    continue;
                }
            }
            ok = false;
            break;
        }
        if (!ok) {
            continue;
        }
        if (cost < bestCost) {
            best = m;
            bestCost = cost;
        }
    }
    return best;
   }
    private int inheritanceDistance(ClassInfo actual,ClassInfo expected) {
    int dist = 0;
    ClassInfo cur = actual;
    while (cur != null) {
        if (cur == expected) {
            return dist;
        }
        cur = cur.parent;
        dist++;
    }
    return -1;
   }
    private MethodInfo resolveMethodByRuntimeType(ObjectInstance obj,MethodInfo sig) {
    ClassInfo cur = obj.clazz;
    while (cur != null) {
        List<MethodInfo> methods = cur.methods.get(sig.name);
        if (methods != null) {
            for (MethodInfo m : methods) {
                if (sameSignature(m, sig)) {
                    return m;
                }
            }
        }
        cur = cur.parent;
    }
    return sig;
   }
    boolean isSameTree(ClassInfo a, ClassInfo b) {
    Set<ClassInfo> set = new HashSet<>();
    while (a != null) {
        set.add(a);
        a = a.parent;
    }
    while (b != null) {
        if (set.contains(b)) return true;
        b = b.parent;
    }
    return false;
    }
    public Runresult runresult = new Runresult();
    public Runresult run(String code) {
        CharStream input = CharStreams.fromString(code);
        MiniJavaLexer lexer = new MiniJavaLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        MiniJavaParser parser = new MiniJavaParser(tokens);    
        parser.removeErrorListeners();      
        parser.setErrorHandler(new BailErrorStrategy());
        try{
        ParseTree tree = parser.compilationUnit();
        Value result = this.visit(tree);
        }
        catch(Exception e){
            runresult.result += "error message:" + e.getMessage() + "\n";
            runresult.result += "Process exits with 34."+"\n";
        }
        return runresult;
    }
}