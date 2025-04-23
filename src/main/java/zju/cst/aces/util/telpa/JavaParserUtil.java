package zju.cst.aces.util.telpa;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.types.ResolvedType;
import lombok.Data;
import lombok.var;
import slicing.graphs.CallGraph;
import slicing.graphs.jsysdg.JSysDG;
import slicing.graphs.sdg.SDG;
import slicing.slicing.MultiVariableCriterion;
import slicing.slicing.Slice;
import zju.cst.aces.api.Project;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.MethodExampleMap;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static zju.cst.aces.parser.ProjectParser.exportJson;

@Data
public class JavaParserUtil {
    private final Config config;
    private final JavaParser parser;
    private final Path srcFolderPath;
    private NodeList<CompilationUnit> compilationUnits;
    private static final Object lock = new Object();

    public JavaParserUtil(Config config) {
        this.config = config;
        this.parser = config.getParser();
        this.srcFolderPath = Paths.get(config.getProject().getBasedir().getAbsolutePath(), "src", "main", "java");
        // 不在构造函数中初始化compilationUnits，而是在第一次使用时初始化
        this.compilationUnits=getParseResult();
    }
    public static List<String> scanSourceDirectory(Project project) {
        List<String> classPaths = new ArrayList<>();
        File[] files = Paths.get(project.getCompileSourceRoots().get(0)).toFile().listFiles();
        if (files != null) {
            for (File file : files) {
                try {
                    Files.walk(file.toPath()).forEach(path -> {
                        if (path.toString().endsWith(".java")) {
                            classPaths.add(path.toString());
                        }
                    });
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return classPaths;
    }
    //get the Class Parsed by JavaParser
    public  NodeList<CompilationUnit> getParseResult(){
        List<String> classPaths = scanSourceDirectory(config.getProject());

        if (classPaths.isEmpty()) {
            config.getLogger().warn("No java file found in " + srcFolderPath);
            return null;
        }
        NodeList<CompilationUnit> cus = new NodeList<>();
        for (String classPath : classPaths) {
            File file = new File(classPath);
            try {
                ParseResult<CompilationUnit> parseResult = parser.parse(file);
                CompilationUnit cu = parseResult.getResult().orElseThrow(()->new NoSuchElementException("CompilationUnit not present in parse result"));
                cus.add(cu);
            } catch (Exception e) {
                throw new RuntimeException("In ProjectParser.parse: " + e);
            }
        }
        return cus;
    }
    public Map<String, String> findCodeByMethodInfo(String methodName, NodeList<CompilationUnit> cus) {
        Map<String, String> codes = new HashMap<>();
        try {

            // 调用 createSDG 方法
            SDG sdg = createSDG(cus);
            // 遍历 CompilationUnits 中的所有方法
            for (CompilationUnit cu : cus) {
                cu.findAll(CallableDeclaration.class).forEach(callable -> {
                    // 获取调用图中的边
                    Set<CallGraph.Edge<?>> edges = findEdgeByCallGraph(callable, sdg.getCallGraph());

                    edges.forEach(edge -> {
                        // 检查当前调用的源方法是否带有 @Test 注解
                        CallableDeclaration<?> caller = edge.getSource();
                        if (caller.isAnnotationPresent("Test")) {
                            // 检查调用的方法是否是指定的方法名
                            String calledMethodName = String.valueOf(edge.getTarget().getSignature());
                            if (calledMethodName != null && methodName.equals(calledMethodName)) {
                                // 获取调用位置和参数
                                Expression callSite = (Expression) edge.getCall();
                                int callSiteLine = callSite.getBegin().orElse(new Position(0, 0)).line;

                                List<String> arguments = createStringArgumets(callSite);
                                CompilationUnit callerCompilationUnit = findClassByCallable(caller);
                                String callerClassFullName = callerCompilationUnit.getType(0).getFullyQualifiedName().orElse(null);

                                if (callerClassFullName != null && !arguments.isEmpty()) {
                                    // 构造完整的测试类代码
                                    StringBuilder completeCode = new StringBuilder();

                                    // 添加包声明和导入语句
                                    callerCompilationUnit.getPackageDeclaration().ifPresent(packageDeclaration -> {
                                        completeCode.append(packageDeclaration).append("\n\n");
                                    });
                                    callerCompilationUnit.getImports().forEach(importDeclaration -> {
                                        completeCode.append(importDeclaration).append("\n");
                                    });

                                    // 获取类声明
                                    List<ClassOrInterfaceDeclaration> classes = callerCompilationUnit.findAll(ClassOrInterfaceDeclaration.class);
                                    Optional<ClassOrInterfaceDeclaration> containingClass = classes.stream()
                                            .filter(c -> c.getFullyQualifiedName().orElse("").equals(callerClassFullName))
                                            .findFirst();

                                    if (containingClass.isPresent()) {
                                        ClassOrInterfaceDeclaration testClass = containingClass.get();

                                        // 构造完整类代码
                                        completeCode.append("\n\n");
                                        completeCode.append(testClass.getAnnotations().stream()
                                                .map(Object::toString)
                                                .collect(Collectors.joining("\n"))).append("\n"); // 添加类注解
                                        completeCode.append("public class ").append(testClass.getNameAsString()).append(" {\n\n");

                                        // 添加测试方法代码
                                        completeCode.append(caller.toString()).append("\n");

                                        // 封闭类定义
                                        completeCode.append("}\n");

                                        // 存储完整代码到 Map
                                        codes.put(callerClassFullName, completeCode.toString());
                                    }
                                }
                            }
                        }
                    });
                });
            }
        } catch (Exception e) {
            config.getLogger().warn("Failed to read class code");
        }

        return codes;
    }


    public NodeList<CompilationUnit> addTestFiles(Path counterExamplePath) {
        NodeList<CompilationUnit> mergedUnits = new NodeList<>();

        // 添加已有的编译单元
        mergedUnits.addAll(this.compilationUnits);

        // 添加测试文件
        List<Path> testFiles = getFiles(counterExamplePath);
        for (Path file : testFiles) {
            try {
                if (Files.isRegularFile(file)) {
                    ParseResult<CompilationUnit> parseResult = parser.parse(file);
                    parseResult.getResult().ifPresent(mergedUnits::add);
                }
            } catch (IOException e) {
                config.getLogger().warn("Failed to parse test file: " + file);
            }
        }

        return mergedUnits;
    }
    private List<Path> getFiles(Path path) {
        List<Path> fileList = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
            for (Path entry : stream) {
                if (Files.isDirectory(entry)) {
                    fileList.addAll(getFiles(entry)); // 递归调用获取子目录中的文件
                } else {
                    fileList.add(entry);
                }
            }
        }catch (Exception e){
            config.logger.error("Failed to read class code for " + path);
        }
        return fileList;
    }
    private Set<CallGraph.Edge<?>> findEdgeByCallGraph(CallableDeclaration node, CallGraph callGraph) {
        CallGraph.Vertex ver = new CallGraph.Vertex(node);
        return callGraph.edgesOf(ver);
    }
    private SDG createSDG(NodeList<CompilationUnit> cus) {
        SDG sdg = new JSysDG();
        sdg.build(cus);
        return sdg;
    }
    private List<String> createStringArgumets(Expression callSite) {
        Set<String> arguments = new HashSet<>();
        if (!isPrimitiveOrString(callSite) && (callSite.isNameExpr() || callSite.isThisExpr())) {
            arguments.add(callSite.toString());
        } else if (callSite.isMethodCallExpr()) {
            callSite.asMethodCallExpr().getArguments().forEach(arg -> {
                arguments.addAll(createStringArgumets(arg));
            });
            if (callSite.hasScope()) {
                arguments.addAll(createStringArgumets(callSite.asMethodCallExpr().getScope().get()));
            }
        } else if (callSite.isObjectCreationExpr()) {
            callSite.asObjectCreationExpr().getArguments().forEach(arg -> {
                arguments.addAll(createStringArgumets(arg));
            });
            if (callSite.hasScope()) {
                arguments.addAll(createStringArgumets(callSite.asObjectCreationExpr().getScope().get()));
            }
        } else if (callSite.isFieldAccessExpr())  {
            arguments.addAll(createStringArgumets(callSite.asFieldAccessExpr().getNameAsExpression()));
            if (callSite.hasScope()) {
                arguments.addAll(createStringArgumets(callSite.asFieldAccessExpr().getScope()));
            }
        }
//        else {
//            throw new RuntimeException("Unsupported call site type: " + callSite.getClass().getSimpleName());
//        }
        return new ArrayList<>(arguments);
    }
    public static String getSignatureByCallable(CallableDeclaration<?> callable) {
        if (callable.isMethodDeclaration()) {
            return callable.asMethodDeclaration().resolve().getSignature();
        } else if (callable.isConstructorDeclaration()) {
            return callable.asConstructorDeclaration().resolve().getSignature();
        } else {
            throw new RuntimeException("Unsupported callable type: " + callable.getClass().getSimpleName());
        }
    }
    private CompilationUnit findClassByCallable(CallableDeclaration<?> node) {
        CompilationUnit cu = node.findAncestor(CompilationUnit.class)
                .orElseThrow(() -> new NoSuchElementException("No CompilationUnit ancestor found for node"));

        return cu;

    }
    private String findCodeBySlice(Slice slice, String callerClassFullName) {
        for (CompilationUnit cu : slice.toAst()) {
            if (!"".equals(cu.toString()) && cu.getType(0).getNameAsString().equals(callerClassFullName)) {
                return cu.toString();
            }
        }
        return null;
    }
    private boolean isPrimitiveOrString(Expression expr) {
        if (expr.isNameExpr()) {
            try {
                ResolvedType type = expr.asNameExpr().resolve().getType();
                if (type.isPrimitive()) {
                    return true;
                }
                if (type.isReferenceType() && type.asReferenceType().getQualifiedName().equals("java.lang.String")) {
                    return true;
                }
            } catch (UnsolvedSymbolException | IllegalStateException e) {
                return true; // ignore unsolved symbol
            }
        }
        return false;
    }
    public MethodExampleMap createMethodExampleMap(NodeList<CompilationUnit> cus) {
        config.getLogger().info("Starting to create method example map...");
        MethodExampleMap methodExampleMap = new MethodExampleMap();
        SDG sdg = createSDG(cus);

        AtomicInteger cuIndex = new AtomicInteger();
        AtomicInteger methodIndex = new AtomicInteger();
        int totalMethods = slicing.graphs.ClassGraph.getInstance().getMethodDeclarationMap().size();
        cus.forEach(cu -> {
            cu.findAll(CallableDeclaration.class).forEach(callable -> {
                Set<CallGraph.Edge<?>> edges = findEdgeByCallGraph(callable, sdg.getCallGraph());
                if (!edges.isEmpty()) {
                    config.getLogger().info("Processing method: [ " + callable.getNameAsString() + " ]" + " total: " + methodIndex.getAndIncrement() + " / " + totalMethods);
                    edges.forEach(edge -> {
                        config.getLogger().info("Processing edge: " + edge.getSource().getNameAsString() + " -> " + edge.getTarget().getNameAsString());
                        if (edge.getTarget().equals(callable)) {
                            if (! (edge.getCall() instanceof Expression)) {
                                return;
                            }
                            Expression callSite = (Expression) edge.getCall();
                            int callSiteLine = callSite.getBegin().orElse(new Position(0, 0)).line;
                            List<String> arguments = createStringArgumets(callSite);

                            CallableDeclaration<?> caller = edge.getSource();
                            CompilationUnit callerCompilationUnit = findClassByCallable(caller);
                            String callerClassFullName = callerCompilationUnit.getType(0).getFullyQualifiedName().get();

                            if (!arguments.isEmpty()) {
                                var sc = new MultiVariableCriterion(callerClassFullName, callSiteLine, arguments);
                                config.getLogger().info("Slicing method: " + getSignatureByCallable(callable) + " at callsite: < " + callSite + " >");
                                Slice slice = sdg.slice(sc);
                                if (!slice.toAst().isEmpty()) {
                                    String code = findCodeBySlice(slice, callerCompilationUnit.getType(0).getNameAsString());
                                    if (code != null) {
                                        methodExampleMap.add(getQualifiedSignatureByCallable(callable),
                                                callerClassFullName,
                                                getSignatureByCallable(caller),
                                                callSiteLine,
                                                code);
                                    }
                                }
                            }
                        }
                    });
                }
            });
        });

        return methodExampleMap;
    }
    public void exportMethodExampleMap(MethodExampleMap methodExampleMap) {
        Path savePath = config.tmpOutput.resolve("methodExampleCode.json");
        Map<String, TreeSet<MethodExampleMap.MEC>> mem = methodExampleMap.getMEM();
        exportJson(savePath, mem);
    }
    public void findBackwardAnalysis(MethodExampleMap methodExampleMap){

        Map<String, Set<List<MethodExampleMap.MEC>>> paths = new HashMap<>();
        methodExampleMap.getMEM().forEach((key, value) -> {
            for (MethodExampleMap.MEC methodExample : value) {
                List<MethodExampleMap.MEC> path = new ArrayList<>();
                path.add(methodExample);
                findPaths(key, methodExample, path, paths, methodExampleMap);
            }
        });
        // select ShortestPath
        filterShortestPaths(methodExampleMap,paths);
    }

    private void findPaths(String key, MethodExampleMap.MEC currentMethod, List<MethodExampleMap.MEC> currentPath,
                           Map<String, Set<List<MethodExampleMap.MEC>>> paths, MethodExampleMap methodExampleMap) {

        // Search For Current Method
        Set<MethodExampleMap.MEC> invokers = methodExampleMap.getMEM().get(currentMethod.getClassName() + "." + currentMethod.getMethodName());
        if (invokers == null || invokers.isEmpty()) {
            // If reach the top of the invocation,then stop,and add the currentpath to Paths
            paths.computeIfAbsent(key, k -> new HashSet<>()).add(new ArrayList<>(currentPath));
        } else {
            for (MethodExampleMap.MEC invoker : invokers) {
                String invokerKey = invoker.getClassName() + "." + invoker.getMethodName();
                currentPath.add(invoker);
                findPaths(key, invoker, currentPath, paths, methodExampleMap);
                currentPath.remove(currentPath.size() - 1);
            }
        }
    }

    private void filterShortestPaths(MethodExampleMap methodExampleMap,Map<String, Set<List<MethodExampleMap.MEC>>> allPaths) {
        Map<String, Set<List<MethodExampleMap.MEC>>> shortestPaths = new HashMap<>();
        for (Map.Entry<String, Set<List<MethodExampleMap.MEC>>> entry : allPaths.entrySet()) {
            String methodFullName = entry.getKey();
            Set<List<MethodExampleMap.MEC>> paths = entry.getValue();
            Map<String, List<List<MethodExampleMap.MEC>>> groupedByEndMethod = new HashMap<>();

            for (List<MethodExampleMap.MEC> path : paths) {
                if (!path.isEmpty()) {
                    String endMethodFullName = path.get(path.size() - 1).getClassName() + "." + path.get(path.size() - 1).getMethodName();
                    groupedByEndMethod.computeIfAbsent(endMethodFullName, k -> new ArrayList<>()).add(path);
                }
            }

            for (Map.Entry<String, List<List<MethodExampleMap.MEC>>> groupEntry : groupedByEndMethod.entrySet()) {
                List<List<MethodExampleMap.MEC>> sortedPaths = groupEntry.getValue();
                sortedPaths.sort(Comparator.comparingInt(List::size));

                int minLength = sortedPaths.isEmpty() ? 0 : sortedPaths.get(0).size();
                for (List<MethodExampleMap.MEC> path : sortedPaths) {
                    if (path.size() == minLength) {
                        methodExampleMap.addBackWardPath(methodFullName,path);
                    } else {
                        break;
                    }
                }
            }
        }
    }


    public void exportBackwardAnalysis(MethodExampleMap methodExampleMap) {
        Path savePath = config.tmpOutput.resolve("backwardAnalysis.json");
        exportJson(savePath, methodExampleMap.getMemList());
    }
    private String getQualifiedSignatureByCallable(CallableDeclaration<?> callable) {
        if (callable.isMethodDeclaration()) {
            MethodDeclaration md = callable.asMethodDeclaration();
            return md.resolve().getQualifiedSignature()
                    .replace(md.resolve().getSignature(), md.getSignature().asString());
        } else if (callable.isConstructorDeclaration()) {
            ConstructorDeclaration cd = callable.asConstructorDeclaration();
            return cd.resolve().getQualifiedSignature();
        } else {
            throw new RuntimeException("Unsupported callable type: " + callable.getClass().getSimpleName());
        }
    }
}
