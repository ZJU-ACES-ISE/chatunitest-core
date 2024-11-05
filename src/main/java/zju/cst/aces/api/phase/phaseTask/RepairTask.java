package zju.cst.aces.api.phase.phaseTask;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;

public class RepairTask {
    private final Config config;

    public RepairTask(Config config) {
        this.config = config;
    }

    public void execute(PromptConstructorImpl pc) {
        new TestGenerationTask(config).execute(pc);
    }
}
