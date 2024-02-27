package zju.cst.aces.prompt;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.*;
import zju.cst.aces.util.TokenCounter;

import java.io.IOException;
import java.util.*;

public class PromptGenerator {
    public Config config;
    public PromptTemplate promptTemplate;

    public PromptGenerator(Config config) throws IOException {
        this.config = config;
        this.promptTemplate = new PromptTemplate(config, config.properties, config.getPromptPath(), config.getMaxPromptTokens());
    }

    public void setConfig(Config config) {
        this.config = config;
        this.promptTemplate = new PromptTemplate(config, config.properties, config.getPromptPath(), config.getMaxPromptTokens());
    }

    public List<Message> generateMessages(PromptInfo promptInfo) {
        List<Message> messages = new ArrayList<>();
        if (promptInfo.errorMsg == null) { // round 0
            messages.add(Message.ofSystem(createSystemPrompt(promptInfo, promptTemplate.TEMPLATE_INIT)));
            messages.add(Message.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_INIT)));
        } else {
            messages.add(Message.of(createUserPrompt(promptInfo, promptTemplate.TEMPLATE_REPAIR)));
        }
        return messages;
    }

    public List<Message> generateMessages(PromptInfo promptInfo, String templateName) {
        List<Message> messages = new ArrayList<>();
        messages.add(Message.ofSystem(createSystemPrompt(promptInfo, templateName)));
        messages.add(Message.of(createUserPrompt(promptInfo, templateName)));
        return messages;
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
                config.getLog().debug("Allowed tokens: " + allowedTokens);
                config.getLog().debug("Processed error message: \n" + processedErrorMsg);

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
            filename = addSystemFileName(templateName);
            return promptTemplate.renderTemplate(filename);
        } catch (Exception e) {
            if (e instanceof IOException) {
                return "";
            }
            throw new RuntimeException("An error occurred while generating the system prompt: " + e);
        }
    }

    public String addSystemFileName(String filename) {
        String[] parts = filename.split("\\.");
        if (parts.length > 1) {
            return parts[0] + "_system." + parts[1];
        }
        return filename;
    }

    public String buildCOT(COT<?> cot) {
        return "";
    }

    public String buildTOT(TOT<?> tot) {
        return "";
    }

}
