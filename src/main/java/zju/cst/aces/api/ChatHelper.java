package zju.cst.aces.api;

import okhttp3.Response;
import zju.cst.aces.config.Config;
import zju.cst.aces.dto.Message;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.util.AskGPT;
import zju.cst.aces.util.CodeExtractor;

import java.util.List;

public class ChatHelper {

    public static Response chat(Config config, List<Message> messages) {
        Response response = new AskGPT(config).askChatGPT(messages);
        if (response == null) {
            throw new RuntimeException("Response is null, failed to get response.");
        }
        return response;
    }

    public static String extractCodeByResponse(Response response) {
        return new CodeExtractor(getContentByResponse(response)).getExtractedCode();
    }

    public static String getContentByResponse(Response response) {
        return AbstractRunner.parseResponse(response);
    }

}
