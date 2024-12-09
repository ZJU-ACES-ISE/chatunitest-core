package zju.cst.aces.api.impl;

import zju.cst.aces.api.Runner;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.prompt.PromptFile;
import zju.cst.aces.runner.ClassRunner;
import zju.cst.aces.runner.MethodRunner;
import zju.cst.aces.runner.solution_runner.ChatTesterRunner;
import zju.cst.aces.runner.solution_runner.HITSRunner;

import java.io.IOException;

public class RunnerImpl implements Runner {
    Config config;

    public RunnerImpl(Config config) {
        this.config = config;
    }

    public void runClass(String fullClassName) {
        try {
            new ClassRunner(config, fullClassName).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void runMethod(String fullClassName, MethodInfo methodInfo) {
        try {
            selectRunner(config.getPhaseType(), fullClassName, methodInfo);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void selectRunner(String phaseType, String fullClassName, MethodInfo methodInfo) throws IOException {
        // Map templateName to a specific PromptFile enum constant
        switch (phaseType) {
            case "CHATTESTER":
                new ChatTesterRunner(config, fullClassName, methodInfo).start();
            case "HITS":
                new HITSRunner(config, fullClassName, methodInfo).start();
            default:
                new MethodRunner(config, fullClassName, methodInfo).start();
        }
    }
}
