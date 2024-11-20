package zju.cst.aces.util.chattester;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import lombok.Data;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.api.Validator;
import zju.cst.aces.dto.PromptInfo;

import java.nio.file.Path;
import java.util.List;


@Data
public class TesterValidator implements Validator {

    TesterCompiler compiler;

    public TesterValidator(Path testOutputPath, Path compileOutputPath, Path targetPath, List<String> classpathElements) {
        this.compiler = new TesterCompiler(testOutputPath, compileOutputPath, targetPath, classpathElements);
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
        return compiler.executeTest(fullTestName);
    }
}
