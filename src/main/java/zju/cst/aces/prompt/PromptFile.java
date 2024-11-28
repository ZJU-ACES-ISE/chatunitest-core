package zju.cst.aces.prompt;

import zju.cst.aces.api.config.Config;

import static zju.cst.aces.parser.ProjectParser.config;

public enum PromptFile {
    // 定义枚举常量及其对应的模板文件名
    chatunitest_init("PROMPT_TEMPLATE_INIT", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    chatunitest_repair("PROMPT_TEMPLATE_REPAIR", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    testpilot_init("PROMPT_TEMPLATE_TESTPILOT_INIT", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    testpilot_repair("PROMPT_TEMPLATE_REPAIR", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    hits_slice_init("PROMPT_TEMPLATE_HITS_GEN_SLICE", "PROMPT_TEMPLATE_HITS_SYS_GEN"),
    hits_test_init("PROMPT_TEMPLATE_HITS_GEN", "PROMPT_TEMPLATE_HITS_SYS_GEN"),
    hits_test_repair("PROMPT_TEMPLATE_HITS_REPAIR", "PROMPT_TEMPLATE_HITS_SYS_REPAIR"),
    chattester_init("PROMPT_TEMPLATE_CHATTESTER_INIT", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    chattester_repair("PROMPT_TEMPLATE_CHATTESTER_REPAIR", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    chattester_extra("PROMPT_TEMPLATE_CHATTESTER_EXTRA", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    testspark_init("PROMPT_TEMPLATE_TESTSPARK_INIT", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    testspark_repair("PROMPT_TEMPLATE_TESTSPARK_REPAIR", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    coverup_repair("PROMPT_TEMPLATE_COVERUP_REPAIR", "PROMPT_TEMPLATE_INIT_SYSTEM"),
    symprompt_init("PROMPT_TEMPLATE_SYMPROMPT_INIT", "PROMPT_TEMPLATE_SYMPROMPT_INIT_SYSTEM"),
    telpa_init("PROMPT_TEMPLATE_TELPA_INIT", "PROMPT_TEMPLATE_INIT_SYSTEM");
    // 成员变量
    private final String generate;
    private final String generateSystem;
    // 构造函数
    PromptFile(String generate, String generateSystem) {
        this.generate = generate;
        this.generateSystem = generateSystem;
    }

    public String getGenerate() {
        return config.getProperties().getProperty(this.generate);
    }

    public String getGenerateSystem() {
        return config.getProperties().getProperty(this.generateSystem);
    }
}
