package zju.cst.aces.api.phase.solution;

import com.fasterxml.jackson.databind.ObjectMapper;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.RepairImpl;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.api.phase.step.*;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.PromptGenerator;
import zju.cst.aces.runner.MethodRunner;
import zju.cst.aces.util.CodeExtractor;
import zju.cst.aces.util.JsonResponseProcessor;

import java.nio.file.Path;
import java.util.List;


public class HITS extends PhaseImpl {

    public PromptGenerator promptGenerator;
    public MethodInfo methodInfo;
    public TestGeneration testGeneration;

    public HITS(Config config) {
        super(config);
        testGeneration = new TestGeneration(config);
    }

    public void setUp(PromptInfo promptInfo) {
        promptGenerator = new PromptGenerator(config);
        methodInfo = promptInfo.getMethodInfo();
    }

//    @Override
//    public void generateTest(PromptConstructorImpl pc) {
//
//    }

    @Override
    public void repairTest(PromptConstructorImpl pc){
        executeForSliceTest(pc);
    }

    public void generateMethodSlice(PromptConstructorImpl pc) {
        executeForMethodSlice(pc);
    }

    public void generateSliceTest(PromptConstructorImpl pc) {
        executeForSliceTest(pc);
    }

    public void executeForMethodSlice(PromptConstructorImpl pc) {
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
            config.getLogger().info("Generating test slices for method < " + methodInfo.methodName + " > round " + rounds + " ...");
        } else {
            config.getLogger().info("Fixing test for method < " + methodInfo.methodName + " > round " + rounds + " ...");
        }
        List<ChatMessage> prompt;
        String response;
        if (config.isEnableObfuscate()) {
            Obfuscator obfuscator = new Obfuscator(config);
            PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
            obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);
            if(config.useSlice) {
                prompt = promptGenerator.generateMessages(obfuscatedPromptInfo, "HITS"); // todo
                generateMethodSlice(prompt, record, obfuscatedPromptInfo);
                config.useSlice = false;
            }else{
                //todo 不生成切片，正常生成test
            }
        } else {
            if(config.useSlice) {
                prompt = promptGenerator.generateMessages(promptInfo, "HITS");
                generateMethodSlice(prompt, record, promptInfo);
                config.useSlice = false;
            }else {
                //todo
            }
        }
    }

    public void executeForSliceTest(PromptConstructorImpl pc) {
        PromptInfo promptInfo = pc.getPromptInfo();
        if (promptGenerator == null) {
            setUp(promptInfo);
        }
        assert(promptInfo.getRound() != null);
        int rounds = promptInfo.getRound();
        promptInfo.addRecord(new RoundRecord(rounds));
        RoundRecord record = promptInfo.getRecords().get(rounds);
        record.setAttempt(promptInfo.getTestNum());
        if (promptInfo.getSliceNum() == null){
            System.out.println("sliceNum is null");
        }
        if (rounds == 0) {
            config.getLogger().info("Generating test for method < " + methodInfo.methodName + " > < sliceNum"+ promptInfo.getSliceNum() + " > round " + rounds + " ...");
        } else {
            config.getLogger().info("Fixing test for method < " + methodInfo.methodName + " > < sliceNum"+ promptInfo.getSliceNum() + " > round "  + rounds + " ...");
        }
        List<ChatMessage> prompt;
        String code;
        if (config.isEnableObfuscate()) {
            Obfuscator obfuscator = new Obfuscator(config);
            PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
            obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);
            prompt = promptGenerator.generateMessages(obfuscatedPromptInfo, "HITS");
            code = testGeneration.generateTest(prompt, record);
            if (!record.isHasCode()) {
                promptInfo.setUnitTest("");
                return;
            }
            code = obfuscator.deobfuscateJava(code);
        } else {
            prompt = promptGenerator.generateMessages(promptInfo, "HITS");
            code = testGeneration.generateTest(prompt, record);
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
     * Core process to chat with LLM and get method slices in its response
     * @param prompt
     * @param record
     * @param promptInfo
     * @return
     */
    public void generateMethodSlice(List<ChatMessage> prompt, RoundRecord record, PromptInfo promptInfo) {

        if (MethodRunner.isExceedMaxTokens(config.getMaxPromptTokens(), prompt)) {
            config.getLogger().error("Exceed max prompt tokens: " + methodInfo.methodName + " Skipped.");
            record.setPromptToken(-1);
            record.setHasCode(false);
            return;
        }
        config.getLogger().debug("[Prompt]:\n" + prompt);

        String slicePath = "methodSlice/" + promptInfo.getClassName() + "/" + promptInfo.getMethodName();
        Path fullDirectoryPath = config.tmpOutput.resolve(slicePath); //todo 每次初始生成需要将文件夹清空

        ChatResponse response = ChatGenerator.chat(config, prompt);
        String content = JsonResponseProcessor.getJsonContentByResponse(response.toString()); //todo get slice json result
        config.getLogger().debug("[Response]:\n" + content);

        boolean success = true;

        if (content != null) {
            // Step 2: Extract information from JSON content
            JsonResponseProcessor.JsonData info = JsonResponseProcessor.extractInfoFromJson(content);//todo extract main info and store in file
            if (info != null) {
                config.getLogger().debug("Extracted JSON Info: " + info.toString());
                // Step 3: Write the extracted JSON information to a file
                JsonResponseProcessor.writeJsonToFile(new ObjectMapper().valueToTree(info), fullDirectoryPath);
            } else {
                success = false;
                config.getLogger().debug("Failed to extract required information from JSON.");
            }
        } else {
            success = false;
            config.getLogger().debug("No JSON content found in the response.");
        }
        // todo 这里应该要有重复生成的机制，如果说content为null，或者提取不出info，应该要重新生成

        if (!success) { //If getting method slices fails
            for (int i = 0; i < 3; i++) { // todo 这里暂定3次，可以在config中设置
                try {
                    response = ChatGenerator.chat(config, prompt);
                    content = JsonResponseProcessor.getJsonContentByResponse(response.toString());
                    if (content != null) {
                        JsonResponseProcessor.JsonData info = JsonResponseProcessor.extractInfoFromJson(content);
                        if (info != null) {
                            JsonResponseProcessor.writeJsonToFile(new ObjectMapper().valueToTree(info), fullDirectoryPath);
                            break;
                        }
                    }
                } catch (Exception e) {
                    config.getLogger().debug("generate method slices failed with exception: " + e.getMessage());
                }
            }
        }

        record.setPromptToken(response.getUsage().getPromptTokens());
        record.setResponseToken(response.getUsage().getCompletionTokens());
        record.setPrompt(prompt);
        record.setResponse(content);

//            JsonResponseProcessor jsonResponseProcessor = new JsonResponseProcessor(promptInfo.getClassName(), promptInfo.getMethodName());

        promptInfo.setMethodSlicePath(fullDirectoryPath);
    }

}