package zju.cst.aces.runner;

import zju.cst.aces.api.Phase;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.ChatGenerator;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.impl.obfuscator.Obfuscator;
import zju.cst.aces.dto.*;
import zju.cst.aces.prompt.PromptGenerator;
import zju.cst.aces.util.JsonResponseProcessor;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class MethodRunner extends ClassRunner {

    public MethodInfo methodInfo;
    PromptGenerator promptGenerator;

    public MethodRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName);
        this.methodInfo = methodInfo;
        this.promptGenerator = new PromptGenerator(config);
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
                if (result && config.isStopWhenSuccess()) { //todo 为什么成功了要跳出循环？
                    break;
                }
            }
        }
    }

    public boolean startRounds(final int num) {

        Phase phase = new Phase(config);

        // Prompt Construction Phase
        PromptConstructorImpl pc = phase.new PromptGeneration(classInfo, methodInfo).execute(num);
        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setRound(0);
        // todo 构造对应的prompt，与llm交互获取code
        List<ChatMessage> prompt;
        String code;
        if (config.isEnableObfuscate()) {
            Obfuscator obfuscator = new Obfuscator(config);
            PromptInfo obfuscatedPromptInfo = new PromptInfo(promptInfo);
            obfuscator.obfuscatePromptInfo(obfuscatedPromptInfo);

            phase.new TestGeneration().executeForMethodSlice(pc);
        } else{
            phase.new TestGeneration().executeForMethodSlice(pc);
        }
        // todo 读取指定路径下的json文件，获取方法切片，开始循环生成方法切片对应的单元测试
        JsonResponseProcessor.JsonData methodSliceInfo = JsonResponseProcessor.readJsonFromFile(promptInfo.getMethodSlicePath().resolve("slice.json"));
        if (methodSliceInfo != null) {
            // Accessing the steps
            boolean hasErrors;
            for (int i=0; i<methodSliceInfo.getSteps().size(); i++) { //这里还要改
                // Test Generation Phase
                hasErrors = false;
                if (methodSliceInfo.getSteps().get(i) == null) continue;
                promptInfo.setSliceNum(i);
                promptInfo.setSliceStep(methodSliceInfo.getSteps().get(i)); // todo 存储切片信息到
                phase.new TestGeneration().executeForSliceTest(pc); //todo 改成新的hits对切片生成单元测试方法
                // Validation
                if (phase.new Validation().execute(pc)) {
                    exportRecord(pc.getPromptInfo(), classInfo, num);
                }else {
                    hasErrors = true;
                }
                if (hasErrors) {
                    // Validation and Repair Phase
                    for (int rounds = 0; rounds < config.getMaxRounds(); rounds++) {

                        promptInfo.setRound(rounds);

                        // Repair
                        phase.new Repair().executeForSliceTest(pc);

                        // Validation and process
                        if (phase.new Validation().execute(pc)) { // if passed validation
                            exportRecord(pc.getPromptInfo(), classInfo, num);
                            break; // successfully
                        }

                    }
                }

                exportSliceRecord(pc.getPromptInfo(), classInfo, num, i); //todo 检测是否顺利生成信息
            }
        } else {
            System.out.println("Failed to read JSON data from file.");
            //todo 这里可以是正常流程
        }

        return true;
    }

}