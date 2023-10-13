package zju.cst.aces.api;

import lombok.Data;
import zju.cst.aces.config.Config;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.runner.AbstractRunner;

import java.io.IOException;

@Data
public class Prompt {

    Config config;
    PromptInfo promptInfo;
    String testName;
    String fullTestName;
    static final String separator = "_";

    public Prompt(Config config) {
        this.config = config;
    }

    public void setPromptInfoWithDep(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        this.promptInfo = AbstractRunner.generatePromptInfoWithDep(config, classInfo, methodInfo);
    }

    public void setPromptInfoWithoutDep(ClassInfo classInfo, MethodInfo methodInfo) throws IOException {
        this.promptInfo = AbstractRunner.generatePromptInfoWithoutDep(config, classInfo, methodInfo);
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public void setFullTestName(String fullTestName) {
        this.fullTestName = fullTestName;
        this.promptInfo.setFullTestName(this.fullTestName);
    }
}
