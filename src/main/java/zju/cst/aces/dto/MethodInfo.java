package zju.cst.aces.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class MethodInfo {
    public String projectName;
    public String moduleName;
    public String packageName;
    public String fullClassName;
    public String className;
    public String methodName;
    public String returnType;
    public String brief;
    public String methodSignature;
    public String sourceCode;
    public boolean useField;
    public boolean isConstructor;
    public boolean isGetSet;
    public boolean isPublic;
    public boolean isBoolean;
    public boolean isAbstract;
    public boolean isTest;
    public List<String> parameters;
    public Map<String, Set<String>> dependentMethods;
    public String full_method_info;
    public String method_comment;
    public String method_annotation;
    public int lineCount;
    public int branchCount;
}
