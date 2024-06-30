package zju.cst.aces.api.impl;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import lombok.Data;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.api.Validator;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.util.TestCompiler;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Data
public class ValidatorImpl implements Validator {

    TestCompiler compiler;
    private static final int TIMEOUT = 1; // Timeout in minutes

    public ValidatorImpl(Path testOutputPath, Path compileOutputPath, Path targetPath, List<String> classpathElements) {
        this.compiler = new TestCompiler(testOutputPath, compileOutputPath, targetPath, classpathElements);
    }

    @Override
    public boolean syntacticValidate(String code) {
        try {
            StaticJavaParser.parse(code);
            return true;
        } catch (ParseProblemException e) {
            return false;
        }
    }

    @Override
    public boolean semanticValidate(String code, String className, Path outputPath, PromptInfo promptInfo) {
        compiler.setCode(code);
        return compiler.compileTest(className, outputPath, promptInfo);
    }

    @Override
    public boolean runtimeValidate(String fullTestName) {
        return compiler.executeTest(fullTestName).getTestsFailedCount() == 0;
    }

    @Override
    public boolean compile(String className, Path outputPath, PromptInfo promptInfo) {
        return compiler.compileTest(className, outputPath, promptInfo);
    }

    @Override
    public TestExecutionSummary execute(String fullTestName) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Callable<TestExecutionSummary> task = () -> compiler.executeTest(fullTestName);

        Future<TestExecutionSummary> future = executor.submit(task);
        try {
            return future.get(TIMEOUT, TimeUnit.MINUTES);
        } catch (TimeoutException e) {
            future.cancel(true);
            return null; // Timeout exceeded, return null
        } catch (InterruptedException | ExecutionException e) {
            e.printStackTrace();
            return null; // Exception occurred, return null
        } finally {
            executor.shutdown();
        }
    }
}
