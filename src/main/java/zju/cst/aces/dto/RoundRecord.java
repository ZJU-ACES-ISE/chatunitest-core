package zju.cst.aces.dto;

import lombok.Data;

import java.util.List;

@Data
public class RoundRecord {
    public int attempt;
    public int round;
    public List<Message> prompt;
    public String response;
    public int promptToken;
    public int responseToken;
    public boolean hasCode;
    public String code;
    public boolean hasError;
    public TestMessage errorMsg;

    public RoundRecord(int round) {
        this.round = round;
    }
}
