package zju.cst.aces.runner;

import zju.cst.aces.api.phase.Phase;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.api.phase.Phase_HITS;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.PromptGenerator;
import zju.cst.aces.util.JsonResponseProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MethodRunner extends ClassRunner {

    public MethodInfo methodInfo;

    public MethodRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName);
        this.methodInfo = methodInfo;
    }

    @Override
    public void start() throws IOException {
        if (!config.isStopWhenSuccess() && config.isEnableMultithreading()) {
            ExecutorService executor = Executors.newFixedThreadPool(config.getTestNumber());
            List<Future<String>> futures = new ArrayList<>();
            for (int num = 0; num < config.getTestNumber(); num++) {
                int finalNum = num;
                Callable<String> callable = () -> {
                    startRounds(finalNum);
                    return "";
                };
                Future<String> future = executor.submit(callable);
                futures.add(future);
            }
            Runtime.getRuntime().addShutdownHook(new Thread(executor::shutdownNow));

            for (Future<String> future : futures) {
                try {
                    String result = future.get();
                    System.out.println(result);
                } catch (InterruptedException | ExecutionException e) {
                    e.printStackTrace();
                }
            }

            executor.shutdown();
        } else {
            for (int num = 0; num < config.getTestNumber(); num++) {
                boolean result = startRounds(num);
                if (result && config.isStopWhenSuccess()) {
                    break;
                }
            }
        }
    }

    public boolean startRounds(final int num) {

        config.setPhaseType("HITS");
        Phase phase = Phase.createPhase(config);

        // Prompt Construction Phase
        PromptConstructorImpl pc = phase.generatePrompt(classInfo, methodInfo,num);
        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setRound(0);

        if(config.getPhaseType() == "HITS") {
            Phase_HITS phase_hits = (Phase_HITS) phase;
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
                boolean hasErrors;
                for (int i = 0; i < methodSliceInfo.getSteps().size(); i++) { //这里还要改
                    // Test Generation Phase
                    hasErrors = false;
                    if (methodSliceInfo.getSteps().get(i) == null) continue;
                    promptInfo.setSliceNum(i);
                    promptInfo.setSliceStep(methodSliceInfo.getSteps().get(i)); // todo 存储切片信息到
                    phase_hits.generateSliceTest(pc); //todo 改成新的hits对切片生成单元测试方法
                    // Validation
                    if (phase_hits.validateTest(pc)) {
                        exportRecord(pc.getPromptInfo(), classInfo, num);
                    } else {
                        hasErrors = true;
                    }
                    if (hasErrors) {
                        // Validation and Repair Phase
                        for (int rounds = 0; rounds < config.getMaxRounds(); rounds++) {

                            promptInfo.setRound(rounds);

                            // Repair
                            phase_hits.repairTest(pc);

                            // Validation and process
                            if (phase_hits.validateTest(pc)) { // if passed validation
                                exportRecord(pc.getPromptInfo(), classInfo, num);
                                break; // successfully
                            }

                        }
                    }

                    exportSliceRecord(pc.getPromptInfo(), classInfo, num, i); //todo 检测是否顺利生成信息
                }
            }
        }


        // Test Generation Phase
        phase.generateTest(pc);

        // Validation
        if (phase.validateTest(pc)) {
            exportRecord(pc.getPromptInfo(), classInfo, num);

            return true;
        }

        // Validation and Repair Phase
        for (int rounds = 1; rounds < config.getMaxRounds(); rounds++) {

            promptInfo.setRound(rounds);

            // Repair
            phase.repairTest(pc);

            // Validation and process
            if (phase.validateTest(pc)) { // if passed validation
                exportRecord(pc.getPromptInfo(), classInfo, num);
                return true;
            }

        }

        exportRecord(pc.getPromptInfo(), classInfo, num);
        return false;
    }
}