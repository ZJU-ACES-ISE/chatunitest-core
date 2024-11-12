package zju.cst.aces.prompt;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.phase.solution.COVERUP;
import zju.cst.aces.api.phase.solution.SYMPROMPT;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.template.PromptTemplate;
import zju.cst.aces.util.TokenCounter;
import zju.cst.aces.util.symprompt.PathConstraintExtractor;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static zju.cst.aces.api.phase.solution.SYMPROMPT.convertedPaths;

public class PromptGenerator {
    public Config config;
    public PromptTemplate promptTemplate;

    public PromptGenerator(Config config) {
        this.config = config;
        this.promptTemplate = new PromptTemplate(config, config.properties, config.getPromptPath(), config.getMaxPromptTokens());
    }

    public void setConfig(Config config) {
        this.config = config;
        this.promptTemplate = new PromptTemplate(config, config.properties, config.getPromptPath(), config.getMaxPromptTokens());
    }

//    /**
//     * Generate messages for hits
//     * @param promptInfo
//     * @return
//     */
//    public List<ChatMessage> generateTestForHITS(PromptInfo promptInfo) {
//        List<ChatMessage> chatMessages = new ArrayList<>();
//        if (promptInfo.errorMsg == null) { // round 0
//            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, promptTemplate.TEMPLATE_SYS_GEN)));
//            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_GEN_CODE)));
//        } else {
//            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, promptTemplate.TEMPLATE_HITS_SYS_REPAIR)));
//            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_HITS_REPAIR)));
//        }
//        return chatMessages;
//    }
//
//    /**
//     * Generate slices for hits
//     * @param promptInfo
//     * @return
//     */
//    public List<ChatMessage> generateSliceForHITS(PromptInfo promptInfo) {
//        List<ChatMessage> chatMessages = new ArrayList<>();
//        chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, promptTemplate.TEMPLATE_SYS_GEN))); //todo 为空
//        chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_GEN_SLICE)));
//        return chatMessages;
//    }

    /**
     * Generate messages by promptInfo with no errors (generation - round 0) or errors (repair - round > 0)
     * @param promptInfo
     * @return
     */
    public List<ChatMessage> generateMessages(PromptInfo promptInfo) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        if (promptInfo.errorMsg == null) { // round 0
            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, promptTemplate.TEMPLATE_INIT_SYSTEM)));
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_INIT)));
        } else {
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_REPAIR)));
        }
        return chatMessages;
    }
    /**
     * Generate messages by promptInfo with no errors (generation - round 0) or errors (repair - round > 0)
     * @param promptInfo
     * @return
     */
    public List<ChatMessage> generateMessages(PromptInfo promptInfo, String templateName) {
        List<ChatMessage> chatMessages = new ArrayList<>();
        if (promptInfo.errorMsg == null) { // round 0
            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, selectPromptFile(templateName, false).getGenerate())));
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, selectPromptFile(templateName, false).getGenerateSystem())));
        } else {
            processRepair(promptInfo);
            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, selectPromptFile(templateName, true).getGenerate())));
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, selectPromptFile(templateName, true).getGenerateSystem())));
        }

        return chatMessages;
    }
    public PromptFile selectPromptFile(String templateName, boolean ifRepair) {
        // Map templateName to a specific PromptFile enum constant
        switch (templateName) {
            case "TESTPILOT":
                if (!ifRepair){
                    return PromptFile.testpilot_init;
                } else {
                    return PromptFile.testpilot_repair;
                }
            case "hits":
                if(config.useSlice){
                    return PromptFile.hits_slice_init;
                }else{
                    if (!ifRepair){
                        return PromptFile.hits_test_init;
                    } else {
                        return PromptFile.hits_test_repair;
                    }
                }
            case "COVERUP":
                if(!ifRepair){
                    return PromptFile.chatunitest_init;
                }else{
                    if(COVERUP.entireCovered){
                        return PromptFile.chatunitest_repair;
                    }else{
                        promptTemplate.dataModel.put("coverage_message", COVERUP.coverage_message);
                        promptTemplate.dataModel.put("uncovered_lines", COVERUP.uncoveredLines);
                        return PromptFile.coverup_repair;
                    }
                }
            case "SYMPROMPT":
                if(!ifRepair){
                    promptTemplate.dataModel.put("minPaths", SYMPROMPT.convertedPaths);
                    return PromptFile.symprompt_init;
                }else{
                    return PromptFile.chatunitest_repair;
                }

            default:
                if (!ifRepair){
                    return PromptFile.chatunitest_init;
                } else {
                    return PromptFile.chatunitest_repair;
                }
        }
    }

    public String createUserPrompt(PromptInfo promptInfo, String templateName) {
        try {
            this.promptTemplate.buildDataModel(config, promptInfo);
            if (templateName.equals(promptTemplate.TEMPLATE_REPAIR)) { // repair process
                return promptTemplate.renderTemplate(promptTemplate.TEMPLATE_REPAIR);
            } else {
                return promptTemplate.renderTemplate(templateName);
            }
        } catch (Exception e) {
            throw new RuntimeException("An error occurred while generating the user prompt: " + e);
        }
    }

    public String createSystemPrompt(PromptInfo promptInfo, String templateName) {
        try {
            this.promptTemplate.buildDataModel(config, promptInfo);
            String filename;
            filename = templateName;
            return promptTemplate.renderTemplate(filename);
        } catch (Exception e) {
            if (e instanceof IOException) {
                return "";
            }
            throw new RuntimeException("An error occurred while generating the system prompt: " + e);
        }
    }



    public String buildCOT(COT<?> cot) {
        return "";
    }

    public String buildTOT(TOT<?> tot) {
        return "";
    }
    public void processRepair(PromptInfo promptInfo){
        int promptTokens = TokenCounter.countToken(promptInfo.getUnitTest())
                + TokenCounter.countToken(promptInfo.getMethodSignature())
                + TokenCounter.countToken(promptInfo.getClassName())
                + TokenCounter.countToken(promptInfo.getContext())
                + TokenCounter.countToken(promptInfo.getOtherMethodBrief());
        int allowedTokens = Math.max(config.getMaxPromptTokens() - promptTokens, config.getMinErrorTokens());
        TestMessage errorMsg = promptInfo.getErrorMsg();
        String processedErrorMsg = "";
        for (String error : errorMsg.getErrorMessage()) {
            if (TokenCounter.countToken(processedErrorMsg + error + "\n") <= allowedTokens) {
                processedErrorMsg += error + "\n";
            }
        }
        config.getLogger().debug("Allowed tokens: " + allowedTokens);
        config.getLogger().debug("Processed error message: \n" + processedErrorMsg);

        promptTemplate.dataModel.put("unit_test", promptInfo.getUnitTest());
        promptTemplate.dataModel.put("error_message", processedErrorMsg);
    }
}
