
package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.solution.CoverUp;
import zju.cst.aces.api.phase.solution.HITS;
import zju.cst.aces.api.phase.solution.TEPLA;
import zju.cst.aces.api.phase.solution.TestPilot;
import zju.cst.aces.api.phase.step.*;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public class PhaseImpl implements Phase {
    public enum PhaseType {
        TEPLA,
        TEST_PILOT,
        COVER_UP,
        HITS
    }

    protected final Config config;

    public PhaseImpl(Config config) {
        this.config = config;
    }

    public void prepare() {
        createPreparation().execute();
    }

    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num) {
        return createPromptGeneration(classInfo, methodInfo).execute(num);
    }

    public void generateTest(PromptConstructorImpl pc) {
        createTestGeneration().execute(pc);
    }

    public boolean validateTest(PromptConstructorImpl pc) {
        return createValidation().execute(pc);
    }

    public void repairTest(PromptConstructorImpl pc) {
        createRepairTask().execute(pc);
    }
    // Factory methods to create task instances
    protected Preparation createPreparation() {
        return new Preparation(config);
    }

    protected PromptGeneration createPromptGeneration(ClassInfo classInfo, MethodInfo methodInfo) {
        return new PromptGeneration(config, classInfo, methodInfo);
    }

    protected TestGeneration  createTestGeneration() {
        return new TestGeneration(config);
    }

    protected Validation createValidation() {
        return new Validation(config);
    }

    protected Repair createRepairTask() {
        return new Repair(config);
    }


    // Factory method to select the appropriate Phase subclass based on config
    public static PhaseImpl createPhase(Config config) {
        // Example logic to select Phase subclass based on config properties
        String phaseTypeString = config.getPhaseType();

        try {
            PhaseType phaseType = PhaseType.valueOf(phaseTypeString); // todo 这里似乎如果没有找到枚举对象会直接崩溃
            switch (phaseType) {
                case TEPLA:
                    return new TEPLA(config);
                case TEST_PILOT:
                    return new TestPilot(config);
                case COVER_UP:
                    return new CoverUp(config);
                case HITS:
                    return new HITS(config);
                default:
                    return new PhaseImpl(config); // Default or fallback Phase
            }
        }catch (IllegalArgumentException e) {
            return new PhaseImpl(config); // Default or fallback Phase
        }

    }
}