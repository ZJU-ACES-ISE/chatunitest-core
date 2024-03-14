package zju.cst.aces.api;
import jdk.jshell.spi.SPIResolutionException;
import lombok.AllArgsConstructor;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.Parser;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.RepairImpl;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.dto.*;
import zju.cst.aces.parser.ProjectParser;
import zju.cst.aces.prompt.PromptGenerator;
import zju.cst.aces.runner.MethodRunner;
import zju.cst.aces.util.CodeExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class Phase {
    Config config;
    public static final String separator = "_";

    public Phase(Config config) {
        this.config = config;
    }

    @AllArgsConstructor
    public class Preparation {

        public void execute() {
            Parser parser = new Parser(new ProjectParser(config), config.getProject(), config.getParseOutput(), config.getLogger());
            process(parser);
        }

        public void process(PreProcess preProcessor) {
            preProcessor.process();
        }
    }

    @AllArgsConstructor
    public class PromptGeneration {
        ClassInfo classInfo;
        MethodInfo methodInfo;

        public PromptConstructorImpl execute(int num) {
            String testName = classInfo.getClassName() + separator + methodInfo.methodName + separator
                    + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
            String fullTestName = classInfo.getFullClassName() + separator + methodInfo.methodName + separator
                    + classInfo.methodSigs.get(methodInfo.methodSignature) + separator + num + separator + "Test";
            config.getLogger().info(String.format("\n==========================\n[%s] Generating test for method < ",config.pluginSign)
                    + methodInfo.methodName + " > number " + num + "...\n");
            try {
                PromptConstructorImpl pc = new PromptConstructorImpl(config);
                if (!methodInfo.dependentMethods.isEmpty()) {
                    pc.setPromptInfoWithDep(classInfo, methodInfo);
                } else {
                    pc.setPromptInfoWithoutDep(classInfo, methodInfo);
                }
                pc.setFullTestName(fullTestName);
                pc.setTestName(testName);

                PromptInfo promptInfo = pc.getPromptInfo();
                promptInfo.setFullTestName(fullTestName);
                Path savePath = config.getTestOutput().resolve(fullTestName.replace(".", File.separator) + ".java");
                promptInfo.setTestPath(savePath);

                promptInfo.setTestNum(num);
                return pc;

            } catch (IOException e) {
                throw  new RuntimeException("In PromptGeneration.execute: " + e);
            }
        }
    }

    public class TestGeneration {

        PromptGenerator promptGenerator;
        int tokenCount = 0;
        MethodInfo methodInfo;
        ClassInfo classInfo;

        static final String separator = "_";

        public void setUp(PromptInfo promptInfo) throws IOException {
            this.promptGenerator = new PromptGenerator(config);
            this.methodInfo = promptInfo.getMethodInfo();
            this.classInfo = promptInfo.getClassInfo();
        }

        public String execute(PromptConstructorImpl pc) throws IOException {

            PromptInfo promptInfo = pc.getPromptInfo();
            if (promptGenerator == null) {
                setUp(promptInfo);
            }

            assert(promptInfo.getRound() != null);

            int rounds = promptInfo.getRound();
            promptInfo.addRecord(new RoundRecord(rounds));
            RoundRecord record = promptInfo.getRecords().get(rounds);
            record.setAttempt(promptInfo.getTestNum());

            if (rounds == 0) {
                config.getLogger().info("Generating test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
            } else {
                config.getLogger().info("Fixing test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
            }

            List<ChatMessage> prompt;
            Obfuscator obfuscator = new Obfuscator(config);
            if (config.isEnableObfuscate()) {
                PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
                obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);
                prompt = promptGenerator.generateMessages(obfuscatedPromptInfo);
            } else {
                prompt = promptGenerator.generateMessages(promptInfo);
            }

            String code = generateTest(prompt, record);
            if (!record.isHasCode()) {
                return "";
            }

            if (config.isEnableObfuscate()) {
                code = obfuscator.deobfuscateJava(code);
            }
            if (CodeExtractor.isTestMethod(code)) {
                TestSkeleton skeleton = new TestSkeleton(promptInfo); // test skeleton to wrap a test method
                code = skeleton.build(code);
            } else {
                RepairImpl repair = new RepairImpl(config, pc);
                code = repair.ruleBasedRepair(code);
            }
            promptInfo.setUnitTest(code);

            record.setCode(code);
            return code;
        }

        public String generateTest(List<ChatMessage> prompt, RoundRecord record) throws IOException {

            if (MethodRunner.isExceedMaxTokens(config.getMaxPromptTokens(), prompt)) {
                config.getLogger().error("Exceed max prompt tokens: " + methodInfo.methodName + " Skipped.");
                record.setPromptToken(-1);
                record.setHasCode(false);
                return "";
            }
            config.getLogger().debug("[Prompt]:\n" + prompt.toString());

            ChatResponse response = ChatGenerator.chat(config, prompt);
            String content = ChatGenerator.getContentByResponse(response);
            config.getLogger().debug("[Response]:\n" + content);
            String code = ChatGenerator.extractCodeByContent(content);

            record.setPromptToken(response.getUsage().getPromptTokens());
            record.setResponseToken(response.getUsage().getCompletionTokens());
            record.setPrompt(prompt);
            record.setResponse(content);
            if (code.isEmpty()) {
                config.getLogger().info("Test for method < " + methodInfo.methodName + " > extract code failed");
                record.setHasCode(false);
                return "";
            }
            record.setHasCode(true);
            return code;
        }
    }

    @AllArgsConstructor
    public class Validation {

        public boolean execute(PromptConstructorImpl pc) {

            RepairImpl repair = new RepairImpl(config, pc);
            PromptInfo promptInfo = pc.getPromptInfo();
            String code = promptInfo.getUnitTest();
            RoundRecord record = promptInfo.getRecords().get(promptInfo.getRound());
            repair.LLMBasedRepair(code, record.getRound());
            if (repair.isSuccess()) {
                record.setHasError(false);
                return true;
            }
            record.setHasError(true);
            record.setErrorMsg(promptInfo.getErrorMsg());
            return false;
        }
    }

    @AllArgsConstructor
    public class Repair {

    }

}
