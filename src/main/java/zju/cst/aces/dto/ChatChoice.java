package zju.cst.aces.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ChatChoice {
    Integer index;
    ChatMessage message;
    @SerializedName("finish_reason")
    String finishReason;
}
