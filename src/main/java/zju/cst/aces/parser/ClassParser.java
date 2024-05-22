package zju.cst.aces.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.Comment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.google.gson.Gson;
import org.jetbrains.annotations.NotNull;
import slicing.graphs.CallGraph;
import slicing.graphs.sdg.SDG;
import zju.cst.aces.api.Logger;
import zju.cst.aces.api.Project;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.OCM;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ClassParser {
    private static final String separator = "_";
    private static Path classOutputPath;
    private static ClassInfo classInfo;
    private static JavaParser parser;
    public int methodCount = 0;
    Project project;
    Logger logger;
    Gson GSON;
    AtomicInteger sharedInteger;
    Map<String, Map<String, String>> classMapping;
    OCM ocm;

    public ClassParser(JavaParser javaParser, Project project, Path path,
                       Logger logger, Gson gson, AtomicInteger sharedInteger,
                       Map<String, Map<String, String>> classMapping, OCM ocm) {
        this.parser = javaParser;
        this.classOutputPath = path;
        this.project = project;
        this.logger = logger;
        this.GSON = gson;
        this.sharedInteger = sharedInteger;
        this.classMapping = classMapping;
        this.ocm = ocm;
    }

    public int extractClass(String classPath) throws FileNotFoundException {
        File file = new File(classPath);
        ParseResult<CompilationUnit> parseResult = parser.parse(file);
        CompilationUnit cu = parseResult.getResult().orElseThrow();
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration classDeclaration : classes) {
            try {
                classInfo = getInfoByClass(cu, classDeclaration);
                exportClassInfo(classInfo, classDeclaration);
                extractConstructors(cu, classDeclaration);
                extractMethods(cu, classDeclaration);

                addClassMapping(classInfo);
                methodCount += classDeclaration.getMethods().size();
            } catch (Exception e) {
                logger.error("In ClassParser.extractClass Exception: when parse class " + classDeclaration.getNameAsString() + " :\n" + e);
            }
        }
        return classes.size();
    }

    public int extractClass(CompilationUnit cu) {
        List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
        for (ClassOrInterfaceDeclaration classDeclaration : classes) {
            try {
                classInfo = getInfoByClass(cu, classDeclaration);
                exportClassInfo(classInfo, classDeclaration);
                extractConstructors(cu, classDeclaration);
                extractMethods(cu, classDeclaration);

                addClassMapping(classInfo);
                methodCount += classDeclaration.getMethods().size();
            } catch (Exception e) {
                logger.error("In ClassParser.extractClass Exception: when parse class " + classDeclaration.getNameAsString() + " :\n" + e);
            }
        }
        return classes.size();
    }

    private static boolean isJavaSourceDir(Path path) {
        return Files.isDirectory(path) && Files.exists(path.resolve(
                "src" + File.separator + "main" + File.separator + "java"));
    }

    private void extractMethods(CompilationUnit cu, ClassOrInterfaceDeclaration classDeclaration) throws IOException {
        List<MethodDeclaration> methods = classDeclaration.getMethods();
        for (MethodDeclaration m : methods) {
            if (m.hasRange()) {
                MethodInfo info = getInfoByMethod(cu, classDeclaration, m);
                exportMethodInfo(info, classDeclaration, m);
            }
        }
    }

    private void extractConstructors(CompilationUnit cu, ClassOrInterfaceDeclaration classDeclaration) throws IOException {
        List<ConstructorDeclaration> constructors = classDeclaration.getConstructors();
        for (ConstructorDeclaration c : constructors) {
            if (c.hasRange()) {
                MethodInfo info = getInfoByMethod(cu, classDeclaration, c);
                exportConstructorInfo(info, classDeclaration, c);
            }
        }
    }

    /**
     * Extract class information to json format
     */
    private ClassInfo getInfoByClass(CompilationUnit cu, ClassOrInterfaceDeclaration classNode) {
        ClassInfo ci = new ClassInfo(
                cu,
                classNode,
                this.sharedInteger.getAndIncrement(),
                getClassSignature(cu, classNode),
                getImports(getImportDeclarations(cu)),
                getFields(cu, classNode.getFields()),
                getSuperClasses(classNode),
                getMethodSignatures(classNode),
                getBriefMethods(cu, classNode),
                hasConstructors(classNode),
                getConstructorSignatures(classNode),
                getBriefConstructors(cu, classNode),
                getGetterSetterSig(cu, classNode),
                getGetterSetter(cu, classNode),
                getConstructorDeps(cu, classNode),
                getSubClasses(classNode)
        );

        ci.setPublic(classNode.isPublic());
//        ci.setPublic(!classNode.isPrivate() && !classNode.isProtected());
        ci.setAbstract(classNode.isAbstract());
        ci.setInterface(classNode.isInterface());
        ci.setCode(cu.toString(), classNode.toString());
        ci.setFullClassName(cu.getPackageDeclaration().orElseThrow().getNameAsString() + "." + ci.className);
        ci.setImplementedTypes(getInterfaces(classNode));
        return ci;
    }

    /**
     * Generate extracted information of focal method(constructor).
     */
    private MethodInfo getInfoByMethod(CompilationUnit cu, ClassOrInterfaceDeclaration classNode, CallableDeclaration node) {
        MethodInfo mi = new MethodInfo(
                classNode.getNameAsString(),
                node.getNameAsString(),
                getBriefMethod(cu, node),
                getMethodSig(node),
                getMethodCode(cu, node),
                getParameters(node),
                getDependentMethods(cu, node),
                node.toString(),
                getMethodComment(node),
                getMethodAnnotation(node)
        );
        mi.setUseField(useField(node));
        mi.setConstructor(node.isConstructorDeclaration());
        mi.setGetSet(isGetSet2(node));
        mi.setPublic(isPublic(node));
        mi.setBoolean(isBoolean(node));
        mi.setAbstract(node.isAbstract());
//        findObjectConstructionCode(cu, node);
//        if (node instanceof MethodDeclaration) {
//            findObjectConstructionCode(cu, node.asMethodDeclaration());
//        }
        return mi;
    }

    private Map<String, Set<String>> getConstructorDeps(CompilationUnit cu, ClassOrInterfaceDeclaration classNode) {
        Map<String, Set<String>> constructorDeps = new LinkedHashMap<>();
        for (ConstructorDeclaration c : classNode.getConstructors()) {
            Map<String, Set<String>> tmp = getDependentMethods(cu, c);
            for (String key : tmp.keySet()) {
                // Do not need method dependency
                if (constructorDeps.containsKey(key)) {
                    continue;
                } else {
                    constructorDeps.put(key, tmp.get(key));
                }
            }
        }
        return constructorDeps;
    }

    private List<String> getGetterSetter(CompilationUnit cu, ClassOrInterfaceDeclaration classNode) {
        List<String> getterSetter = new ArrayList<>();
        for (MethodDeclaration m : classNode.getMethods()) {
            if (isGetSet2(m)) {
                getterSetter.add(getBriefMethod(cu, m));
            }
        }
        return getterSetter;
    }

    private List<String> getGetterSetterSig(CompilationUnit cu, ClassOrInterfaceDeclaration classNode) {
        List<String> getterSetter = new ArrayList<>();
        for (MethodDeclaration m : classNode.getMethods()) {
            if (isGetSet2(m)) {
                getterSetter.add(m.getSignature().asString());
            }
        }
        return getterSetter;
    }

    /**
     * Get method signature (the parameters in signature are qualified name)
     */
    private String getMethodSig(CallableDeclaration node) {
        if (node instanceof MethodDeclaration) {
            return node.getSignature().asString();
        } else {
            return node.getSignature().asString();
        }
    }

    private List<ImportDeclaration> getImportDeclarations(CompilationUnit compilationUnit) {
        return compilationUnit.getImports();
    }

    /**
     * get String format imports by imports declaration, each import declaration is a line
     */
    private List<String> getImports(List<ImportDeclaration> importDeclarations) {
        List<String> imports = new ArrayList<>();
        for (ImportDeclaration i : importDeclarations) {
            imports.add(i.toString().trim());
        }
        return imports;
    }

    private boolean hasConstructors(ClassOrInterfaceDeclaration classNode) {
        return classNode.getConstructors().size() > 0;
    }

    private List<String> getSuperClasses(ClassOrInterfaceDeclaration node) {
        List<String> superClasses = new ArrayList<>();
        node.getExtendedTypes().forEach(sup -> {
            superClasses.add(sup.getNameAsString());
        });
        return superClasses;
    }

    public List<String> getSubClasses(ClassOrInterfaceDeclaration node) {
        String targetClassName = node.getFullyQualifiedName().orElseThrow().toString();
        List<String> subClasses = new ArrayList<>();
        List<String> classPaths = ProjectParser.scanSourceDirectory(this.project);
        if (classPaths.isEmpty()) {
            return null;
        }
        try {
            for (String classPath : classPaths) {
                ParseResult<CompilationUnit> parseResult = parser.parse(new File(classPath));
                CompilationUnit cu = parseResult.getResult().orElseThrow();
                String packageName=cu.getPackageDeclaration().isEmpty()?"":cu.getPackageDeclaration().get().getNameAsString();
                List<ClassOrInterfaceDeclaration> classes = cu.findAll(ClassOrInterfaceDeclaration.class);
                for (ClassOrInterfaceDeclaration classDeclaration : classes) {
                    for (ClassOrInterfaceType extendedType : classDeclaration.getExtendedTypes()) {
                        if (targetClassName.equals(packageName+"."+extendedType.getNameAsString())) {
                            subClasses.add(classDeclaration.getFullyQualifiedName().orElseThrow().toString());
                        }
                    }
                }
            }
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
        return subClasses;
    }

    private List<String> getInterfaces(ClassOrInterfaceDeclaration node) {
        List<String> interfaces = new ArrayList<>();
        node.getImplementedTypes().forEach(sup -> {
            interfaces.add(sup.getNameAsString());
        });
        return interfaces;
    }

    /**
     * Get the map of all method signatures and id in class
     */
    private Map<String, String> getMethodSignatures(ClassOrInterfaceDeclaration node) {
        Map<String, String> mSigs = new LinkedHashMap<>();
        List<MethodDeclaration> methods = node.getMethods();
        int i = 0;
        for (; i < methods.size(); i++) {
            if (!methods.get(i).hasRange()) continue;
            try {
                mSigs.put(methods.get(i).getSignature().asString(), String.valueOf(i));
            } catch (Exception e) {
                throw new RuntimeException("In ClassParser getMethodSignatures: when resolve method: " + methods.get(i).getNameAsString() + ": " + e);
            }
        }
        List<ConstructorDeclaration> constructors = node.getConstructors();
        for (; i < methods.size() + constructors.size(); i++) {
            if (!constructors.get(i - methods.size()).hasRange()) continue;
            mSigs.put(constructors.get(i - methods.size()).getSignature().asString(), String.valueOf(i));
        }
        return mSigs;
    }

    private List<String> getConstructorSignatures(ClassOrInterfaceDeclaration node) {
        List<String> cSigs = new ArrayList<>();
        node.getConstructors().forEach(c -> {
            if (!c.hasRange()) return;
            cSigs.add(c.getSignature().asString());
        });
        return cSigs;
    }

    private List<String> getBriefConstructors(CompilationUnit cu, ClassOrInterfaceDeclaration node) {
        List<ConstructorDeclaration> constructors = node.getConstructors();
        List<String> cSigs = new ArrayList<>();
        for (ConstructorDeclaration c : constructors) {
            if (c.hasRange()) {
                cSigs.add(getBriefMethod(cu, c));
            }
        }
        return cSigs;
    }


    /**
     * Get all brief method in class
     */
    private List<String> getBriefMethods(CompilationUnit cu, ClassOrInterfaceDeclaration node) {
        List<String> mSigs = new ArrayList<>();
        node.getMethods().forEach(m -> {
            if (m.hasRange()) mSigs.add(getBriefMethod(cu, m));
        });
        node.getConstructors().forEach(c -> {
            if (c.hasRange()) mSigs.add(getBriefMethod(cu, c));
        });
        return mSigs;
    }

    /**
     * Get brief method(construcor)
     * Note:
     * Get source code from begin of method to begin of body
     */
    private String getBriefMethod(CompilationUnit cu, CallableDeclaration node) {
        String sig = "";
        if (node instanceof MethodDeclaration) {
            MethodDeclaration methodNode = (MethodDeclaration) node;
            if (methodNode.getBody().isPresent()) {
                sig = getSourceCodeByPosition(getTokenString(cu),
                        methodNode.getBegin().orElseThrow(), methodNode.getBody().get().getBegin().orElseThrow());
                sig = sig.substring(0, sig.lastIndexOf("{") - 1) + "{}";
            } else {
                sig = getSourceCodeByPosition(getTokenString(cu),
                        methodNode.getBegin().orElseThrow(), methodNode.getEnd().orElseThrow());
            }
        } else if (node instanceof ConstructorDeclaration) {
            ConstructorDeclaration constructorNode = (ConstructorDeclaration) node.removeComment();
            sig = getSourceCodeByPosition(getTokenString(cu),
                    constructorNode.getBegin().orElseThrow(), constructorNode.getBody().getBegin().orElseThrow());
            sig = sig.substring(0, sig.lastIndexOf("{") - 1) + "{}";
        }
        return sig;
    }

    /**
     * Get class signature
     */
    public static String getClassSignature(CompilationUnit cu, ClassOrInterfaceDeclaration node) {
        if (!node.hasRange()) {
            return node.getNameAsString();
        }
        return getSourceCodeByPosition(getTokenString(cu), node.getBegin().orElseThrow(), node.getName().getEnd().orElseThrow());
    }

    /**
     * Get method(constructor) source code start from the first modifier to the end of the node.
     */
    private String getMethodCode(CompilationUnit cu, CallableDeclaration node) {
        return node.getTokenRange().orElseThrow().toString();
    }

    private String getMethodAnnotation(CallableDeclaration node) {
        StringBuffer sb = new StringBuffer();
        // 检查是否有注解
        if (node.getAnnotations().isEmpty()) {
            // 如果没有注解，返回空字符串
            return "";
        }
        for (Object annotation : node.getAnnotations()) {
            sb.append(annotation.toString() + "\n");
        }
        return sb.toString();
    }

    private String getMethodComment(CallableDeclaration node) {
        Optional<Comment> commentOptional = node.getComment();
        if (commentOptional.isPresent()) {
            Comment comment = commentOptional.get();
            return comment.getContent();
        }
        return "";
    }

    /**
     * Get full fields declaration of.
     */
    private List<String> getFields(CompilationUnit cu, List<FieldDeclaration> nodes) {
        List<String> fields = new ArrayList<>();
        for (FieldDeclaration f : nodes) {
            fields.add(getFieldCode(cu, f));
        }
        return fields;
    }

    /**
     * Get field source code start from the first modifier to the end of the node
     */
    private String getFieldCode(CompilationUnit cu, FieldDeclaration node) {
        return node.getTokenRange().orElseThrow().toString();
    }

    /**
     * Whether the method uses a field
     */
    private boolean useField(CallableDeclaration node) {
        return node.findAll(FieldAccessExpr.class).size() > 0;
    }

    /**
     * Whether the method is a getter or setter (assume the getter and setter access the field by "this")
     */
    private boolean isGetSet(CallableDeclaration node) {
        if (node.isConstructorDeclaration()) {
            return false;
        }
        if (!node.getNameAsString().startsWith("get") && !node.getNameAsString().startsWith("set")) {
            return false;
        }

        List<FieldAccessExpr> fieldAccesses = node.findAll(FieldAccessExpr.class);
        for (FieldAccessExpr fa : fieldAccesses) {
            // getter: return field
            if (fa.getParentNode().orElse(null) instanceof ReturnStmt) {
                return true;
            }
            // setter: assign field
            if (fa.getParentNode().orElse(null) instanceof AssignExpr && ((AssignExpr) fa.getParentNode().orElseThrow()).getTarget().equals(fa)) {
                return true;
            }
        }
        return false;
    }

    private boolean isGetSet2(CallableDeclaration node) {
        if (node.isConstructorDeclaration()) {
            return false;
        }
        if (node.getNameAsString().startsWith("get") && node.getParameters().size() == 0) {
            return true;
        }
        if (node.getNameAsString().startsWith("set")) {
            return true;
        }
        return false;
    }

    private boolean isPublic(CallableDeclaration node) {
        return node.isPublic();
    }

    private boolean isBoolean(CallableDeclaration node) {
        if (node.isConstructorDeclaration()) {
            return false;
        }
        if (node.getNameAsString().startsWith("is")
                && node.getParameters().size() == 0
                && node.asMethodDeclaration().getTypeAsString().equals("boolean")) {
            return true;
        }
        return false;
    }

    /**
     * Get method parameters
     */
    private List<String> getParameters(CallableDeclaration node) {
        List<String> parameters = new ArrayList<>();
        node.getParameters().forEach(p -> {
            parameters.add(((Parameter) p).getType().asString());
        });
        return parameters;
    }

    private Map<String, Set<String>> getDependentMethods(CompilationUnit cu, CallableDeclaration node) {
        Map<String, Set<String>> dependentMethods = new LinkedHashMap<>();
        List<MethodCallExpr> methodCalls = node.findAll(MethodCallExpr.class);
        List<Parameter> pars = node.getParameters();

        for (Parameter p : pars) {
            try {
                if (p.getType().isPrimitiveType()) {
                    continue;
                }
                if (p.getType().isArrayType()) {
                    String dependentType = p.resolve().getType().asArrayType().getComponentType().describe();
                    dependentMethods.put(dependentType, new HashSet<String>());
                    continue;
                } else if (p.getTypeAsString().split("<")[0].endsWith("Map")
                        || p.getTypeAsString().split("<")[0].endsWith("List")
                        || p.getTypeAsString().split("<")[0].endsWith("Set")) {
                    continue;
                } else if (p.getType().getChildNodes().size() == 1) {
                    String dependentType = p.resolve().describeType();
                    dependentMethods.put(dependentType, new HashSet<String>());
                }
            } catch (Exception e) {

            }
        }
        for (MethodCallExpr m : methodCalls) {
            try {
                ResolvedMethodDeclaration md = m.resolve();
                String dependentType = md.declaringType().getQualifiedName();
                String mSig = getParamTypeInSig(md); // change parameters' type to non-qualified name
                Set<String> invocations = dependentMethods.get(dependentType);
                if (invocations == null) {
                    invocations = new HashSet<>();
                }
                invocations.add(mSig);
                dependentMethods.put(dependentType, invocations);
            } catch (Exception e) {
//                logger.warn("Cannot resolve method call: " + m.getNameAsString() + " in: " + node.getNameAsString());
            }
        }
        return dependentMethods;
    }

    private static String getParamTypeInSig(ResolvedMethodDeclaration md) {
        String sig = md.getName() + "(";
        for (int i = 0; i < md.getNumberOfParams(); i++) {
//            String paramType = md.getParam(i).getType().erasure().describe();
            String paramType = md.getParam(i).getType().describe();
            if (paramType.contains(".")) {
                paramType = paramType.substring(paramType.lastIndexOf(".") + 1);
            }
            if (i == md.getNumberOfParams() - 1) {
                sig += paramType;
            } else {
                sig += paramType + ", ";
            }
        }
        sig += ")";
        return sig;
    }

    public String getLastType(String type) {
        return type.substring(type.lastIndexOf(".") + 1);
    }

    private static String getSourceCodeByPosition(String code, Position begin, Position end) {
        String[] lines = code.split("\\n");
        StringBuilder sb = new StringBuilder();

        for (int i = begin.line - 1; i < end.line; i++) {
            if (i == begin.line - 1 && i == end.line - 1) {
                // The range is within a single line
                sb.append(lines[i].substring(begin.column - 1, end.column));
            } else if (i == begin.line - 1) {
                // The first line of the range
                sb.append(lines[i].substring(begin.column - 1));
            } else if (i == end.line - 1) {
                // The last line of the range
                sb.append(lines[i].substring(0, end.column));
            } else {
                // A middle line in the range
                sb.append(lines[i]);
            }

            // Add line breaks except for the last line
            if (i < end.line - 1) {
                sb.append(System.lineSeparator());
            }
        }

        return sb.toString();
    }

    private static String getTokenString(@NotNull Node node) {
        if (node.getTokenRange().isPresent()) {
            return node.getTokenRange().get().toString();
        } else {
            return "";
        }
    }

    private void exportClassInfo(ClassInfo classInfo, ClassOrInterfaceDeclaration classNode) throws IOException {
        Path classOutputDir = classOutputPath.resolve(classNode.getName().getIdentifier());
        if (!Files.exists(classOutputDir)) {
            Files.createDirectories(classOutputDir);
        }
        Path classInfoPath = classOutputDir.resolve("class.json");
        //set charset utf-8
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(classInfoPath.toFile()), StandardCharsets.UTF_8)) {
            writer.write(this.GSON.toJson(classInfo));
        }
    }

    private void exportMethodInfo(MethodInfo methodInfo, ClassOrInterfaceDeclaration classNode, MethodDeclaration node) throws IOException {
        Path classOutputDir = classOutputPath.resolve(classNode.getName().getIdentifier());
        if (!Files.exists(classOutputDir)) {
            Files.createDirectories(classOutputDir);
        }
        Path info = classOutputDir.resolve(getFilePathBySig(node.getSignature().asString()));
        //set charset utf-8
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(info.toFile()), StandardCharsets.UTF_8)) {
            writer.write(this.GSON.toJson(methodInfo));
        }
    }

    private void exportConstructorInfo(MethodInfo methodInfo, ClassOrInterfaceDeclaration classNode, ConstructorDeclaration node) throws IOException {
        Path classOutputDir = classOutputPath.resolve(classNode.getName().getIdentifier());
        if (!Files.exists(classOutputDir)) {
            Files.createDirectories(classOutputDir);
        }
        Path info = classOutputDir.resolve(getFilePathBySig(node.getSignature().asString()));
        //set charset utf-8
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(info.toFile()), StandardCharsets.UTF_8)) {
            writer.write(this.GSON.toJson(methodInfo));
        }
    }

    /**
     * Generate a filename for the focal method json file by method signature.
     */
    private Path getFilePathBySig(String sig) {
        Map<String, String> mSigs = classInfo.methodSigs;
        return Paths.get(mSigs.get(sig) + ".json");
    }

    /**
     * Get the filename of the focal method by finding method name and parameters in mSig.
     */
    public static Path getFilePathBySig(String mSig, ClassInfo info) {
        Map<String, String> mSigs = info.methodSigs;
        return Paths.get(mSigs.get(mSig) + ".json");
    }

    private List<Path> getSources() {
        try (Stream<Path> paths = Files.walk(Path.of(System.getProperty("user.dir")))) {
            return paths
                    .filter(ClassParser::isJavaSourceDir)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new RuntimeException("In ClassParser.getSources: " + e);
        }
    }

    public void addClassMapping(ClassInfo classInfo) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("className", classInfo.className);
        map.put("packageName", classInfo.packageName);
        map.put("modifier", classInfo.modifier);
        map.put("extend", classInfo.extend);
        map.put("implement", classInfo.implement);
        if (this.classMapping == null) {
            this.classMapping = new LinkedHashMap<>();
        }
        this.classMapping.put("class" + classInfo.index, map);
    }

    /**
     * 获取对象创建示例代码，从两种语句中获取:
     * 1. 直接new了一个对象(ObjectCreationExpr).
     * 2. 调用函数（MethodCallExpr）创建并返回了一个对象(md.getReturnType().isReferenceType()).
     * @param node
     */
    public void findObjectConstructionCode(CompilationUnit cu, CallableDeclaration node) {
        var p = findCallerByCallGraph(cu, node);
        String methodBrief = getBriefMethod(cu, node);

        List<ObjectCreationExpr> objCreationStmts = node.findAll(ObjectCreationExpr.class);
        List<MethodCallExpr> methodCalls = node.findAll(MethodCallExpr.class);

        List<VariableDeclarationExpr> varDecls = node.findAll(VariableDeclarationExpr.class);
        List<String> paramDecls = null;
        if (node instanceof MethodDeclaration) {
            paramDecls = node.asMethodDeclaration().getParameters().stream().map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList());
        } else if (node instanceof ConstructorDeclaration) {
            paramDecls = node.asConstructorDeclaration().getParameters().stream().map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList());
        }

        Map<String, VariableDeclarationExpr> variableDeclarations = new HashMap<>();
        for (VariableDeclarationExpr varDecl : varDecls) {
            varDecl.getVariables().forEach(var -> {
                variableDeclarations.put(var.getNameAsString(), varDecl);
            });
        }

        for (ObjectCreationExpr expr : objCreationStmts) {
            try {
                if (!expr.getType().isReferenceType()) {
                    continue;
                }
                ResolvedReferenceTypeDeclaration objType = expr.resolve().declaringType();
                String typeName = objType.getQualifiedName();

                ExpressionStmt stmt = findExpressionStmt(expr);
                if (stmt == null) {
                    continue;
                }

                NodeList<Expression> argumentExprs = expr.getArguments();
                List<String> argNames = argumentExprs.stream().map(Node::toString).collect(Collectors.toList());

                StringBuilder sb = new StringBuilder();
                List<ExpressionStmt> depExprList = findArgDeclarationStmts(argNames, variableDeclarations);

                if (depExprList.isEmpty()) {
                    sb.append(stmt);
                } else {
                    var paramsFromCaller = findCallerByCallGraph(cu, node);
                    if (paramsFromCaller != null) {
                        for (CallableDeclaration<?> caller : paramsFromCaller) {
                            if (caller instanceof MethodDeclaration) {
                                MethodDeclaration callerMethod = (MethodDeclaration) caller;
                                List<String> callerParams = callerMethod.getParameters().stream().map(NodeWithSimpleName::getNameAsString).collect(Collectors.toList());
                                for (String argName : argNames) {
                                    if (callerParams.contains(argName)) {
                                        sb.append(methodBrief.substring(0, methodBrief.length() - 1) + "\n");
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    sb.append(
                            depExprList.stream().map(Node::toString).collect(Collectors.joining("\n"))
                    ).append("\n").append(stmt);
                }

                for (String argName : argNames) {
                    if (paramDecls.contains(argName)) {
                        sb.insert(0, methodBrief.substring(0, methodBrief.length() - 1) + "\n");
                        sb.append("\n}");
                        break;
                    }
                }
                ocm.add(typeName, classInfo.className, node.getNameAsString(), expr.getBegin().get().line, sb.toString());

            } catch (Exception e) {
                logger.warn("Cannot resolve expression : " + expr + "\n" + e.getMessage());
            }
        }

        for (MethodCallExpr expr : methodCalls) {
            try {
                ResolvedMethodDeclaration md = expr.resolve();

                // save return type
                if (!md.getReturnType().isReferenceType()) {
                    continue;
                }

                ResolvedReferenceType returnType = md.getReturnType().asReferenceType();
                String typeName = returnType.getQualifiedName();

                NodeList<Expression> argumentExprs = expr.getArguments();
                List<String> argNames = argumentExprs.stream().map(Node::toString).collect(Collectors.toList());

                // Add dependentType declaration stmt
                String scopeName;
                if (expr.getScope().isPresent()) {
                    scopeName = expr.getScope().get().toString();
                    argNames.add(scopeName);
                }

                StringBuilder sb = new StringBuilder();
                List<ExpressionStmt> depExprList = findArgDeclarationStmts(argNames, variableDeclarations);

                String stmt = createExpressionStmt(typeName, expr);

                if (depExprList.isEmpty()) {
                    sb.append(stmt);
                } else {
                    sb.append(
                            depExprList.stream().map(Node::toString).collect(Collectors.joining("\n"))
                    ).append("\n").append(stmt);
                }

                for (String argName : argNames) {
                    if (paramDecls.contains(argName)) {
                        sb.insert(0, methodBrief.substring(0, methodBrief.length() - 1) + "\n");
                        sb.append("\n}");
                        break;
                    }
                }
                ocm.add(typeName, classInfo.className, node.getNameAsString(), expr.getBegin().get().line, sb.toString());

            } catch (Exception e) {
                logger.warn("Cannot resolve expression : " + expr + "\n" + e.getMessage());
            }
        }
    }

    private Set<CallableDeclaration<?>> findCallerByCallGraph(CompilationUnit cu, CallableDeclaration node) {
        NodeList<CompilationUnit> cus = new NodeList<>();
        cus.add(cu);
        SDG sdg = new SDG();
        sdg.build(cus);
        CallGraph cg = sdg.getCallGraph();
        CallGraph.Vertex v = new CallGraph.Vertex(node);
        if (cg.containsVertex(v)) {
            if (node instanceof MethodDeclaration) {
                return cg.getCallTargets(node.asMethodDeclaration()).collect(Collectors.toSet());
            } else if (node instanceof ConstructorDeclaration) {
                return cg.getCallTargets(node.asConstructorDeclaration()).collect(Collectors.toSet());
            }
        }
        return null;
    }

    /**
     * 找到表达式节点对应那一行的语句
     */
    private static ExpressionStmt findExpressionStmt(Expression expr) {
        return expr.findAncestor(ExpressionStmt.class).orElse(null);
    }

    /**
     * 找到方法对应的调用节点。
     */
    private static String createExpressionStmt(String typeName, MethodCallExpr expr) {
        if (typeName.contains(".")) {
            typeName = typeName.substring(typeName.lastIndexOf(".") + 1);
        }
        return typeName + " " + typeName.toLowerCase() + " = " + expr + ";";
    }

    /**
     * 递归地找到参数的声明语句并添加到OCC中
     */
    private List<ExpressionStmt> findArgDeclarationStmts(List<String> typeNames, Map<String, VariableDeclarationExpr> variableDeclarations) {
        LinkedHashSet<ExpressionStmt> set = new LinkedHashSet<>();
        typeNames.forEach(typeName -> {
                if (variableDeclarations.containsKey(typeName)) {
                    VariableDeclarationExpr vd = variableDeclarations.get(typeName);
                    List<MethodCallExpr> temp = vd.findAll(MethodCallExpr.class);
                    if (!temp.isEmpty()) {
                        List<String> depNames = temp.get(0).getArguments().stream().map(Node::toString).collect(Collectors.toList());
                        set.addAll(findArgDeclarationStmts(depNames, variableDeclarations));
                    }
                    ExpressionStmt stmt = findExpressionStmt(vd);
                    if (stmt != null) {
                        set.add(stmt);
                    }
                }
        });
        return new ArrayList<>(set);
    }
}

class LengthComparator implements Comparator {
    @Override
    public int compare(Object obj1, Object obj2) { //按长度排序
        String s1 = (String) obj1;
        String s2 = (String) obj2;
        return s1.length() - s2.length();
    }
}
