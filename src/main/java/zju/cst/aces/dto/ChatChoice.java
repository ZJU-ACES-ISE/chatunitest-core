package zju.cst.aces.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ChatChoice {
    Integer index;
    Message message;
    @SerializedName("finish_reason")
    String finishReason;
}
