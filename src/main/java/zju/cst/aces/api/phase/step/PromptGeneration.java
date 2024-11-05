package zju.cst.aces.api.phase.step;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

public class PromptGeneration {
    private final Config config;
    private final ClassInfo classInfo;
    private final MethodInfo methodInfo;
    private static final String separator = "_";

    public PromptGeneration(Config config, ClassInfo classInfo, MethodInfo methodInfo) {
        this.config = config;
        this.classInfo = classInfo;
        this.methodInfo = methodInfo;
    }

    public PromptConstructorImpl execute(int num) {
        String testName = classInfo.getClassName() + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
        String fullTestName = classInfo.getFullClassName() + separator + methodInfo.methodName + separator
                + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
        config.getLogger().info(String.format("\n==========================\n[%s] Generating test for method < ",
                config.pluginSign) + methodInfo.methodName + " > number " + num + "...\n");

        try {
            PromptConstructorImpl pc = new PromptConstructorImpl(config);
            if (!methodInfo.dependentMethods.isEmpty()) {
                pc.setPromptInfoWithDep(classInfo, methodInfo);
            } else {
                pc.setPromptInfoWithoutDep(classInfo, methodInfo);            }
            pc.setFullTestName(fullTestName);
            pc.setTestName(testName);

            PromptInfo promptInfo = pc.getPromptInfo();
            promptInfo.setFullTestName(fullTestName);
            Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
            promptInfo.setTestPath(savePath);

            promptInfo.setTestNum(num);
            return pc;

        } catch (IOException e) {
            throw new RuntimeException("In PromptGeneration.execute: " + e);
        }
    }
}
