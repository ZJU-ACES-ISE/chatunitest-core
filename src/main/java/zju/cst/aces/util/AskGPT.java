package zju.cst.aces.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.*;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.config.ModelConfig;
import zju.cst.aces.dto.ChatMessage;
import zju.cst.aces.dto.ChatResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AskGPT {
    private static final MediaType MEDIA_TYPE = MediaType.parse("application/json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    public Config config;

    public AskGPT(Config config) {
        this.config = config;
    }

    public ChatResponse askChatGPT(List<ChatMessage> chatMessages) {
//        String apiKey = config.getRandomKey(); //
        String hundsunID = ""; //不需要apikey，只需要ID
        String modelName = "";
        String hundsunGPTAPI = "";
        int maxTry = 5;
        while (maxTry > 0) {
            Response response = null;
            try {
                Map<String, Object> payload = new HashMap<>();

//                ModelConfig modelConfig = config.getModel().getDefaultConfig();

                // Create the model_params JSON object
                Map<String, Object> modelParams = new HashMap<>();
                modelParams.put("temperature", config.getTemperature());
                modelParams.put("frequency_penalty", config.getFrequencyPenalty());
                modelParams.put("presence_penalty", config.getPresencePenalty());
                modelParams.put("max_tokens", config.getMaxResponseTokens());

                // Create the extra JSON object if there are additional parameters
                Map<String, Object> extraParams = new HashMap<>();
                // Add any extra parameters needed here, for example:
                // extraParams.put("some_param", "some_value");
                // test
                // Construct the message object
                Map<String, Object> message = new HashMap<>();
                message.put("prefix_text", "推荐一部好看的电影");
                message.put("type", "common");
                String jsonParams = GSON.toJson(modelParams);
                message.put("model_params", jsonParams);
                message.put("extra", extraParams);

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

                response = config.getClient().newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                try {
                    Thread.sleep(config.sleepTime);
                } catch (InterruptedException ie) {
                    throw new RuntimeException("In AskGPT.askChatGPT: " + ie);
                }
                if (response.body() == null) throw new IOException("Response body is null.");
                // 这里需要修改
                ChatResponse chatResponse = GSON.fromJson(response.body().string(), ChatResponse.class);
                response.close();
                return chatResponse;
            } catch (IOException e) {
                if (response != null) {
                    response.close();
                }
                config.getLogger().error("In AskGPT.askChatGPT: " + e);
                maxTry--;
            }
        }
        config.getLogger().debug("AskGPT: Failed to get response\n");
        return null;
    }

}
