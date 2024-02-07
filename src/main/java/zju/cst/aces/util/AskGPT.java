package zju.cst.aces.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.config.ModelConfig;
import zju.cst.aces.dto.ChatResponse;
import zju.cst.aces.dto.Message;

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

    public ChatResponse askChatGPT(List<Message> messages) {
        String apiKey = config.getRandomKey();
        int maxTry = 5;
        while (maxTry > 0) {
            Response response = null;
            try {
                Map<String, Object> payload = new HashMap<>();

//                if (Objects.equals(config.getModel(), "code-llama") || Objects.equals(config.getModel(), "code-llama-13B")) {
//                    payload.put("max_tokens", 8092);
//                }

                ModelConfig modelConfig = config.getModel().getDefaultConfig();

                payload.put("messages", messages);
                payload.put("model", modelConfig.getModelName());
                payload.put("temperature", config.getTemperature());
                payload.put("frequency_penalty", config.getFrequencyPenalty());
                payload.put("presence_penalty", config.getPresencePenalty());
                payload.put("max_tokens", config.getMaxResponseTokens());
                String jsonPayload = GSON.toJson(payload);

                RequestBody body = RequestBody.create(MEDIA_TYPE, jsonPayload);
                Request request = new Request.Builder().url(modelConfig.getUrl()).post(body).addHeader("Content-Type", "application/json").addHeader("Authorization", "Bearer " + apiKey).build();

                response = config.getClient().newCall(request).execute();
                if (!response.isSuccessful()) throw new IOException("Unexpected code " + response);
                try {
                    Thread.sleep(config.sleepTime);
                } catch (InterruptedException ie) {
                    throw new RuntimeException("In AskGPT.askChatGPT: " + ie);
                }
                if (response.body() == null) throw new IOException("Response body is null.");
                ChatResponse chatResponse = GSON.fromJson(response.body().string(), ChatResponse.class);
                response.close();
                return chatResponse;
            } catch (IOException e) {
                if (response != null) {
                    response.close();
                }
                config.getLog().error("In AskGPT.askChatGPT: " + e);
                maxTry--;
            }
        }
        config.getLog().debug("AskGPT: Failed to get response\n");
        return null;
    }
}