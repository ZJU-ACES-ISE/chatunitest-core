package zju.cst.aces.coverage;

import org.jacoco.core.analysis.*;
import org.jacoco.core.data.ExecutionDataStore;
import org.jacoco.core.data.SessionInfoStore;
import org.jacoco.core.instr.Instrumenter;
import org.jacoco.core.runtime.IRuntime;
import org.jacoco.core.runtime.LoggerRuntime;
import org.jacoco.core.runtime.RuntimeData;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.objectweb.asm.Type;

import javax.tools.*;
import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * 调用analyzeCoverage方法，传入测试类的源代码、目标类名和方法签名，即可获取方法的覆盖率信息
 */
public class CodeCoverageAnalyzer {

    public static class MemoryClassLoader extends URLClassLoader {
        private final Map<String, byte[]> definitions = new HashMap<>();

        public MemoryClassLoader(URL[] urls, ClassLoader parent) {
            super(urls, parent); // 显式传递父类加载器
        }

        public void addDefinition(final String name, final byte[] bytes) {
            definitions.put(name, bytes);
        }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            // 优先由父类加载JUnit相关类
            if (name.startsWith("org.junit") || name.startsWith("org.opentest4j") || name.startsWith("org.apiguardian")) {
                return super.loadClass(name);
            }
            if (definitions.containsKey(name)) {
                return findClass(name);
            }
            return super.loadClass(name);
        }

        @Override
        protected Class<?> findClass(final String name) throws ClassNotFoundException {
            byte[] bytes = definitions.get(name);
            if (bytes != null) {
                return defineClass(name, bytes, 0, bytes.length);
            }
            return super.findClass(name);
        }
    }

    /**
     *  分析测试类的覆盖率
     * @param testSourceCode  测试类的源代码
     * @param targetTestName 测试类的全限定名
     * @param targetClassName 目标类的全类名
     * @param methodSignature 目标方法签名
     * @param targetClassCompiledDir 目标类的编译目录
     * @param targetClassSourceDir 目标类相关的源代码目录
     * @param dependencies 依赖的jar包路径
     * @return
     * @throws Exception
     */
    public Map<String, Object> analyzeCoverage(String testSourceCode, String targetTestName, String targetClassName, String methodSignature, String targetClassCompiledDir, String targetClassSourceDir, List<String> dependencies) throws Exception {

        final IRuntime runtime = new LoggerRuntime();
        final Instrumenter instr = new Instrumenter(runtime);

        byte[] instrumentedTest = compileAndInstrument(testSourceCode, targetTestName, instr, targetClassCompiledDir, dependencies);
        byte[] instrumentedClass = instrumentTargetClass(targetClassName, targetClassCompiledDir, instr);

        final RuntimeData data = new RuntimeData();
        runtime.startup(data);

        URL[] urls = dependencies.stream().map(File::new).map(file -> {
            try {
                return file.toURI().toURL();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }).toArray(URL[]::new);
        // 确保目标类编译目录在依赖列表中

    // 修改此行代码，传入当前上下文类加载器作为父类
        MemoryClassLoader memoryClassLoader = new MemoryClassLoader(urls, Thread.currentThread().getContextClassLoader());
        memoryClassLoader.addDefinition(targetTestName, instrumentedTest);
        memoryClassLoader.addDefinition(targetClassName, instrumentedClass);

        Class<?> testClass = memoryClassLoader.loadClass(targetTestName);

        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(DiscoverySelectors.selectClass(testClass))
                .build();
        // 不需要显式创建 TestEngine，使用默认配置即可
        Launcher launcher = LauncherFactory.create();  // 默认会自动注册 JUnit Jupiter 引擎
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);


        TestExecutionSummary summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out));

        final ExecutionDataStore executionData = new ExecutionDataStore();
        final SessionInfoStore sessionInfos = new SessionInfoStore();
        data.collect(executionData, sessionInfos, false);
        runtime.shutdown();

        final CoverageBuilder coverageBuilder = new CoverageBuilder();
        final Analyzer analyzer = new Analyzer(executionData, coverageBuilder);
        InputStream originalClass = getTargetClass(targetClassName, targetClassCompiledDir);
        analyzer.analyzeClass(originalClass, targetClassName);
        originalClass.close();

        return getMethodCoverageInfo(coverageBuilder, targetClassName, methodSignature, targetClassSourceDir);
    }

    private byte[] compileAndInstrument(String sourceCode, String className, Instrumenter instr, String targetClassCompiledDir, List<String> dependencies) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

        File outputDir = new File("compiled_classes");
        if (!outputDir.exists()) {
            outputDir.mkdirs();
        }

        List<File> classPath = new ArrayList<>();
        classPath.add(new File(targetClassCompiledDir));
        for (String dependency : dependencies) {
            classPath.add(new File(dependency));
        }
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT,Arrays.asList(outputDir));
        fileManager.setLocation(StandardLocation.CLASS_PATH, classPath);

        JavaFileObject file = new JavaSourceFromString(className, sourceCode);
        Iterable<? extends JavaFileObject> compilationUnits =Arrays.asList(file);
        compiler.getTask(null, fileManager, null, null, null, compilationUnits).call();

        File compiledFile = new File(outputDir, className.replace('.', '/') + ".class");
        InputStream compiledClass = new FileInputStream(compiledFile);
        byte[] instrumentedClass = instr.instrument(compiledClass, className);
        compiledClass.close();
        // 删除编译后的 .class 文件
        if (compiledFile.exists()) {
            compiledFile.delete();
        }
        return instrumentedClass;
    }

    private byte[] instrumentTargetClass(final String targetClassName, final String targetClassCompiledDir, Instrumenter instr) throws IOException {
        File compiledFile = new File(targetClassCompiledDir, targetClassName.replace('.', '/') + ".class");
        InputStream compiledClass = new FileInputStream(compiledFile);
        byte[] instrumentedClass = instr.instrument(compiledClass, targetClassName);
        compiledClass.close();
        return instrumentedClass;
    }

    private InputStream getTargetClass(final String name, final String compiledDir) throws IOException {
        File compiledFile = new File(compiledDir, name.replace('.', '/') + ".class");
        return new FileInputStream(compiledFile);
    }

    private Map<String, Object> getMethodCoverageInfo(final CoverageBuilder coverageBuilder, final String className, final String methodSignature, final String sourceDir) throws IOException {
        Map<String, Object> resultMap = new HashMap<>();
        List<Integer> uncoveredLines = new ArrayList<>();
        StringBuilder methodCode = new StringBuilder();

        for (final IClassCoverage cc : coverageBuilder.getClasses()) {
            if (cc.getName().replace("/", ".").equals(className)) {
                for (IMethodCoverage mc : cc.getMethods()) {
                    String methodSig = getMethodSignature(mc.getName(), mc.getDesc());
                    //方法签名，对应class-info的"methodSignature"
                    if (methodSig.equals(methodSignature)) {
                        resultMap.put("instructionCoverage", getCoveragePercentage(mc.getInstructionCounter()));
                        resultMap.put("branchCoverage", getCoveragePercentage(mc.getBranchCounter()));
                        resultMap.put("lineCoverage", getCoveragePercentage(mc.getLineCounter()));

                        String sourceFileName = sourceDir + File.separator + className.replace('.', File.separatorChar) + ".java";
                        List<String> sourceLines = Files.readAllLines(Paths.get(sourceFileName));

                        boolean insideMethod = false;
                        int openBraces = 0;
                        for (int i = 0; i < sourceLines.size(); i++) {
                            String line = sourceLines.get(i);
                            if (mc.getFirstLine() - 2 <= i + 1 && i + 1 <= mc.getLastLine() + 2) {
                                if (mc.getLine(i + 1).getStatus() == ICounter.NOT_COVERED) {
                                    methodCode.append(String.format("%s //--- Uncovered line: %d ---%n", line, i + 1));
                                    uncoveredLines.add(i + 1);
                                } else {
                                    methodCode.append(line).append("\n");
                                }
                            }
                        }
                    }
                }
            }
        }

        resultMap.put("methodCode", methodCode.toString());
        resultMap.put("uncoveredLines", formatUncoveredLines(uncoveredLines));
        return resultMap;
    }

    private double getCoveragePercentage(ICounter counter) {
        if (counter.getTotalCount() == 0) {
            return 100.0;
        }
        return 100.0 * counter.getCoveredCount() / counter.getTotalCount();
    }

    private String getMethodSignature(String methodName, String methodDesc) {
        return methodName + parseMethodDescriptor(methodDesc);
    }

    public static String parseMethodDescriptor(String descriptor) {
        Type[] argumentTypes = Type.getArgumentTypes(descriptor);
        StringBuilder result = new StringBuilder("(");
        for (int i = 0; i < argumentTypes.length; i++) {
            Type argumentType = argumentTypes[i];
            String typeName = argumentType.getClassName();
            int lastDotIndex = typeName.lastIndexOf('.');
            if (lastDotIndex != -1) {
                typeName = typeName.substring(lastDotIndex + 1);
            }
            result.append(typeName);

            if (i < argumentTypes.length - 1) {
                result.append(", ");
            }
        }

        result.append(")");
        return result.toString();
    }

    private int countOccurrences(String haystack, char needle) {
        int count = 0;
        for (char c : haystack.toCharArray()) {
            if (c == needle) {
                count++;
            }
        }
        return count;
    }

    private List<String> formatUncoveredLines(List<Integer> uncoveredLines) {
        List<String> result = new ArrayList<>();
        if (uncoveredLines.isEmpty()) {
            return result;
        }
        int start = uncoveredLines.get(0);
        int end = start;

        for (int i = 1; i < uncoveredLines.size(); i++) {
            if (uncoveredLines.get(i) == end + 1) {
                end = uncoveredLines.get(i);
            } else {
                if (start == end) {
                    result.add(String.valueOf(start));
                } else {
                    result.add(start + "-" + end);
                }
                start = uncoveredLines.get(i);
                end = start;
            }
        }

        if (start == end) {
            result.add(String.valueOf(start));
        } else {
            result.add(start + "-" + end);
        }

        return result;
    }

    public static void main(final String[] args) throws Exception {

        String testSourceCode = "/*\n" +
                " * This file was automatically generated by SmartUt\n" +
                " * Tue Nov 26 16:13:43 GMT 2024\n" +
                " */\n" +
                "\n" +
                "package myTest;\n" +
                "\n" +
                "import org.junit.Test;\n" +
                "import static org.junit.Assert.*;\n" +
                "public class TeplaB_SSTest  {\n" +
                "\n" +
                "    @Test(timeout = 4000)\n" +
                "    public void test_getA_0()  throws Throwable  {\n" +
                "        TeplaB teplaB0 = new TeplaB();\n" +
                "        teplaB0.init();\n" +
                "        int int0 = teplaB0.init();\n" +
                "        teplaB0.teplaB();\n" +
                "        boolean boolean0 = teplaB0.isTrue((-135));\n" +
                "        assertFalse(boolean0);\n" +
                "\n" +
                "        boolean boolean1 = teplaB0.isTrue(1);\n" +
                "        assertTrue(boolean1);\n" +
                "\n" +
                "        int int1 = teplaB0.getA();\n" +
                "        assertFalse(int1 == int0);\n" +
                "    }\n" +
                "}\n" +
                "\n";

        String targetClassName = "myTest.Tepla";
        String methodSignature = "calculate()";
        String targetClassCompiledDir = "D:\\ZJUtest\\target\\classes"; // 目标类的编译目录
        String targetClassSourceDir = "D:\\ZJUtest\\src\\main\\java"; // 目标类的源代码目录

        List<String> dependencies = Arrays.asList(
                "D:\\ZJUtest\\target\\ZJUtest-1.0-SNAPSHOT.jar",
                "D:\\ZJUtest\\target\\classes",
                "D:\\maven-repository\\org\\apiguardian\\apiguardian-api\\1.1.2\\apiguardian-api-1.1.2.jar",
                "D:\\maven-repository\\org\\jacoco\\org.jacoco.core\\0.8.8\\org.jacoco.core-0.8.8.jar",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-suite-commons\\1.9.2\\junit-platform-suite-commons-1.9.2.jar",
                "D:\\maven-repository\\org\\junit\\vintage\\junit-vintage-engine\\5.9.2\\junit-vintage-engine-5.9.2.jar",
                "D:\\maven-repository\\org\\opentest4j\\opentest4j\\1.2.0\\opentest4j-1.2.0.jar",
                "D:\\maven-repository\\org\\objenesis\\objenesis\\3.1\\objenesis-3.1.jar",
                "D:\\maven-repository\\io\\github\\ZJU-ACES-ISE\\chatunitest-starter\\1.4.0\\chatunitest-starter-1.4.0.pom",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-suite-api\\1.9.2\\junit-platform-suite-api-1.9.2.jar",
                "D:\\maven-repository\\org\\jacoco\\org.jacoco.report\\0.8.8\\org.jacoco.report-0.8.8.jar",
                "D:\\maven-repository\\net\\bytebuddy\\byte-buddy\\1.10.20\\byte-buddy-1.10.20.jar",
                "D:\\maven-repository\\org\\mockito\\mockito-core\\3.8.0\\mockito-core-3.8.0.jar",
                "D:\\maven-repository\\org\\ow2\\asm\\asm-commons\\9.2\\asm-commons-9.2.jar",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-engine\\1.9.2\\junit-platform-engine-1.9.2.jar",
                "D:\\maven-repository\\org\\ow2\\asm\\asm\\9.2\\asm-9.2.jar",
                "D:\\maven-repository\\org\\ow2\\asm\\asm-tree\\9.2\\asm-tree-9.2.jar",
                "D:\\maven-repository\\junit\\junit\\4.13.2\\junit-4.13.2.jar",
                "D:\\maven-repository\\org\\hamcrest\\hamcrest-core\\1.3\\hamcrest-core-1.3.jar",
                "D:\\maven-repository\\org\\junit\\jupiter\\junit-jupiter-params\\5.9.2\\junit-jupiter-params-5.9.2.jar",
                "D:\\maven-repository\\net\\bytebuddy\\byte-buddy-agent\\1.10.20\\byte-buddy-agent-1.10.20.jar",
                "D:\\maven-repository\\org\\junit\\jupiter\\junit-jupiter-engine\\5.9.2\\junit-jupiter-engine-5.9.2.jar",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-commons\\1.9.2\\junit-platform-commons-1.9.2.jar",
                "D:\\maven-repository\\org\\mockito\\mockito-junit-jupiter\\3.8.0\\mockito-junit-jupiter-3.8.0.jar",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-launcher\\1.9.2\\junit-platform-launcher-1.9.2.jar",
                "D:\\maven-repository\\org\\junit\\jupiter\\junit-jupiter-api\\5.9.2\\junit-jupiter-api-5.9.2.jar",
                "D:\\maven-repository\\org\\junit\\platform\\junit-platform-runner\\1.9.2\\junit-platform-runner-1.9.2.jar",
                "D:\\maven-repository\\org\\ow2\\asm\\asm-analysis\\9.2\\asm-analysis-9.2.jar"

        );
        String targetTestName = "myTest.TeplaB_SSTest";
        System.out.println(targetTestName);
        Map<String, Object> coverageInfo = new CodeCoverageAnalyzer().analyzeCoverage(testSourceCode,targetTestName, targetClassName, methodSignature, targetClassCompiledDir, targetClassSourceDir, dependencies);
        System.out.println("Coverage Information:");
        System.out.println("Instruction Coverage: " + coverageInfo.get("instructionCoverage"));
        System.out.println("Branch Coverage: " + coverageInfo.get("branchCoverage"));
        System.out.println("Line Coverage: " + coverageInfo.get("lineCoverage"));
        System.out.println("Method Code:\n" + coverageInfo.get("methodCode"));
        System.out.println("Uncovered Lines: " + coverageInfo.get("uncoveredLines"));
    }



    static class JavaSourceFromString extends SimpleJavaFileObject {
        final String code;

        JavaSourceFromString(String name, String code) {
            super(URI.create("string:///" + name.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }
}
