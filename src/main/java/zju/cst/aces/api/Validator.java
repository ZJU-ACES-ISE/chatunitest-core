package zju.cst.aces.api;

import zju.cst.aces.dto.PromptInfo;

import java.nio.file.Path;

public interface Validator {

    public boolean syntacticValidate(String code);
    public boolean semanticValidate(String className, Path outputPath, PromptInfo promptInfo);
    public boolean runtimeValidate(String fullTestName);

}
