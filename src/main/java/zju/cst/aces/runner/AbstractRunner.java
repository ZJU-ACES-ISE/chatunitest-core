package zju.cst.aces.runner;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.api.Task;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.*;
import zju.cst.aces.parser.ClassParser;
import zju.cst.aces.prompt.PromptGenerator;
import zju.cst.aces.util.CodeExtractor;
import zju.cst.aces.util.TestProcessor;
import zju.cst.aces.util.TokenCounter;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

public abstract class AbstractRunner {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public static final String separator = "_";
    public static int testTimeOut = 8000;
    public String className;
    public String fullClassName;
    public Config config;
    public PromptGenerator promptGenerator;
    // get configuration from Config, and move init() to Config
    public AbstractRunner(Config config, String fullClassname) throws IOException {
        fullClassName = fullClassname;
        className = fullClassname.substring(fullClassname.lastIndexOf(".") + 1);
        this.config = config;
        promptGenerator = new PromptGenerator(config);
    }

    abstract void start() throws IOException;

    public static String joinLines(List<String> lines) {
        return lines.stream().collect(Collectors.joining("\n"));
    }

    public static String filterAndJoinLines(List<String> lines, String filter) {
        return lines.stream()
                .filter(line -> !line.equals(filter))
                .collect(Collectors.joining("\n"));
    }

    public static String parseResponse(ChatResponse response) {
        if (response == null) {
            return "";
        }
        return response.getContent();
    }

    public static void exportTest(String code, Path savePath) {
        if (!savePath.toAbsolutePath().getParent().toFile().exists()) {
            savePath.toAbsolutePath().getParent().toFile().mkdirs();
        }
        //set charset utf-8
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(savePath.toFile()), StandardCharsets.UTF_8)) {
            writer.write(code);
        } catch (IOException e) {
            throw new RuntimeException("In AbstractRunner.exportTest: " + e);
        }
    }

    public String extractCode(String content) {
        try {
            return new CodeExtractor(content).getExtractedCode();
        } catch (Exception e) {
            config.getLogger().error("In AbstractRunner.extractCode: " + e);
        }
        return "";
    }

    public static String repairImports(String code, List<String> imports) {
        try {
            CompilationUnit cu = StaticJavaParser.parse(code);
            cu.addImport("org.mockito", false, true);
            cu.addImport("org.junit.jupiter.api", false, true);
            cu.addImport("org.mockito.Mockito", true, true);
            cu.addImport("org.junit.jupiter.api.Assertions", true, true);
            cu.addImport("org.junit.jupiter.api.extension.ExtendWith", false, false);
            cu.addImport("org.mockito.junit.jupiter.MockitoExtension", false, false);
            imports.forEach(i -> cu.addImport(i.replace("import ", "").replace(";", "")));
            return cu.toString();
        } catch (ParseProblemException e) {
            return code;
        }
    }

    public static String repairPackage(String code, String packageName) {
        CompilationUnit cu = StaticJavaParser.parse(code).setPackageDeclaration(packageName);
        return cu.toString();
    }

    public String addTimeout(String testCase, int timeout) {
        // Check JUnit version
        String junit4 = "import org.junit.Test";
        String junit5 = "import org.junit.jupiter.api.Test";
        if (testCase.contains(junit4)) {  // JUnit 4
            if (testCase.contains("@Test(timeout =")) {
                return testCase;
            }
            testCase = testCase.replace("@Test(", String.format("@Test(timeout = %d, ", timeout));
            return testCase.replace("@Test\n", String.format("@Test(timeout = %d)%n", timeout));
        } else if (testCase.contains(junit5)) {  // JUnit 5
            if (testCase.contains("import org.junit.jupiter.api.Timeout;")) {
                return testCase;
            }
            List<String> timeoutImport = new ArrayList<>();
            timeoutImport.add("import org.junit.jupiter.api.Timeout;");
            testCase = repairImports(testCase, timeoutImport);
            return testCase.replace("@Test\n", String.format("@Test%n    @Timeout(%d)%n", timeout));
        } else {
            config.getLogger().warn("Generated with unknown JUnit version, try without adding timeout.");
        }
        return testCase;
    }

    public static String changeTestName(String code, String newName) {
        CompilationUnit cu = StaticJavaParser.parse(code);
        cu.findFirst(ClassOrInterfaceDeclaration.class).ifPresent(c -> c.setName(newName));
        return cu.toString();
    }

    public static PromptInfo generatePromptInfoWithoutDep(Config config, ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        PromptInfo promptInfo = new PromptInfo(
                false,
                classInfo.fullClassName,
                methodInfo.methodName,
                methodInfo.methodSignature);
        promptInfo.setClassInfo(classInfo);
        promptInfo.setMethodInfo(methodInfo);
        String fields = joinLines(classInfo.fields);
        String methods = filterAndJoinLines(classInfo.methodsBrief, methodInfo.brief);
        String imports = joinLines(classInfo.imports);

        String information = classInfo.packageName
                + "\n" + imports
                + "\n" + classInfo.classSignature
                + " {\n";
//        if (methodInfo.useField) {
//            information += fields + "\n";
//        }
        information += fields + "\n";
        if (classInfo.hasConstructor) {
            information += joinLines(classInfo.constructorBrief) + "\n";
        }
        information += methodInfo.sourceCode + "\n}";

        promptInfo.setContext(information);
        promptInfo.setOtherMethodBrief(methods);

        String otherMethodBodies = "";
        for (String sig : classInfo.methodSigs.keySet()) {
            if (sig.equals(methodInfo.methodSignature)) {
                continue;
            }
            otherMethodBodies += getBody(config, classInfo, sig);
        }
        promptInfo.setOtherMethodBodies(otherMethodBodies);

        return promptInfo;
    }

    public static PromptInfo generatePromptInfoWithDep(Config config, ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        PromptInfo promptInfo = new PromptInfo(
                true,
                classInfo.fullClassName,
                methodInfo.methodName,
                methodInfo.methodSignature);
        promptInfo.setClassInfo(classInfo);
        promptInfo.setMethodInfo(methodInfo);
        List<String> otherBriefMethods = new ArrayList<>();
        List<String> otherMethodBodies = new ArrayList<>();

        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            Set<String> depMethods = entry.getValue();
            if (methodInfo.dependentMethods.containsKey(depClassName)) {
                continue;
            }
            promptInfo.addConstructorDeps(depClassName, getDepInfo(config, depClassName, depMethods));
        }

        for (Map.Entry<String, Set<String>> entry : methodInfo.dependentMethods.entrySet()) {
            String depClassName = entry.getKey();
            if (depClassName.equals(classInfo.getClassName())) {
                Set<String> otherSig = methodInfo.dependentMethods.get(depClassName);
                for (String otherMethod : otherSig) {
                    MethodInfo otherMethodInfo = getMethodInfo(config, classInfo, otherMethod);
                    if (otherMethodInfo == null) {
                        continue;
                    }
                    // only add the methods in focal class that are invoked
                    otherBriefMethods.add(otherMethodInfo.brief);
                    otherMethodBodies.add(otherMethodInfo.sourceCode);
                }
                continue;
            }

            Set<String> depMethods = entry.getValue();
            promptInfo.addMethodDeps(depClassName, getDepInfo(config, depClassName, depMethods));
            addMethodDepsByDepth(config, depClassName, depMethods, promptInfo, config.getDependencyDepth());
        }

        String fields = joinLines(classInfo.fields);
        String imports = joinLines(classInfo.imports);

        String information = classInfo.packageName
                + "\n" + imports
                + "\n" + classInfo.classSignature
                + " {\n";
        //TODO: handle used fields instead of all fields
        String otherMethods = "";
        String otherFullMethods = "";
        if (classInfo.hasConstructor) {
            otherMethods += joinLines(classInfo.constructorBrief) + "\n";
            otherFullMethods += getBodies(config, classInfo, classInfo.constructorSigs) + "\n";
        }
//        if (methodInfo.useField) {
//            information += fields + "\n";
//            otherMethods +=  joinLines(classInfo.getterSetterBrief) + "\n";
//            otherFullMethods += getBodies(config, classInfo, classInfo.getterSetterSigs) + "\n";
//        }
        information += fields + "\n";
        otherMethods +=  joinLines(classInfo.getterSetterBrief) + "\n";
        otherFullMethods += getBodies(config, classInfo, classInfo.getterSetterSigs) + "\n";

        otherMethods += joinLines(otherBriefMethods) + "\n";
        otherFullMethods += joinLines(otherMethodBodies) + "\n";
        information += methodInfo.sourceCode + "\n}";

        promptInfo.setContext(information);
        promptInfo.setOtherMethodBrief(otherMethods);
        promptInfo.setOtherMethodBodies(otherFullMethods);
        return promptInfo;
    }

    public static void addMethodDepsByDepth(Config config, String className, Set<String> methodSigs, PromptInfo promptInfo, int depth) throws IOException {
        if (depth <= 1) {
            return;
        }
        for (String dm : methodSigs) {
            ClassInfo depClassInfo = getClassInfo(config, className);
            if (depClassInfo == null) {
                continue;
            }
            addConstructorDepsByDepth(config, depClassInfo, promptInfo);

            MethodInfo depMethodInfo = getMethodInfo(config, depClassInfo, dm);
            if (depMethodInfo == null) {
                continue;
            }
            for (String depClassName : depMethodInfo.dependentMethods.keySet()) {
                Set<String> depMethods = depMethodInfo.dependentMethods.get(depClassName);
                promptInfo.addMethodDeps(depClassName, getDepInfo(config, depClassName, depMethods));
                addMethodDepsByDepth(config, depClassName, depMethods, promptInfo, depth - 1);
            }
        }
    }

    public static void addConstructorDepsByDepth(Config config, ClassInfo classInfo, PromptInfo promptInfo) throws IOException {
        for (Map.Entry<String, Set<String>> entry : classInfo.constructorDeps.entrySet()) {
            String depClassName = entry.getKey();
            Set<String> depMethods = entry.getValue();
            promptInfo.addConstructorDeps(depClassName, getDepInfo(config, depClassName, depMethods));
        }
    }

    public static ClassInfo getClassInfo(Config config, String className) throws IOException {
        try {
            String fullClassName = Task.getFullClassName(config, className);
            Path classInfoPath = config.getParseOutput().resolve(fullClassName.replace(".", File.separator)).resolve("class.json");
            if (!classInfoPath.toFile().exists()) {
                return null;
            }
            return GSON.fromJson(Files.readString(classInfoPath, StandardCharsets.UTF_8), ClassInfo.class);
        } catch (InvalidPathException e) {
            return null;
        }
    }

    public static MethodInfo getMethodInfo(Config config, ClassInfo info, String mSig) throws IOException {
        String packagePath = info.getPackageName()
                .replace("package ", "")
                .replace(".", File.separator)
                .replace(";", "");
        Path depMethodInfoPath = config.getParseOutput()
                .resolve(packagePath)
                .resolve(info.className)
                .resolve(ClassParser.getFilePathBySig(mSig, info));
        if (!depMethodInfoPath.toFile().exists()) {
            return null;
        }
        return GSON.fromJson(Files.readString(depMethodInfoPath, StandardCharsets.UTF_8), MethodInfo.class);
    }

    public static String getDepInfo(Config config, String depClassName, Set<String> depMethods) throws IOException {
        ClassInfo depClassInfo = getClassInfo(config, depClassName);
        if (depClassInfo == null) {
            return null;
        }

        String classSig = depClassInfo.classSignature;
        String fields = joinLines(depClassInfo.fields);

        String basicInfo = depClassInfo.packageName + "\n" + joinLines(depClassInfo.imports) + "\n"
                + classSig + " {\n" + fields + "\n";
        if (depClassInfo.hasConstructor) {
            String constructors = "";
            for (String sig : depClassInfo.constructorSigs) {
                MethodInfo depConstructorInfo = getMethodInfo(config, depClassInfo, sig);
                if (depConstructorInfo == null) {
                    continue;
                }
                constructors += depConstructorInfo.getSourceCode() + "\n";
            }

            basicInfo += constructors + "\n";
        }

        String sourceDepMethods = "";
        for (String sig : depMethods) {
            //TODO: identify used fields in dependent class
            MethodInfo depMethodInfo = getMethodInfo(config, depClassInfo, sig);
            if (depMethodInfo == null) {
                continue;
            }
            sourceDepMethods += depMethodInfo.getSourceCode() + "\n";
        }
        String getterSetter = joinLines(depClassInfo.getterSetterBrief) + "\n";
        return basicInfo + getterSetter + sourceDepMethods + "}";
    }

    public static String getBodies(Config config, ClassInfo info, List<String> sigs) throws IOException {
        String bodies = "";
        for (String sig : sigs) {
            bodies += getBody(config, info, sig) + "\n";
        }
        return bodies;
    }

    public static String getBody(Config config, ClassInfo info, String Sig) throws IOException {
        MethodInfo mi = getMethodInfo(config, info, Sig);
        return mi.sourceCode;
    }

    public void exportRecord(PromptInfo promptInfo, ClassInfo classInfo, int attempt) {
        String methodIndex = classInfo.methodSigs.get(promptInfo.methodSignature);
        Path recordPath = config.getHistoryPath();

        recordPath = recordPath.resolve("class" + classInfo.index);
        exportMethodMapping(classInfo, recordPath);

        recordPath = recordPath.resolve("method" + methodIndex);
        exportAttemptMapping(promptInfo, recordPath);

        recordPath = recordPath.resolve("attempt" + attempt);
        if (!recordPath.toFile().exists()) {
            recordPath.toFile().mkdirs();
        }
        File recordFile = recordPath.resolve("records.json").toFile();
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(recordFile), StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(promptInfo.getRecords()));
        } catch (IOException e) {
            throw new RuntimeException("In AbstractRunner.exportRecord: " + e);
        }
    }

    public static synchronized void exportClassMapping(Config config, Path savePath) {
        if (!savePath.toFile().exists()) {
            savePath.toFile().mkdirs();
        }
        File classMappingFile = savePath.resolve("classMapping.json").toFile();
        if (classMappingFile.exists()) {
            return;
        }
        Path sourcePath = config.tmpOutput.resolve("classMapping.json");
        try {
            Files.copy(sourcePath, classMappingFile.toPath());
        } catch (IOException e) {
            throw new RuntimeException("In AbstractRunner.exportClassMapping: " + e);
        }
    }

    public void exportMethodMapping(ClassInfo classInfo, Path savePath) {
        if (!savePath.toFile().exists()) {
            savePath.toFile().mkdirs();
        }
        File methodMappingFile = savePath.resolve("methodMapping.json").toFile();
        if (methodMappingFile.exists()) {
            return;
        }
        Map<String, Map<String, String>> methodMapping = new TreeMap<>();
        classInfo.methodSigs.forEach((sig, index) -> {
            Map<String, String> map = new LinkedHashMap<>();
            map.put("methodName", sig.split("\\(")[0]);
            map.put("signature", sig);
            map.put("className", classInfo.className);
            map.put("packageName", classInfo.packageName);
            methodMapping.put("method" + index, map);
        });
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(methodMappingFile), StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(methodMapping));
        } catch (IOException e) {
            throw new RuntimeException("In AbstractRunner.exportMethodMapping: " + e);
        }
    }

    public void exportAttemptMapping(PromptInfo promptInfo, Path savePath) {
        if (!savePath.toFile().exists()) {
            savePath.toFile().mkdirs();
        }
        File attemptMappingFile = savePath.resolve("attemptMapping.json").toFile();
        if (attemptMappingFile.exists()) {
            return;
        }
        Map<String, Map<String, String>> attemptMapping = new TreeMap<>();
        String fullNamePrefix = promptInfo.getFullTestName().substring(0, promptInfo.getFullTestName().indexOf("_Test") - 1);
        for (int i = 0; i < config.getTestNumber(); i++) {
            Map<String, String> map = new LinkedHashMap<>();
            String fullTestName = fullNamePrefix + i + "_Test";
            map.put("testClassName", fullTestName.substring(fullTestName.lastIndexOf(".") + 1));
            map.put("fullName", fullTestName);
            map.put("path", promptInfo.getTestPath().toString());
            map.put("className", promptInfo.className);
            map.put("packageName", promptInfo.classInfo.packageName);
            map.put("methodName", promptInfo.methodName);
            map.put("methodSig", promptInfo.methodSignature);
            attemptMapping.put("attempt" + i, map);
        }
        try (OutputStreamWriter writer = new OutputStreamWriter(
                new FileOutputStream(attemptMappingFile), StandardCharsets.UTF_8)) {
            writer.write(GSON.toJson(attemptMapping));
        } catch (IOException e) {
            throw new RuntimeException("In AbstractRunner.exportAttemptMapping: " + e);
        }
    }

    public static boolean isExceedMaxTokens(int maxPromptTokens, List<ChatMessage> prompt) {
        int count = 0;
        for (ChatMessage p : prompt) {
            count += TokenCounter.countToken(p.getContent());
        }
        if (count > maxPromptTokens) {
            return true;
        }
        return false;
    }

    public static boolean isExceedMaxTokens(int maxPromptTokens, String prompt) {
        int count = TokenCounter.countToken(prompt);
        if (count > maxPromptTokens) {
            return true;
        }
        return false;
    }

    /**
     * Compile and execute the test, if no errors, the test will be exported.
     * @param config
     * @param fullTestName
     * @param promptInfo
     * @param rounds
     * @return
     */
    public static boolean runTest(Config config, String fullTestName, PromptInfo promptInfo, int rounds) {
        String testName = fullTestName.substring(fullTestName.lastIndexOf(".") + 1);
        Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
        if (promptInfo.getTestPath() == null) {
            promptInfo.setTestPath(savePath);
        }

        TestProcessor testProcessor = new TestProcessor(fullTestName);
        String code = promptInfo.getUnitTest();
        if (rounds >= 1) {
            code = testProcessor.addCorrectTest(promptInfo);
        }

        // Compilation
        Path compilationErrorPath = config.getErrorOutput().resolve(testName + "_CompilationError_" + rounds + ".txt");
        Path executionErrorPath = config.getErrorOutput().resolve(testName + "_ExecutionError_" + rounds + ".txt");
        boolean compileResult = config.getValidator().semanticValidate(code, testName, compilationErrorPath, promptInfo);
        if (!compileResult) {
            config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() + " > compilation failed round " + rounds);
            return false;
        }
        if (config.isNoExecution()) {
            exportTest(code, savePath);
            config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() + " > generated successfully round " + rounds);
            return true;
        }

        // Execution
        TestExecutionSummary summary = config.getValidator().execute(fullTestName);

        List<String> errors = extractErrorBySummary(summary, fullTestName);
        if (summary.getTestsFailedCount() > 0 || summary.getTestsSucceededCount() == 0) {
            if (isOnlyAssertionError(errors)) {
                exportTest(code, savePath);
                config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() + " > compile and execute successfully with only assertion error, round " + rounds);
                return true;
            }

            String testProcessed = testProcessor.removeErrorTest(promptInfo, summary);

            // Remove errors successfully, recompile and re-execute test
            if (testProcessed != null) {
                config.getLogger().debug("[Original Test]:\n" + code);
                if (config.getValidator().semanticValidate(testProcessed, testName, compilationErrorPath, null)) {
                    if (config.getValidator().runtimeValidate(fullTestName)) {
                        exportTest(testProcessed, savePath);
                        config.getLogger().debug("[Processed Test]:\n" + testProcessed);
                        config.getLogger().info("Processed test for method < " + promptInfo.getMethodInfo().getMethodName() + " > generated successfully round " + rounds);
                        return true;
                    }
                }
                testProcessor.removeCorrectTest(promptInfo, summary);
            }

            // Set promptInfo error message
            TestMessage testMessage = new TestMessage();
            testMessage.setErrorType(TestMessage.ErrorType.RUNTIME_ERROR);
            testMessage.setErrorMessage(errors);
            promptInfo.setErrorMsg(testMessage);
            exportError(code, errors, executionErrorPath);
            testProcessor.removeCorrectTest(promptInfo, summary);
            config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() + " > execution failed round " + rounds);
            return false;
        }
//            summary.printTo(new PrintWriter(System.out));
        exportTest(code, savePath);
        config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() + " > compile and execute successfully round " + rounds);
        return true;
    }

    public static boolean isOnlyAssertionError(List<String> errors) {
        for (String error : errors) {
            if (!error.toLowerCase().contains("AssertionError".toLowerCase())
                    && !error.toLowerCase().contains("AssertionFailed".toLowerCase())) {
                return false;
            }
        }
        return true;
    }

    public static List<String> extractErrorBySummary(TestExecutionSummary summary, String matchName) {
        List<String> errors = new ArrayList<>();
        summary.getFailures().forEach(failure -> {
            for (StackTraceElement st : failure.getException().getStackTrace()) {
                if (st.getClassName().contains(matchName)) {
                    errors.add("Error in " + failure.getTestIdentifier().getLegacyReportingName()
                            + ": line " + st.getLineNumber() + " : "
                            + failure.getException().toString());
                }
            }
        });
        return errors;
    }

    public static void exportError(String code, List<String> errors, Path outputPath) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
            writer.write(code);
            writer.write("\n--------------------------------------------\n");
            writer.write(String.join("\n", errors));
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("In TestCompiler.exportError: " + e);
        }
    }
}
