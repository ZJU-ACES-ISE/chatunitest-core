package zju.cst.aces.runner;

import okhttp3.Response;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.dto.*;
import zju.cst.aces.util.AskGPT;
import zju.cst.aces.util.TestCompiler;
import zju.cst.aces.util.TestProcessor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MethodRunner extends ClassRunner {

    public MethodInfo methodInfo;

    public MethodRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName);
        this.methodInfo = methodInfo;
    }

    @Override
    public void start() throws IOException {
        if (!config.isStopWhenSuccess() && config.isEnableMultithreading()) {
            ExecutorService executor = Executors.newFixedThreadPool(config.getTestNumber());
            List<Future<String>> futures = new ArrayList<>();
            for (int num = 0; num < config.getTestNumber(); num++) {
                int finalNum = num;
                Callable<String> callable = new Callable<String>() {
                    @Override
                    public String call() throws Exception {
                        startRounds(finalNum);
                        return "";
                    }
                };
                Future<String> future = executor.submit(callable);
                futures.add(future);
            }
            Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    executor.shutdownNow();
                }
            });

            for (Future<String> future : futures) {
                try {
                    String result = future.get();
                    System.out.println(result);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            executor.shutdown();
        } else {
            for (int num = 0; num < config.getTestNumber(); num++) {
                if (startRounds(num) && config.isStopWhenSuccess()) {
                    break;
                }
            }
        }
    }

    public boolean startRounds(final int num) throws IOException {
        PromptInfo promptInfo = null;
        String testName = className + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
        String fullTestName = fullClassName + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
        config.getLog().info("\n==========================\n[ChatUniTest] Generating test for method < "
                + methodInfo.methodName + " > number " + num + "...\n");

        for (int rounds = 0; rounds < config.getMaxRounds(); rounds++) {
            if (promptInfo == null) {
                config.getLog().info("Generating test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
                if (methodInfo.dependentMethods.size() > 0) {
                    promptInfo = generatePromptInfoWithDep(config, classInfo, methodInfo);
                } else {
                    promptInfo = generatePromptInfoWithoutDep(config, classInfo, methodInfo);
                }
            } else {
                config.getLog().info("Fixing test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
            }
            promptInfo.setFullTestName(fullTestName);
            promptInfo.addRecord(new RoundRecord(rounds));
            RoundRecord record = promptInfo.getRecords().get(rounds);

            Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
            promptInfo.setTestPath(savePath);

//            TestSkeleton skeleton = new TestSkeleton(promptInfo); // test skeleton to wrap a test method
            Obfuscator obfuscator = new Obfuscator(config);
            PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
            if (config.isEnableObfuscate()) {
                obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);
            }

            List<Message> prompt = promptGenerator.generateMessages(obfuscatedPromptInfo);
            if (isExceedMaxTokens(config, prompt)) {
                config.getLog().severe("Exceed max prompt tokens: " + methodInfo.methodName + " Skipped.");
                break;
            }
            config.getLog().config("[Prompt]:\n" + prompt.toString());

            AskGPT askGPT = new AskGPT(config);
            Response response = askGPT.askChatGPT(prompt);

            String content = parseResponse(response);
            String code = extractCode(content);

//            code = skeleton.build(code);

            record.setPrompt(prompt);
            record.setResponse(content);
            if (code.isEmpty()) {
                config.getLog().info("Test for method < " + methodInfo.methodName + " > extract code failed");
                record.setHasCode(false);
                continue;
            }
            record.setHasCode(true);

            code = repairPackage(code, classInfo.packageName);
            if (config.isEnableObfuscate()) {
                code = obfuscator.deobfuscateJava(code);
            }
            code = changeTestName(code, testName);
//            code = addTimeout(code, testTimeOut);
            code = repairImports(code, classInfo.imports);
            promptInfo.setUnitTest(code); // Before repair imports

            record.setCode(code);
            if (runTest(fullTestName, promptInfo, rounds)) {
                record.setHasError(false);
                exportRecord(promptInfo, classInfo, num);
                return true;
            }
            record.setHasError(true);
            record.setErrorMsg(promptInfo.getErrorMsg());
        }

        exportRecord(promptInfo, classInfo, num);
        return false;
    }

    public boolean runTest(String fullTestName, PromptInfo promptInfo, int rounds) {
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
        TestCompiler compiler = new TestCompiler(config, code);
        Path compilationErrorPath = config.getErrorOutput().resolve(testName + "_CompilationError_" + rounds + ".txt");
        Path executionErrorPath = config.getErrorOutput().resolve(testName + "_ExecutionError_" + rounds + ".txt");
        boolean compileResult = compiler.compileTest(testName, compilationErrorPath, promptInfo);
        if (!compileResult) {
            config.getLog().info("Test for method < " + methodInfo.methodName + " > compilation failed round " + rounds);
            return false;
        }
        if (config.isNoExecution()) {
            exportTest(code, savePath);
            config.getLog().info("Test for method < " + methodInfo.methodName + " > generated successfully round " + rounds);
            return true;
        }

        // Execution
        TestExecutionSummary summary = compiler.executeTest(fullTestName);
        if (summary.getTestsFailedCount() > 0) {
            String testProcessed = testProcessor.removeErrorTest(promptInfo, summary);

            // Remove errors successfully, recompile and re-execute test
            if (testProcessed != null) {
                config.getLog().config("[Original Test]:\n" + code);
                TestCompiler newCompiler = new TestCompiler(config, testProcessed);
                if (newCompiler.compileTest(testName, compilationErrorPath, null)) {
                    TestExecutionSummary newSummary = newCompiler.executeTest(fullTestName);
                    if (newSummary.getTestsFailedCount() == 0) {
                        exportTest(testProcessed, savePath);
                        config.getLog().config("[Processed Test]:\n" + testProcessed);
                        config.getLog().info("Processed test for method < " + methodInfo.methodName + " > generated successfully round " + rounds);
                        return true;
                    }
                }
                testProcessor.removeCorrectTest(promptInfo, summary);
            }

            // Set promptInfo error message
            // TODO: should be a function invoked by each return statement
            TestMessage testMessage = new TestMessage();
            List<String> errors = new ArrayList<>();
            summary.getFailures().forEach(failure -> {
                for (StackTraceElement st : failure.getException().getStackTrace()) {
                    if (st.getClassName().contains(fullTestName)) {
                        errors.add("Error in " + failure.getTestIdentifier().getLegacyReportingName()
                                + ": line " + st.getLineNumber() + " : "
                                + failure.getException().toString());
                    }
                }
            });
            testMessage.setErrorType(TestMessage.ErrorType.RUNTIME_ERROR);
            testMessage.setErrorMessage(errors);
            promptInfo.setErrorMsg(testMessage);
            compiler.exportError(errors, executionErrorPath);
            testProcessor.removeCorrectTest(promptInfo, summary);
            config.getLog().info("Test for method < " + methodInfo.methodName + " > execution failed round " + rounds);
            return false;
        }
//            summary.printTo(new PrintWriter(System.out));
        exportTest(code, savePath);
        config.getLog().info("Test for method < " + methodInfo.methodName + " > compile and execute successfully round " + rounds);
        return true;
    }
}