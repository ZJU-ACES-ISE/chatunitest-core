package zju.cst.aces.prompt;

public enum PromptFile {
    
    // 定义枚举常量及其对应的模板文件名
    chatunitest_init("TEMPLATE_INIT","TEMPLATE_INIT_SYSTEM"),
    chatunitest_repair("TEMPLATE_REPAIR","TEMPLATE_REPAIR_SYSTEM"), //todo 原来的chatunitest的repair和gen是用的一套sys模板，建议还是稍微区分一下吧
    testpilot_init("TEMPLATE_TESTPILOT_INIT","TEMPLATE_TESTPILOT_INIT_SYSTEM"),
    testpilot_repair("TEMPLATE_TESTPILOT_INIT","TEMPLATE_TESTPILOT_INIT_SYSTEM"),
    hits_slice_init("TEMPLATE_HITS_SLICE_INIT","TEMPLATE_HITS_TEST_INIT_SYSTEM"), //使用通用的生成sys模板
    hits_test_init("TEMPLATE_HITS_TEST_INIT","TEMPLATE_HITS_TEST_INIT_SYSTEM"),
    hits_test_repair("TEMPLATE_HITS_TEST_REPAIR","TEMPLATE_HITS_TEST_REPAIR_SYSTEM"),
    coverup_init("TEMPLATE_COVERUP_INIT","TEMPLATE_COVERUP_INIT_SYSTEM");
    // 成员变量
    private final String generate;
    private final String generateSystem;

    // 构造函数
    PromptFile(String generate, String generateSystem) {
        this.generate = generate;
        this.generateSystem = generateSystem;
    }

    public String getGenerate() {
        return generate;
    }

    public String getGenerateSystem() {
        return generateSystem;
    }
}
