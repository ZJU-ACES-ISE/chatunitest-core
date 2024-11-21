package zju.cst.aces.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
public class ClassInfo {
    public String projectName;
    public String moduleName;
    public String packageName;
    public String fullClassName;
    public String className;
    public int index;
    public String modifier;
    public String extend;
    public String implement;
    public String packageDeclaration;
    public String classSignature;
    public boolean hasConstructor;
    public boolean isPublic;
    public boolean isInterface;
    public boolean isAbstract;
    public boolean isTest;
    public List<String> imports;
    public List<String> importTypes;
    public List<String> fields;
    public List<String> superClasses;
    public List<String> implementedTypes;
    public Map<String, String> methodSigs;
    public List<String> methodsBrief;
    public List<String> constructorSigs;
    public List<String> constructorBrief;
    public List<String> getterSetterSigs;
    public List<String> getterSetterBrief;
    public Map<String, Set<String>> constructorDeps;
    public List<String> mockDeps;
    public String compilationUnitCode;
    public String classDeclarationCode;
    public List<String> subClasses;
    public int lineCount;
}
