package zju.cst.aces.runner.solution_runner;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.Phase;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.runner.MethodRunner;

import java.io.IOException;

public class MUTAPRunner extends MethodRunner {
    public MUTAPRunner(Config config, String fullClassName, MethodInfo methodInfo) throws IOException {
        super(config, fullClassName, methodInfo);
    }

    public boolean startRounds(final int num) throws IOException {
        Phase phase = PhaseImpl.createPhase(config);

        // Prompt Construction Phase
        PromptConstructorImpl pc = phase.generatePrompt(classInfo, methodInfo, num);
        PromptInfo promptInfo = pc.getPromptInfo();
        promptInfo.setRound(0);

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

        // 后面增加一段PITS的变异测试
        if (runPitMutationTests()) {
            exportRecord(pc.getPromptInfo(), classInfo, num);
            return true;
        }

        exportRecord(pc.getPromptInfo(), classInfo, num);
        return false;
    }

    // 使用 Maven 命令执行 PIT 变异测试
    private boolean runPitMutationTests() {
        try {
            // 执行 Maven 命令调用 PIT 变异测试
            ProcessBuilder processBuilder = new ProcessBuilder("mvn", "pitest:mutationCoverage");
            processBuilder.inheritIO(); // 让命令行的输出直接显示在控制台

            Process process = processBuilder.start();
            int exitCode = process.waitFor();

            // 根据 Maven 命令的退出码判断变异测试是否成功
            return exitCode == 0;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }
}
