package zju.cst.aces.dto;

import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import lombok.Data;

import java.nio.file.Path;
import java.util.*;

@Data
public class PromptInfo {
    public boolean hasDep;
    public String fullClassName;
    public String className;
    public String methodName;
    public String methodSignature;
    public String context; // context with only focal method.
    public String otherMethodBrief;
    public String otherMethodBodies;
    public Map<String, String> constructorDeps = new HashMap<>(); // dependent classes in constructor.
    public Map<String, String> methodDeps = new HashMap<>(); // dependent classes in method parameters and body.
    public TestMessage errorMsg;
    public String unitTest = "";
    public String fullTestName;
    public Path testPath;
    public Map<String, List<MethodDeclaration>> correctTests = new HashMap<>();
    public List<RoundRecord> records = new ArrayList<>();
    public MethodInfo methodInfo;
    public ClassInfo classInfo;

    public PromptInfo(boolean hasDep, String fullClassName, String methodName,
                      String methodSignature) {
        this.hasDep = hasDep;
        this.fullClassName = fullClassName;
        this.className = fullClassName.contains(".") ?
                fullClassName.substring(fullClassName.lastIndexOf(".") + 1) : fullClassName;
        this.methodName = methodName;
        this.methodSignature = methodSignature;
    }

    public PromptInfo(){}

    public PromptInfo(PromptInfo p) {
        this.setHasDep(p.isHasDep());
        this.setFullClassName(p.getFullClassName());
        this.setClassName(p.getClassName());
        this.setMethodName(p.getMethodName());
        this.setMethodSignature(p.getMethodSignature());
        this.setContext(p.getContext());
        this.setOtherMethodBrief(p.getOtherMethodBrief());
        this.setConstructorDeps(p.getConstructorDeps());
        this.setMethodDeps(p.getMethodDeps());
        this.setErrorMsg(p.getErrorMsg());
        this.setUnitTest(p.getUnitTest());
        this.setFullTestName(p.getFullTestName());
        this.setTestPath(p.getTestPath());
        this.setCorrectTests(p.getCorrectTests());
        this.setRecords(p.getRecords());
        this.setMethodInfo(p.getMethodInfo());
        this.setClassInfo(p.getClassInfo());
    }

    public void addMethodDeps(String depClassName, String methodDep) {
        if (methodDep == null) {
            return;
        }
        this.methodDeps.put(depClassName, methodDep);
    }

    public void addConstructorDeps(String depClassName, String constructorDep) {
        if (constructorDep == null) {
            return;
        }
        this.constructorDeps.put(depClassName, constructorDep);
    }

    public void addCorrectTest(MethodDeclaration m) {
        ClassOrInterfaceDeclaration c = (ClassOrInterfaceDeclaration) m.getParentNode().orElseThrow();
        String className = c.getNameAsString();
        if (this.correctTests.containsKey(className)) {
            this.correctTests.get(className).add(m);
            return;
        } else {
            List<MethodDeclaration> methods = new ArrayList<>();
            methods.add(m);
            this.correctTests.put(className, methods);
        }
    }

    public void addRecord(RoundRecord r) {
        this.records.add(r);
    }
}
