package zju.cst.aces.api;

import okhttp3.Response;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.runner.MethodRunner;

import java.io.IOException;

import static zju.cst.aces.runner.AbstractRunner.*;
import static zju.cst.aces.api.ChatHelper.*;

public class Repair {

    Config config;

    Prompt prompt;

    public Repair(Config config, Prompt prompt) {
        this.config = config;
        this.prompt = prompt;
    }

    public String ruleBasedRepair(String code) {
        code = changeTestName(code, prompt.getTestName());
        code = repairPackage(code, prompt.getPromptInfo().getClassInfo().getPackageName());
        code = repairImports(code, prompt.getPromptInfo().getClassInfo().getImports());
        return code;
    }

    public String chatbotBasedRepair(String code, int rounds) throws IOException {
        PromptInfo promptInfo = prompt.getPromptInfo();
        promptInfo.setUnitTest(code);
        String fullClassName = promptInfo.getClassInfo().getPackageName() + "." + promptInfo.getClassInfo().getClassName();
        if (new MethodRunner(config, fullClassName, promptInfo.getMethodInfo())
                .runTest(prompt.getFullTestName(), promptInfo, rounds)) {
            config.getLog().info("Test for method < " + promptInfo.methodInfo.methodName + " > doesn't need repair");
            return code;
        }

        prompt.generate();

        if (prompt.isExceedMaxTokens()) {
            config.getLog().severe("Exceed max prompt tokens: " + promptInfo.methodInfo.methodName + " Skipped.");
            return code;
        }
        Response response = chat(config, prompt.getMessages());
        String newcode = extractCodeByResponse(response);
        if (newcode.isEmpty()) {
            config.getLog().warning("Test for method < " + promptInfo.methodInfo.methodName + " > extract code failed");
            return code;
        } else {
            return newcode;
        }
    }

    public String chatbotBasedRepair(String code) throws IOException {
        PromptInfo promptInfo = prompt.getPromptInfo();
        promptInfo.setUnitTest(code);
        String fullClassName = promptInfo.getClassInfo().getPackageName() + "." + promptInfo.getClassInfo().getClassName();

        if (new MethodRunner(config, fullClassName, promptInfo.getMethodInfo())
                .runTest(prompt.getFullTestName(), promptInfo, 0)) {
            config.getLog().info("Test for method < " + promptInfo.methodInfo.methodName + " > doesn't need repair");
            return code;
        }

        prompt.generate();

        if (prompt.isExceedMaxTokens()) {
            config.getLog().severe("Exceed max prompt tokens: " + promptInfo.methodInfo.methodName + " Skipped.");
            return code;
        }
        Response response = chat(config, prompt.getMessages());
        String newcode = extractCodeByResponse(response);
        if (newcode.isEmpty()) {
            config.getLog().warning("Test for method < " + promptInfo.methodInfo.methodName + " > extract code failed");
            return code;
        } else {
            return newcode;
        }
    }
}
