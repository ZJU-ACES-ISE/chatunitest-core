package zju.cst.aces.api.impl;

import okhttp3.Response;
import zju.cst.aces.api.Repair;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.runner.MethodRunner;

import java.io.IOException;

import static zju.cst.aces.runner.AbstractRunner.*;
import static zju.cst.aces.api.impl.ChatGenerator.*;

public class RepairImpl implements Repair {

    Config config;

    PromptConstructorImpl promptConstructorImpl;

    public RepairImpl(Config config, PromptConstructorImpl promptConstructorImpl) {
        this.config = config;
        this.promptConstructorImpl = promptConstructorImpl;
    }

    @Override
    public String ruleBasedRepair(String code) {
        code = changeTestName(code, promptConstructorImpl.getTestName());
        code = repairPackage(code, promptConstructorImpl.getPromptInfo().getClassInfo().getPackageName());
        code = repairImports(code, promptConstructorImpl.getPromptInfo().getClassInfo().getImports());
        return code;
    }

    @Override
    public String LLMBasedRepair(String code, int rounds) {
        PromptInfo promptInfo = promptConstructorImpl.getPromptInfo();
        promptInfo.setUnitTest(code);
        String fullClassName = promptInfo.getClassInfo().getPackageName() + "." + promptInfo.getClassInfo().getClassName();
        try {
            if (new MethodRunner(config, fullClassName, promptInfo.getMethodInfo())
                    .runTest(promptConstructorImpl.getFullTestName(), promptInfo, rounds)) {
                config.getLog().info("Test for method < " + promptInfo.methodInfo.methodName + " > doesn't need repair");
                return code;
            }

            promptConstructorImpl.generate();
            if (promptConstructorImpl.isExceedMaxTokens()) {
                config.getLog().severe("Exceed max prompt tokens: " + promptInfo.methodInfo.methodName + " Skipped.");
                return code;
            }
            Response response = chat(config, promptConstructorImpl.getMessages());
            String newcode = extractCodeByResponse(response);
            if (newcode.isEmpty()) {
                config.getLog().warning("Test for method < " + promptInfo.methodInfo.methodName + " > extract code failed");
                return code;
            } else {
                return newcode;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String LLMBasedRepair(String code) {
        PromptInfo promptInfo = promptConstructorImpl.getPromptInfo();
        promptInfo.setUnitTest(code);
        String fullClassName = promptInfo.getClassInfo().getPackageName() + "." + promptInfo.getClassInfo().getClassName();
        try {
            if (new MethodRunner(config, fullClassName, promptInfo.getMethodInfo())
                    .runTest(promptConstructorImpl.getFullTestName(), promptInfo, 0)) {
                config.getLog().info("Test for method < " + promptInfo.methodInfo.methodName + " > doesn't need repair");
                return code;
            }

            promptConstructorImpl.generate();

            if (promptConstructorImpl.isExceedMaxTokens()) {
                config.getLog().severe("Exceed max prompt tokens: " + promptInfo.methodInfo.methodName + " Skipped.");
                return code;
            }
            Response response = chat(config, promptConstructorImpl.getMessages());
            String newcode = extractCodeByResponse(response);
            if (newcode.isEmpty()) {
                config.getLog().warning("Test for method < " + promptInfo.methodInfo.methodName + " > extract code failed");
                return code;
            } else {
                return newcode;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
