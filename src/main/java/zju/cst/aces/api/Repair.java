package zju.cst.aces.api;

import java.io.IOException;

public interface Repair {

    String ruleBasedRepair(String code);
    String LLMBasedRepair(Validator validator, String code);
    String LLMBasedRepair(Validator validator, String code, int rounds);

}
