package zju.cst.aces.api.phase.task;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;

public class Repair {
    private final Config config;

    public Repair(Config config) {
        this.config = config;
    }

    public void execute(PromptConstructorImpl pc) {
        new TestGeneration(config).execute(pc);
    }
}
