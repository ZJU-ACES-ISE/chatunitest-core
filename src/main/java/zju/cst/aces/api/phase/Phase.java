
package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.phaseTask.*;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public class Phase {
    private final Config config;

    public Phase(Config config) {
        this.config = config;
    }

    public void prepare() {
        new PreparationTask(config).execute();
    }

    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num) {
        return new PromptGenerationTask(config, classInfo, methodInfo).execute(num);
    }

    public void generateTest(PromptConstructorImpl pc) {
        new TestGenerationTask(config).execute(pc);
    }

    public boolean validateTest(PromptConstructorImpl pc) {
        return new ValidationTask(config).execute(pc);
    }

    public void repairTest(PromptConstructorImpl pc) {
        new RepairTask(config).execute(pc);
    }
    // Factory method to select the appropriate Phase subclass based on config
    private Phase createPhase() {
        // Example logic to select Phase subclass based on config properties
        String phaseType = config.getPhaseType();
        switch (phaseType) {
            case "Phase_TEPLA":
                return new Phase_TEPLA(config);
            case "Phase_TestPilot":
                return new Phase_TestPilot(config);
            case "Phase_CoverUp":
                return new Phase_CoverUp(config);
            case "Phase_HITS":
                return new Phase_HITS(config);
            default:
                return new Phase(config); // Default or fallback Phase
        }
    }

}
