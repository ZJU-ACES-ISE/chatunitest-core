package zju.cst.aces.api.phase.solution;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.api.phase.PhaseImpl;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.util.symprompt.PathConstraintExtractor;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

public class SYMPROMPT extends PhaseImpl {
    public static  List<String> convertedPaths;
    public SYMPROMPT(Config config) {
        super(config);
    }

    @Override
    public PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num){
            List<List<String>> minPaths = PathConstraintExtractor.extractPathConstraints(methodInfo.sourceCode);
            // Convert List<List<String>> to List<String>
            SYMPROMPT.convertedPaths = minPaths.stream()
                    .map(path -> String.join(",", path))
                    .collect(Collectors.toList());
            return super.generatePrompt(classInfo, methodInfo, num);
    }
}
