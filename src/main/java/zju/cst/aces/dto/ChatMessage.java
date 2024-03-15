package zju.cst.aces.dto;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage {
    private String role;
    private String content;

    public static ChatMessage of(String content) {

        return new ChatMessage(ChatMessage.Role.USER.getValue(), content);
    }

    public static ChatMessage ofSystem(String content) {

        return new ChatMessage(Role.SYSTEM.getValue(), content);
    }

    public static ChatMessage ofAssistant(String content) {

        return new ChatMessage(Role.ASSISTANT.getValue(), content);
    }

    @Getter
    @AllArgsConstructor
    public enum Role {

        SYSTEM("system"),
        USER("user"),
        ASSISTANT("assistant"),
        ;
        private final String value;
    }

}