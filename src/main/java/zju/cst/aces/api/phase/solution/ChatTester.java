package zju.cst.aces.api.phase.solution;

import lombok.Data;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.api.phase.step.PromptGeneration;
import zju.cst.aces.api.phase.step.TestGeneration;
import zju.cst.aces.api.phase.step.Validation;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public class ChatTester extends PhaseImpl {
    public ChatTester(Config config) {
        super(config);
    }

    // 内部类 ChatTesterValidation
    public class ChatTesterValidation extends Validation {
        public ChatTesterValidation(Config config) {
            super(config);
        }

    }
}
