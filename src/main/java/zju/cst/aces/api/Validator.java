package zju.cst.aces.api;

import zju.cst.aces.dto.PromptInfo;

import java.nio.file.Path;

public interface Validator {

    boolean syntacticValidate(String code);
    boolean semanticValidate(String className, Path outputPath, PromptInfo promptInfo);
    boolean runtimeValidate(String fullTestName);

}
