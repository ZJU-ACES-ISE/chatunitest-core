package zju.cst.aces.util;

import lombok.Data;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.config.ModelConfig;
import zju.cst.aces.dto.ChatChoice;
import zju.cst.aces.dto.ChatMessage;
import zju.cst.aces.dto.ChatResponse;
import zju.cst.aces.dto.ChatUsage;

import java.io.IOException;
import java.util.*;

public class AskGPT {
    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public Config config;

    public AskGPT(Config config) {
        this.config = config;
    }

    public ChatResponse askChatGPT(List<ChatMessage> chatMessages) {
        String hundsunID = "your_hundsun_id"; // 设置实际值
        String modelName = "gpt-3.5-turbo"; // 设置实际值
        String hundsunGPTAPI = "your_api_url"; // 设置实际值
        int maxTry = 5;
        while (maxTry > 0) {
            Response response = null;
            try {
                Map<String, Object> payload = new HashMap<>();

                // Create the model_params JSON object
                Map<String, Object> modelParams = new HashMap<>();
                modelParams.put("temperature", config.getTemperature());
                modelParams.put("frequency_penalty", config.getFrequencyPenalty());
                modelParams.put("presence_penalty", config.getPresencePenalty());
                modelParams.put("max_tokens", config.getMaxResponseTokens());

                // Create the extra JSON object if there are additional parameters
                Map<String, Object> extraParams = new HashMap<>();

                // Construct the message object
                Map<String, Object> message = new HashMap<>();
                message.put("prefix_text", chatMessages);
                message.put("type", "common");
                String jsonParams = GSON.toJson(modelParams);
                message.put("model_params", jsonParams);
//                message.put("extra", extraParams);

                payload.put("message", message);
                payload.put("model", modelName);
                payload.put("stream", false);

                String jsonPayload = GSON.toJson(payload);

                RequestBody body = RequestBody.create(MEDIA_TYPE, jsonPayload);
                HttpUrl url = HttpUrl.parse(hundsunGPTAPI);
                Request request = new Request.Builder()
                        .url(url)
                        .post(body)
                        .addHeader("Content-Type", "application/json")
                        .addHeader("user", hundsunID)
                        .addHeader("User-Agent", "PostmanRuntime/7.39.0")
                        .addHeader("Accept", "*/*")
                        .addHeader("Accept-Encoding", "gzip, deflate, br")
                        .addHeader("Connection", "keep-alive")
                        .addHeader("Host", url.host())
                        .addHeader("Content-Length", String.valueOf(body.contentLength()))
                        .build();
                // 这里修改请求头，加上user
                try {
                    response = config.getClient().newCall(request).execute();
                }catch (IOException e){
                    System.err.println("Request failed: " + e.getMessage());
                    e.printStackTrace();
                }

                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                try {
                    Thread.sleep(config.sleepTime);
                } catch (InterruptedException ie) {
                    throw new RuntimeException("In AskGPT.askChatGPT: " + ie);
                }
                if (response.body() == null) throw new IOException("Response body is null.");

                String response_data = response.body().string();
                if (response_data.startsWith("data:")) {
                    response_data = response_data.substring(5, response_data.length() - 1);
                }

                Map<String, Object> customResponse = GSON.fromJson(response_data, Map.class);
                response.close();

                // 构造符合 ChatResponse 结构的对象
                ChatResponse chatResponse = new ChatResponse();
                chatResponse.setId("chatcmpl-" + UUID.randomUUID().toString().replace("-", ""));
                chatResponse.setObject("chat.completion");
                chatResponse.setCreated(System.currentTimeMillis() / 1000);
                chatResponse.setModel(modelName);
//                String mockResponse = "{ \"choices\": [ { \"message\": { \"role\": \"assistant\", \"content\": \"当然！如果你喜欢科幻和动作电影，我强烈推荐《黑客帝国》(The Matrix)。这部电影由沃卓斯基姐妹 (Lana and Lilly Wachowski) 执导，讲述了一个虚拟现实世界中的反抗故事。\" }, \"finish_reason\": \"stop\" } ] }";
//                Map<String, Object> customResponse = GSON.fromJson(mockResponse, Map.class);
                List<ChatChoice> choices = new ArrayList<>();
                List<Map<String, Object>> customChoices = (List<Map<String, Object>>) customResponse.get("choices");
                for (int i = 0; i < customChoices.size(); i++) {
                    Map<String, Object> customChoice = customChoices.get(i);
                    ChatChoice choice = new ChatChoice();
                    choice.setIndex(i);
                    ChatMessage messageObj = new ChatMessage();
                    Map<String, String> messageMap = (Map<String, String>) customChoice.get("message");
                    messageObj.setRole(messageMap.get("role"));
                    messageObj.setContent(messageMap.get("content"));
                    choice.setMessage(messageObj);
                    choice.setFinishReason("stop");
                    choices.add(choice);
                }
                chatResponse.setChoices(choices);

                ChatUsage usage = new ChatUsage();
                usage.setPromptTokens(12);
                usage.setCompletionTokens(16);
                usage.setTotalTokens(28);
                chatResponse.setUsage(usage);
//                chatResponse.setSystem_fingerprint(null);

                return chatResponse;
            } catch (IOException e) {
                if (response != null) {
                    response.close();
                }
                config.getLogger().error("In AskGPT.askChatGPT: " + e);
                System.err.println("Error: " + e.getMessage());
                maxTry--;
            }
        }

        config.getLogger().debug("AskGPT: Failed to get response\n");
        System.err.println("AskGPT: Failed to get response after multiple attempts");
        return null;
    }
}
