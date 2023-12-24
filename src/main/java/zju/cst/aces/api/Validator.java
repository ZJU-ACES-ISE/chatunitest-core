package zju.cst.aces.api;

import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.dto.PromptInfo;

import java.nio.file.Path;

public interface Validator {

    boolean syntacticValidate(String code);
    boolean semanticValidate(String code, String className, Path outputPath, PromptInfo promptInfo);
    boolean runtimeValidate(String fullTestName);
    public boolean compile(String className, Path outputPath, PromptInfo promptInfo);
    public TestExecutionSummary execute(String fullTestName);
}
