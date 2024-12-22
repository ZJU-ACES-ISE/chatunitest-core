package zju.cst.aces.runner.solution_runner;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.api.phase.solution.HITS;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.runner.MethodRunner;
import zju.cst.aces.util.JsonResponseProcessor;

import java.io.IOException;

public class HITSRunner extends MethodRunner {
    public HITSRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName, methodInfo);
    }

    /**
     * Main process of HITS, including:
     * @param num
     * @return If the generation process is successful
     */
    @Override
    public boolean startRounds(final int num) {
        PhaseImpl phase = PhaseImpl.createPhase(config);
        config.useSlice = true;

        // Prompt Construction Phase
        PromptConstructorImpl pc = phase.generatePrompt(classInfo, methodInfo,num);
        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setRound(0);

        HITS phase_hits = (HITS) phase;

        if (config.isEnableObfuscate()) {
            Obfuscator obfuscator = new Obfuscator(config);
            PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
            obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);

            phase_hits.generateMethodSlice(pc);
        } else {
            phase_hits.generateMethodSlice(pc);
        }
        JsonResponseProcessor.JsonData methodSliceInfo = JsonResponseProcessor.readJsonFromFile(promptInfo.getMethodSlicePath().resolve("slice.json"));
        if (methodSliceInfo != null) {
            // Accessing the steps
            boolean hasErrors = false;
            for (int i = 0; i < methodSliceInfo.getSteps().size(); i++) {
                // Test Generation Phase
                hasErrors = false;
                if (methodSliceInfo.getSteps().get(i) == null) continue;
                promptInfo.setSliceNum(i);
                promptInfo.setSliceStep(methodSliceInfo.getSteps().get(i)); // todo 存储切片信息到promptInfo
                phase_hits.generateSliceTest(pc); //todo 改成新的hits对切片生成单元测试方法
                // Validation
                if (phase_hits.validateTest(pc)) {
                    exportRecord(pc.getPromptInfo(), classInfo, num);
                    continue;
                } else {
                    hasErrors = true;
                }
                if (hasErrors) {
                    // Validation and Repair Phase
                    for (int rounds = 1; rounds < config.getMaxRounds(); rounds++) {

                        promptInfo.setRound(rounds);

                        // Repair
                        phase_hits.generateSliceTest(pc);
                        // Validation and process
                        if (phase_hits.validateTest(pc)) { // if passed validation
                            exportRecord(pc.getPromptInfo(), classInfo, num);
                            hasErrors = false;
                            break; // successfully
                        }

                    }
                }

                exportSliceRecord(pc.getPromptInfo(), classInfo, num, i); //todo 检测是否顺利生成信息
            }
            return !hasErrors;
        }
        return true;
    }
}
