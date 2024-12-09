package zju.cst.aces.api.phase.step;

import zju.cst.aces.api.PreProcess;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.api.impl.Parser;
import zju.cst.aces.parser.ProjectParser;

public  class Preparation {
    private final Config config;

    public Preparation(Config config) {
        this.config = config;
    }

    public void execute() {
        Parser parser = new Parser(new ProjectParser(config), config.getProject(), config.getParseOutput(), config.getLogger());
        process(parser);
    }

    public void process(PreProcess preProcessor) {
        preProcessor.process();
    }
}
