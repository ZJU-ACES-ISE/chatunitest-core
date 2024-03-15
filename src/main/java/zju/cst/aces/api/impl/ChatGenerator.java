package zju.cst.aces.api.impl;

import zju.cst.aces.api.Generator;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.ChatMessage;
import zju.cst.aces.dto.ChatResponse;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.util.AskGPT;
import zju.cst.aces.util.CodeExtractor;

import java.util.List;

public class ChatGenerator implements Generator {

    Config config;

    public ChatGenerator(Config config) {
        this.config = config;
    }

    @Override
    public String generate(List<ChatMessage> chatMessages) {
        return extractCodeByResponse(chat(config, chatMessages));
    }

    public static ChatResponse chat(Config config, List<ChatMessage> chatMessages) {
        ChatResponse response = new AskGPT(config).askChatGPT(chatMessages);
        if (response == null) {
            throw new RuntimeException("Response is null, failed to get response.");
        }
        return response;
    }

    public static String extractCodeByResponse(ChatResponse response) {
        return new CodeExtractor(getContentByResponse(response)).getExtractedCode();
    }

    public static String getContentByResponse(ChatResponse response) {
        return AbstractRunner.parseResponse(response);
    }

    public static String extractCodeByContent(String content) {
        return new CodeExtractor(content).getExtractedCode();
    }
}
