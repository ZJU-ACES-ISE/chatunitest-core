package zju.cst.aces.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ParserConfiguration.LanguageLevel;
import com.github.javaparser.Position;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import slicing.graphs.CallGraph;
import slicing.graphs.CallGraph.Edge;
import slicing.graphs.CallGraph.Vertex;
import slicing.graphs.jsysdg.JSysDG;
import slicing.graphs.sdg.SDG;
import slicing.slicing.MultiVariableCriterion;
import slicing.slicing.Slice;
import zju.cst.aces.api.Project;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.MethodExampleMap;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class ProjectParser {

    public static JavaParser parser;
    public Path srcFolderPath;
    public Path outputPath;
    public Map<String, Set<String>> classNameMap = new HashMap<>();
    public static Config config;
    public int classCount = 0;
    public int methodCount = 0;

    public ProjectParser(Config config) {
        this.srcFolderPath = Paths.get(config.getProject().getBasedir().getAbsolutePath(), "src", "main", "java");
        this.config = config;
        this.outputPath = config.getParseOutput();
        this.parser = config.getParser();
    }

    /**
     * Parse the project.
     */
    public void parse() {
        List<String> classPaths = scanSourceDirectory(config.getProject());
        if (classPaths.isEmpty()) {
            config.getLogger().warn("No java file found in " + srcFolderPath);
            return;
        }
        NodeList<CompilationUnit> cus = new NodeList<>();
        for (String classPath : classPaths) {
            File file = new File(classPath);
            try {
                ParseResult<CompilationUnit> parseResult = parser.parse(file);
                CompilationUnit cu = parseResult.getResult().orElseThrow();
                cus.add(cu);
            } catch (Exception e) {
                throw new RuntimeException("In ProjectParser.parse: " + e);
            }
        }
        MethodExampleMap methodExampleMap = createMethodExampleMap(cus);

        for (var cu : cus) {
            try {
                Path output = outputPath;
                String packageName = "";
                if (cu.getPackageDeclaration().isPresent()) {
                    packageName = cu.getPackageDeclaration().get().getNameAsString();
                    output = outputPath.resolve(packageName.replace(".", File.separator));
                }
                ClassParser classParser = new ClassParser(parser, config.getProject(), output,
                        config.getLogger(),  config.getGSON(), config.sharedInteger, config.classMapping, config.ocm);
                int classNum = classParser.extractClass(cu);

                if (classNum == 0) {
                    continue;
                }
                addClassMap(cu);
                classCount += classNum;
                methodCount += classParser.methodCount;
            } catch (Exception e) {
                throw new RuntimeException("In ProjectParser.parse: " + e);
            }
        }
        exportClassMapping();
//        exportOCC();
        exportMethodExampleMap(methodExampleMap);
        exportJson(config.getClassNameMapPath(), classNameMap);
        config.getLogger().info("\nParsed classes: " + classCount + "\nParsed methods: " + methodCount);
    }

    private SDG createSDG(NodeList<CompilationUnit> cus) {
        SDG sdg = new JSysDG();
        sdg.build(cus);
        return sdg;
    }

    private MethodExampleMap createMethodExampleMap(NodeList<CompilationUnit> cus) {
        MethodExampleMap methodExampleMap = new MethodExampleMap();
        SDG sdg = createSDG(cus);

        cus.forEach(cu -> {
            cu.findAll(CallableDeclaration.class).forEach(callable -> {
                Set<Edge<?>> edges = findEdgeByCallGraph(callable, sdg.getCallGraph());
                if (!edges.isEmpty()) {
                    edges.forEach(edge -> {
                        if (edge.getTarget().equals(callable)) {
                            var callSite = (Expression) edge.getCall();
                            int callSiteLine = callSite.getBegin().orElse(new Position(0, 0)).line;
                            NodeList<Expression> arguments = new NodeList<>();
                            if (callSite.isMethodCallExpr()) {
                                arguments.addAll(callSite.asMethodCallExpr().getArguments());
                                if (callSite.hasScope()) {
                                    arguments.add(callSite.asMethodCallExpr().getScope().get());
                                }
                            } else if (callSite.isObjectCreationExpr()) {
                                arguments.addAll(callSite.asObjectCreationExpr().getArguments());
                                if (callSite.hasScope()) {
                                    arguments.add(callSite.asObjectCreationExpr().getScope().get());
                                }
                            } else {
                                throw new RuntimeException("Unsupported call site type: " + callSite.getClass().getSimpleName());
                            }

                            CallableDeclaration<?> caller = edge.getSource();
                            ClassOrInterfaceDeclaration callerClassDecl = findClassByCallable(caller);

                            if (arguments.isEmpty()) {
                                String callSiteExpr = callSite.toString();
                                if (callSite.findAncestor(ExpressionStmt.class).isPresent()) {
                                    callSiteExpr = callSite.findAncestor(ExpressionStmt.class).get().toString();
                                }
                                methodExampleMap.add(getQualifiedSignatureByCallable(callable),
                                        callerClassDecl.resolve().getQualifiedName(),
                                        getSignatureByCallable(caller),
                                        callSiteLine,
                                        callSiteExpr);
                            } else {
                                var sc = new MultiVariableCriterion(callerClassDecl.getFullyQualifiedName().get(), callSiteLine, arguments.stream().map(Expression::toString).collect(Collectors.toList()));
                                Slice slice = sdg.slice(sc);
                                if (!slice.toAst().isEmpty()) {
                                    methodExampleMap.add(getQualifiedSignatureByCallable(callable),
                                            callerClassDecl.resolve().getQualifiedName(),
                                            getSignatureByCallable(caller),
                                            callSiteLine,
                                            slice.toAst().getFirst().orElseThrow().toString());
                                }
                            }
                        }
                    });
                }
            });
        });
        return methodExampleMap;
    }

    private String getSignatureByCallable(CallableDeclaration<?> callable) {
        if (callable.isMethodDeclaration()) {
            return callable.asMethodDeclaration().resolve().getSignature();
        } else if (callable.isConstructorDeclaration()) {
            return callable.asConstructorDeclaration().resolve().getSignature();
        } else {
            throw new RuntimeException("Unsupported callable type: " + callable.getClass().getSimpleName());
        }
    }

    private String getQualifiedSignatureByCallable(CallableDeclaration<?> callable) {
        if (callable.isMethodDeclaration()) {
            return callable.asMethodDeclaration().resolve().getQualifiedSignature();
        } else if (callable.isConstructorDeclaration()) {
            return callable.asConstructorDeclaration().resolve().getQualifiedSignature();
        } else {
            throw new RuntimeException("Unsupported callable type: " + callable.getClass().getSimpleName());
        }
    }

    private ClassOrInterfaceDeclaration findClassByCallable(CallableDeclaration<?> node) {
        return node.findAncestor(ClassOrInterfaceDeclaration.class).orElseThrow();
    }

    private Set<Edge<?>> findEdgeByCallGraph(CallableDeclaration node, CallGraph callGraph) {
        Vertex ver = new Vertex(node);
        return callGraph.edgesOf(ver);
    }

    private List<CallableDeclaration<?>> findCallerByCallGraph(CallableDeclaration node, CallGraph callGraph) {
        List<CallableDeclaration<?>> callerList = new ArrayList<>();
        Vertex ver = new Vertex(node);
        callGraph.edgesOf(ver).forEach(edge -> {
            if (edge.getTarget().equals(node)) {
                System.out.println("find caller: " + edge.getSource());
                callerList.add(edge.getSource());
            }
        });
        return callerList;
    }

    public void addClassMap(CompilationUnit cu) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classNode -> {
            String className = classNode.getNameAsString();
            String fullClassName = cu.getPackageDeclaration().isPresent() ?
                    cu.getPackageDeclaration().get().getNameAsString() + "." + className : className;
            if (classNameMap.containsKey(className)) {
                classNameMap.get(className).add(fullClassName);
            } else {
                Set<String> fullClassNames = new HashSet<>();
                fullClassNames.add(fullClassName);
                classNameMap.put(className, fullClassNames);
            }
        });
    }

    public static void exportJson(Path path, Object obj) {
        if (!Files.exists(path.getParent())) {
            try {
                Files.createDirectories(path.getParent());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(new FileOutputStream(path.toFile()), StandardCharsets.UTF_8)){
            writer.write(config.getGSON().toJson(obj));
        } catch (Exception e) {
            throw new RuntimeException("In ProjectParser.exportJson: " + e);
        }
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

    public static void walkDep(DependencyNode node, Set<DependencyNode> depSet) {
        depSet.add(node);
        for (DependencyNode dep : node.getChildren()) {
            walkDep(dep, depSet);
        }
    }

    public void exportClassMapping() {
        Path savePath = config.tmpOutput.resolve("classMapping.json");
        exportJson(savePath, config.classMapping);
    }

    public void exportOCC() {
        Path savePath = config.tmpOutput.resolve("objectConstructionCode.json");
        exportJson(savePath, config.ocm.getOCM());
    }

    public void exportMethodExampleMap(MethodExampleMap methodExampleMap) {
        Path savePath = config.tmpOutput.resolve("methodExampleCode.json");
        exportJson(savePath, methodExampleMap.getMEM());
    }

    public static void setLanguageLevel(ParserConfiguration configuration) {
        int version = Runtime.version().feature();
//        int versionPrefix = Integer.parseInt(System.getProperty("java.version").split("\\.")[0]);
        switch (version) {
            case 8: // java 8
                configuration.setLanguageLevel(LanguageLevel.JAVA_8);
                break;
            case 9:
                configuration.setLanguageLevel(LanguageLevel.JAVA_9);
                break;
            case 10:
                configuration.setLanguageLevel(LanguageLevel.JAVA_10);
                break;
            case 11:
                configuration.setLanguageLevel(LanguageLevel.JAVA_11);
                break;
            case 12:
                configuration.setLanguageLevel(LanguageLevel.JAVA_12);
                break;
            case 13:
                configuration.setLanguageLevel(LanguageLevel.JAVA_13);
                break;
            case 14:
                configuration.setLanguageLevel(LanguageLevel.JAVA_14);
                break;
            case 15:
                configuration.setLanguageLevel(LanguageLevel.JAVA_15);
                break;
            case 16:
                configuration.setLanguageLevel(LanguageLevel.JAVA_16);
                break;
            default:
                configuration.setLanguageLevel(LanguageLevel.JAVA_17);
                break;
        }
    }
}
