package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.task.*;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public class Phase{
    protected final Config config;

    public Phase(Config config) {
        this.config = config;
    }

    public void prepare() {
        Preparation preparation =createPreparationTask();
        preparation.execute();
    }

    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num) {
        return createPromptGenerationTask(classInfo, methodInfo).execute(num);
    }

    public void generateTest(PromptConstructorImpl pc) {
        createTestGenerationTask().execute(pc);
    }

    public boolean validateTest(PromptConstructorImpl pc) {
        return createValidationTask().execute(pc);
    }

    public void repairTest(PromptConstructorImpl pc) {
        createRepairTask().execute(pc);
    }
    // Factory methods to create task instances
    private Preparation createPreparationTask() {
        return new Preparation(config);
    }

    private PromptGeneration createPromptGenerationTask(ClassInfo classInfo, MethodInfo methodInfo) {
        return new PromptGeneration(config, classInfo, methodInfo);
    }

    private TestGeneration  createTestGenerationTask() {
        return new TestGeneration(config);
    }

    private Validation createValidationTask() {
        return new Validation(config);
    }

    private Repair createRepairTask() {
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
