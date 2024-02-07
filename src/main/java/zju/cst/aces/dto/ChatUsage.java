package zju.cst.aces.dto;

import com.google.gson.annotations.SerializedName;
import lombok.Data;

@Data
public class ChatUsage {
    @SerializedName("prompt_tokens")
    private Integer promptTokens;
    @SerializedName("completion_tokens")
    private Integer completionTokens;
    @SerializedName("total_tokens")
    private Integer totalTokens;
}
