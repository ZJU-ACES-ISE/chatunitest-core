package zju.cst.aces.api;

import java.io.IOException;

public interface Repair {

    public String ruleBasedRepair(String code);
    public String LLMBasedRepair(String code);
    public String LLMBasedRepair(String code, int rounds);

}
