package zju.cst.aces.api.phase.step;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.RoundRecord;

import static zju.cst.aces.runner.AbstractRunner.runTest;

public class Validation {
    private final Config config;

    public Validation(Config config) {
        this.config = config;
    }

    public boolean execute(PromptConstructorImpl pc) {
        PromptInfo promptInfo = pc.getPromptInfo();

        if (promptInfo.getUnitTest().isEmpty()) {
            return false;
        }
        if (runTest(config, pc.getFullTestName(), promptInfo, promptInfo.getRound())) {
            return true;
        }else{
            RoundRecord roundRecord = promptInfo.getRecords().get(promptInfo.getRound());
            roundRecord.setHasError(true);
            roundRecord.setErrorMsg(promptInfo.getErrorMsg());
        }

        return false;
    }
}
