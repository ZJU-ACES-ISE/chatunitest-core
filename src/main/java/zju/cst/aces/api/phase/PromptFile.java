package zju.cst.aces.api.phase;

public enum PromptFile {
    
    // 定义枚举常量及其对应的模板文件名
    chatunitest("TEMPLATE_INIT","TEMPLATE_INIT_SYSTEM","TEMPLATE_EXTRA","TEMPLATE_EXTRA_SYSTEM","TEMPLATE_REPAIR"),
    testpilot("TEMPLATE_TESTPILOT_INIT","TEMPLATE_INIT_SYSTEM","TEMPLATE_EXTRA","TEMPLATE_EXTRA_SYSTEM","TEMPLATE_REPAIR"),
    hits("TEMPLATE_GEN_CODE","TEMPLATE_SYS_GEN","TEMPLATE_GEN_SLICE","TEMPLATE_HITS_SYS_REPAIR","TEMPLATE_HITS_REPAIR"),
    coverup("TEMPLATE_GEN_CODE","TEMPLATE_SYS_GEN","TEMPLATE_GEN_SLICE","TEMPLATE_HITS_SYS_REPAIR","TEMPLATE_HITS_REPAIR");
    // 成员变量
    private final String init;
    private final String initSystem;
    private final String extra;
    private final String extraSystem;
    private final String repair;

    // 构造函数
    PromptFile(String init, String initSystem, String extra, String extraSystem,String repair) {
        this.init = init;
        this.initSystem = initSystem;
        this.extraSystem=extraSystem;
        this.extra = extra;
        this.repair = repair;
    }

    // Getter 方法
    public String getInit() {
        return init;
    }

    public String getInitSystem() {
        return initSystem;
    }

    public String getExtra() {
        return extra;
    }

    public String getExtraSystem(){return extraSystem;}

    public String getRepair() {
        return repair;
    }

}
