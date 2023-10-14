package zju.cst.aces.api;

import lombok.Data;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.config.Config;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.util.TestCompiler;
import static zju.cst.aces.runner.AbstractRunner.*;

import java.nio.file.Path;

@Data
public class Validator {

    Config config;

    TestCompiler compiler;

    public Validator(Config config) {
        this.compiler = new TestCompiler(config);
    }

    public String ruleBasedRepair(String code, String testName, ClassInfo classInfo) {
        code = changeTestName(code, testName);
        code = repairPackage(code, classInfo.packageName);
        code = repairImports(code, classInfo.imports, config.enableRuleRepair);
        return code;
    }

    public boolean compile(String className, Path outputPath, PromptInfo promptInfo) {
        return compiler.compileTest(className, outputPath, promptInfo);
    }

    public TestExecutionSummary execute(String fullTestName) {
        return compiler.executeTest(fullTestName);
    }
}
