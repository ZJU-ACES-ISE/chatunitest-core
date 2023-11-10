package zju.cst.aces.api;

import zju.cst.aces.dto.Message;

import java.util.List;

public interface PromptConstructor {

    List<Message> generate();

}
