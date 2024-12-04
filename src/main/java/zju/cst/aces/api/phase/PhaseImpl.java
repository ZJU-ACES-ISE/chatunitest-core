
package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.solution.*;
import zju.cst.aces.api.phase.step.*;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

import java.io.IOException;

public class PhaseImpl implements Phase {
    public enum PhaseType {
        TELPA,
        TESTPILOT,
        COVERUP,
        HITS,
        SYMPROMPT,
        CHATTESTER,
        MUTAP
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
                case TELPA:
                    return new TELPA(config);
                case TESTPILOT:
                    return new TESTPILOT(config);
                case COVERUP:
                    return new COVERUP(config);
                case HITS:
                    return new HITS(config);
                case SYMPROMPT:
                    return new SYMPROMPT(config);
                case CHATTESTER:
                    return new CHATTESTER(config);
                case MUTAP:
                    return new MUTAP(config);
                default:
                    return new PhaseImpl(config); // Default or fallback Phase
            }
        }catch (IllegalArgumentException e) {
            return new PhaseImpl(config); // Default or fallback Phase
        }

    }
}