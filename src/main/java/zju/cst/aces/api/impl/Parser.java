package zju.cst.aces.api.impl;

import lombok.Data;
import zju.cst.aces.api.PreProcess;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.parser.ProjectParser;

import zju.cst.aces.api.PreProcess;

@Data
public class Parser implements PreProcess {

    ProjectParser parser;

    Config config;

    public Parser(Config config) {
        this.config = config;
        this.parser = new ProjectParser(config);
    }

    @Override
    public void process() {
        this.parse();
    }

    public void parse() {
        if (! config.getParseOutput().toFile().exists()) {
            config.getLog().info("\n==========================\n[ChatTester] Parsing class info ...");
            parser.parse();
            config.getLog().info("\n==========================\n[ChatTester] Parse finished");
        } else {
            config.getLog().info("\n==========================\n[ChatTester] Parse output already exists, skip parsing!");
        }
    }
}
