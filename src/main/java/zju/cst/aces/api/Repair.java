package zju.cst.aces.api;

import java.io.IOException;

public interface Repair {

    String ruleBasedRepair(String code);
    String LLMBasedRepair(String code);
    String LLMBasedRepair(String code, int rounds);

}
