package zju.cst.aces.api;

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

import static zju.cst.aces.runner.AbstractRunner.runTest;

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
        //  目前就是使用Parser类进行预处理
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
        MethodInfo methodInfo;
        ClassInfo classInfo;


        public void setUp(PromptInfo promptInfo) {
            this.promptGenerator = new PromptGenerator(config);
            this.methodInfo = promptInfo.getMethodInfo();
            this.classInfo = promptInfo.getClassInfo();
        }

        public void execute(PromptConstructorImpl pc) {

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
            String code;
            if (config.isEnableObfuscate()) {
                Obfuscator obfuscator = new Obfuscator(config);
                PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
                obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);
                prompt = promptGenerator.generateMessages(obfuscatedPromptInfo);
                code = generateTest(prompt, record);
                if (!record.isHasCode()) {
                    promptInfo.setUnitTest("");
                    return;
                }
                code = obfuscator.deobfuscateJava(code);
            } else {
                prompt = promptGenerator.generateMessages(promptInfo);
                code = generateTest(prompt, record);
                if (!record.isHasCode()) {
                    promptInfo.setUnitTest("");
                    return;
                }
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
        }

        /**
         * Core process to chat with LLM and get code in its response
         * @param prompt
         * @param record
         * @return unit test code
         */
        public String generateTest(List<ChatMessage> prompt, RoundRecord record) {

            if (MethodRunner.isExceedMaxTokens(config.getMaxPromptTokens(), prompt)) {
                config.getLogger().error("Exceed max prompt tokens: " + methodInfo.methodName + " Skipped.");
                record.setPromptToken(-1);
                record.setHasCode(false);
                return "";
            }
            config.getLogger().debug("[Prompt]:\n" + prompt);

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

            PromptInfo promptInfo = pc.getPromptInfo();
            if (promptInfo.getUnitTest().isEmpty()) {
                return false;
            }

            RoundRecord record = promptInfo.getRecords().get(promptInfo.getRound());

            // compilation and runtime validation
            if (runTest(config, pc.getFullTestName(), promptInfo, promptInfo.getRound())) {
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

        /**
         * We directly call test generation since the error prompt will auto-generated by
         * {@link PromptGenerator#generateMessages(PromptInfo)}
         * @param pc
         */
        public void execute(PromptConstructorImpl pc) {
            new TestGeneration().execute(pc);
        }
    }

}
