package zju.cst.aces.api.phase.solution;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.coverage.CodeCoverageAnalyzer;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.TestMessage;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static zju.cst.aces.runner.AbstractRunner.exportTest;
import static zju.cst.aces.runner.AbstractRunner.runTest;

public class COVERUP extends PhaseImpl {
    public static List<String>  uncoveredLines=new ArrayList<>();
    public static List<String>  coverage_message=new ArrayList<>();
    public COVERUP(Config config) {
        super(config);
    }
    @Override
    public boolean validateTest(PromptConstructorImpl pc){

        PromptInfo promptInfo = pc.getPromptInfo();
        String code=promptInfo.getUnitTest();
        if (promptInfo.getUnitTest().isEmpty()) {
            return false;
        }

        if (runTest(config, pc.getFullTestName(), promptInfo, promptInfo.getRound())) {
            try {
                String testName = pc.getFullTestName().substring(pc.getFullTestName().lastIndexOf(".") + 1);
                Path savePath = config.getTestOutput().resolve(pc.getFullTestName().replace(".", File.separator) + ".java");
                Map<String, Object> coverageInfo = new CodeCoverageAnalyzer().analyzeCoverage(
                        code, pc.getFullTestName(),
                        promptInfo.fullClassName,
                        promptInfo.methodSignature,
                        config.project.getBuildPath().toString(),
                        config.project.getCompileSourceRoots().get(0),
                        config.classPaths
                );

                float lineCoverage = coverageInfo.get("lineCoverage") instanceof Double
                        ? ((Double) coverageInfo.get("lineCoverage")).floatValue()
                        : 0.0f;
                String uncoveredCode = coverageInfo.get("methodCode").toString();
                List<String> uncoveredLines = (List<String>) coverageInfo.get("uncoveredLines");
                COVERUP.uncoveredLines=uncoveredLines;
                COVERUP.coverage_message= Arrays.asList(uncoveredCode);
                config.getLogger().info("Coverage Analysis for Method < " + promptInfo.getMethodInfo().getMethodName() + " >: " +
                        "Line Coverage: " + lineCoverage + "%\n" +
                        "Method Code:\n" + uncoveredCode + "\n" +
                        "Uncovered Lines: " + uncoveredLines);

                // Check if coverage is already at 100%
                if (lineCoverage == 100) {
                    config.getLogger().info("Test for method < " + promptInfo.getMethodInfo().getMethodName() +
                            " > successfully completed at maximum coverage: " + lineCoverage + "% after round " + promptInfo.getRound());
                    exportTest(code, savePath);
                    return true;
                }
                // Check maximum improvement threshold
                if (promptInfo.coverage_improve_time >= config.max_coverage_improve_time) {
                    exportTest(promptInfo.max_coverage_test_code, savePath);
                    return true;
                }

                // Update promptInfo if coverage improves
                TestMessage testMessage = new TestMessage();
                testMessage.setErrorType(TestMessage.ErrorType.COVERAGE_ISSUE);
                testMessage.setErrorMessage(uncoveredLines);
                promptInfo.setErrorMsg(testMessage);

                promptInfo.setCoverage_improve_time(promptInfo.coverage_improve_time + 1);
                config.getLogger().warn("maxTimes "+config.max_coverage_improve_time);
                config.getLogger().warn("The improve time "+promptInfo.coverage_improve_time);
                if (lineCoverage > promptInfo.coverage) {
                    promptInfo.setCoverage(lineCoverage);
                    promptInfo.setMax_coverage_test_code(code);
                }
                config.getLogger().warn("Test for method < " + promptInfo.getMethodInfo().getMethodName() +
                        " > did not achieve maximum coverage. Current coverage: " + lineCoverage + "%");

                return false;

            } catch (Exception e) {
                config.getLogger().error("An error occurred during coverage analysis. Saving test and skipping further coverage analysis.");
                e.printStackTrace();
                return true; // Skip further coverage analysis on exception
            }
        }
        return false;
    }
}
