package zju.cst.aces.dto;
import lombok.Data;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Data
public class ChatResponse {
    private String id;
    private String model;
    private Long created;
    private String object;
    private ChatUsage usage;
    private List<ChatChoice> choices;

    public List<ChatMessage> getMessages() {
        if (this.choices == null || this.choices.isEmpty()) return Collections.emptyList();
        return this.choices.stream().map(ChatChoice::getMessage).collect(Collectors.toList());
    }

    /**
     * get reply text from messages' content
     *
     * @return reply text
     */
    public String getContent() {
        if (this.choices == null || this.choices.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (ChatChoice choice : choices) {
            ChatMessage chatMessage = choice.getMessage();
            if (chatMessage != null && chatMessage.getContent() != null) {
                sb.append(chatMessage.getContent());
            }
        }
        return sb.toString();
    }
}
