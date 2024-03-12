package zju.cst.aces.api;

import zju.cst.aces.dto.ChatMessage;

import java.util.List;

public interface PromptConstructor {

    List<ChatMessage> generate();

}
