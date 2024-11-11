package zju.cst.aces.api.phase;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.PromptConstructorImpl;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;

public interface Phase {
    void prepare();
    PromptConstructorImpl generatePrompt(ClassInfo classInfo, MethodInfo methodInfo, int num);
    void generateTest(PromptConstructorImpl pc);
    boolean validateTest(PromptConstructorImpl pc);
    void repairTest(PromptConstructorImpl pc);
}
