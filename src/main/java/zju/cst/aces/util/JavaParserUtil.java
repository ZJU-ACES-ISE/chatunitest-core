package zju.cst.aces.util;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
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

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

import static zju.cst.aces.parser.ProjectParser.config;

@Data
public class JavaParserUtil {
    public static JavaParser parser;
    public Path srcFolderPath;
    public static NodeList<CompilationUnit> cus;
    public static NodeList<CompilationUnit> cusWithTest;
    public JavaParserUtil(Config config){
        this.parser = config.getParser();
        this.srcFolderPath = Paths.get(config.getProject().getBasedir().getAbsolutePath(), "src", "main", "java");
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
            // 创建SDG
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
                            // 检查调用的方法是否是指定的 className
                            String calledMethodName = String.valueOf(edge.getTarget().getSignature());
                            if (calledMethodName != null && methodName.equals(calledMethodName)) {
                                Expression callSite = (Expression) edge.getCall();
                                int callSiteLine = callSite.getBegin().orElse(new Position(0, 0)).line;

                                List<String> arguments = createStringArgumets(callSite);
                                CompilationUnit callerCompilationUnit = findClassByCallable(caller);
                                String callerClassFullName = callerCompilationUnit.getType(0).getFullyQualifiedName().orElse(null);

                                if (callerClassFullName != null && !arguments.isEmpty()) {
                                    var sc = new MultiVariableCriterion(callerClassFullName, callSiteLine, arguments);
                                    Slice slice = sdg.slice(sc);
                                    if (!slice.toAst().isEmpty()) {
                                        // 提取 import 语句
                                        StringBuilder codeWithImports = new StringBuilder();
                                        callerCompilationUnit.getImports().forEach(importDeclaration -> {
                                            codeWithImports.append(importDeclaration).append("\n");
                                        });
                                        // 添加切片代码
                                        String code = findCodeBySlice(slice, callerCompilationUnit.getType(0).getNameAsString());
                                        if (code != null) {
                                            codeWithImports.append(code);
                                            codes.put(callerClassFullName, codeWithImports.toString());
                                        }
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

    public NodeList<CompilationUnit> addFilesToCompilationUnits(Path counterExamplePath) {
        List<Path> files = getFiles(counterExamplePath);
        NodeList<CompilationUnit> cusWithTest = new NodeList<>();
        for (CompilationUnit unit : cus) {
            cusWithTest.add(unit.clone()); // 使用 clone 方法进行深拷贝
        }
        for (Path file : files) {
            try {
                if (Files.isRegularFile(file)) {
                    ParseResult<CompilationUnit> parseResult = parser.parse(file);
                    CompilationUnit cu = parseResult.getResult().orElseThrow(() -> new NoSuchElementException("parse Failed"));
                    cusWithTest.add(cu);
                }
            } catch (IOException e) {
                config.getLogger().warn("Failed to parse file: " + file);
            }
        }
        return cusWithTest;
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
}
