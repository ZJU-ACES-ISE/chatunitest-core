package zju.cst.aces.util;


import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.ChatMessage;
import zju.cst.aces.dto.ChatResponse;
import zju.cst.aces.util.AskGPT;

import java.util.Arrays;

public class TestGPT {
    public static void main(String[] args) {
        // 配置参数（替换为实际配置）
        Config config = new Config();
        config.setTemperature(0.7);
        config.setFrequencyPenalty(0);
        config.setPresencePenalty(0);
        config.setMaxResponseTokens(150);
        config.setSleepTime(1000);

        // 创建消息
        ChatMessage message = new ChatMessage("推荐一部好看的电影", "common");

        // 创建 AskGPT 实例
        AskGPT askGPT = new AskGPT(config);

        // 发送请求
        ChatResponse response = askGPT.askChatGPT(Arrays.asList(message));
        Gson prettyGson = new GsonBuilder().setPrettyPrinting().create();
        // 打印响应
        if (response != null) {
//            System.out.println("Response: " + response.getChoices().get(0).getMessage().getContent());
//            System.out.println("id: " + response.getId());
            String jsonOutput = prettyGson.toJson(response);
            System.out.println(jsonOutput);
        } else {
            System.out.println("Failed to get response from API.");
        }
    }
}

