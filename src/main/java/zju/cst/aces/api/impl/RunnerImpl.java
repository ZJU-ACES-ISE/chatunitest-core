package zju.cst.aces.api.impl;

import zju.cst.aces.api.Runner;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.RoundRecord;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.runner.ClassRunner;
import zju.cst.aces.runner.MethodRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

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
            new MethodRunner(config, fullClassName, methodInfo).start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean runRound(String fullClassName, MethodInfo methodInfo, String fullTestName, int rounds, int num) throws IOException {
        ClassInfo classInfo = AbstractRunner.getClassInfo(config, fullClassName);

        ChatGenerator generator = new ChatGenerator(config);
        PromptConstructorImpl pc = new PromptConstructorImpl(config);
        RepairImpl repair = new RepairImpl(config, pc);

        if (methodInfo.dependentMethods.size() > 0) {
            pc.setPromptInfoWithDep(classInfo, methodInfo);
        } else {
            pc.setPromptInfoWithoutDep(classInfo, methodInfo);
        }
        pc.setFullTestName(fullTestName);

        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setFullTestName(fullTestName);
        Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
        promptInfo.setTestPath(savePath);

        promptInfo.addRecord(new RoundRecord(rounds));
        RoundRecord record = promptInfo.getRecords().get(rounds);
        record.setAttempt(num);
        MethodRunner mRunner = new MethodRunner(config, fullClassName, methodInfo);
        if (mRunner.generateTest(pc, repair, record)) {
            mRunner.exportRecord(pc.getPromptInfo(), classInfo, num);
            return true;
        }
        mRunner.exportRecord(pc.getPromptInfo(), classInfo, num);
        return false;
    }

    public boolean runRound(String fullClassName, MethodInfo methodInfo, String fullTestName) throws IOException {
        ClassInfo classInfo = AbstractRunner.getClassInfo(config, fullClassName);

        ChatGenerator generator = new ChatGenerator(config);
        PromptConstructorImpl pc = new PromptConstructorImpl(config);
        RepairImpl repair = new RepairImpl(config, pc);

        if (methodInfo.dependentMethods.size() > 0) {
            pc.setPromptInfoWithDep(classInfo, methodInfo);
        } else {
            pc.setPromptInfoWithoutDep(classInfo, methodInfo);
        }
        pc.setFullTestName(fullTestName);

        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setFullTestName(fullTestName);
        Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
        promptInfo.setTestPath(savePath);

        MethodRunner mRunner = new MethodRunner(config, fullClassName, methodInfo);
        return mRunner.generateTest(pc, repair);
    }
}
