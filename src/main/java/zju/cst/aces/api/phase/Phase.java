package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.step.*;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public class Phase{
    protected final Config config;

    public Phase(Config config) {
        this.config = config;
    }

    public void prepare() {
        Preparation preparation =createPreparation();
        preparation.execute();
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


    public static Phase createPhase(Config config) {
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
                throw new IllegalArgumentException("Unknown phase type: " + phaseType);
        }
    }
}
