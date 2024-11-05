package zju.cst.aces.prompt;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.phase.PromptFile;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.template.PromptTemplate;
import zju.cst.aces.util.TokenCounter;

import java.io.IOException;
import java.util.*;

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
            chatMessages.add(ChatMessage.ofSystem(createSystemPrompt(promptInfo, selectPromptFile(templateName).getInit())));
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, selectPromptFile(templateName).getInitSystem())));
        } else {
            chatMessages.add(ChatMessage.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_REPAIR)));
        }

        return chatMessages;
    }
    public PromptFile selectPromptFile(String templateName) {
        // Map templateName to a specific PromptFile enum constant
        switch (templateName) {
            case "testpilot":
                return PromptFile.testpilot;
            // Add additional cases if there are more PromptFile constants
            default:
                return PromptFile.chatunitest;
        }
    }

    public String createUserPrompt(PromptInfo promptInfo, String templateName) {
        try {
            this.promptTemplate.buildDataModel(config, promptInfo);
            if (templateName.equals(promptTemplate.TEMPLATE_REPAIR)) { // repair process

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

}
