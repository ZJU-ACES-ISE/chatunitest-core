package zju.cst.aces.api.impl;

import lombok.Data;
import zju.cst.aces.api.PreProcess;
import zju.cst.aces.api.Task;
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
        try {
            Task.checkTargetFolder(config.getProject());
        } catch (RuntimeException e) {
            config.getLog().error(e.toString());
            return;
        }
        if (config.getProject().getPackaging().equals("pom")) {
            config.getLog().info("\n==========================\n[ChatUniTest] Skip pom-packaging ...");
            return;
        }
        if (! config.getParseOutput().toFile().exists()) {
            config.getLog().info("\n==========================\n[ChatUniTest] Parsing class info ...");
            parser.parse();
            config.getLog().info("\n==========================\n[ChatUniTest] Parse finished");
        } else {
            config.getLog().info("\n==========================\n[ChatUniTest] Parse output already exists, skip parsing!");
        }
    }
}
