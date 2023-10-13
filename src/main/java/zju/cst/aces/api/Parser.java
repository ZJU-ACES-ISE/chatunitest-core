package zju.cst.aces.api;

import lombok.Data;
import zju.cst.aces.config.Config;
import zju.cst.aces.parser.ProjectParser;


@Data
public class Parser {

    ProjectParser parser;

    Config config;

    public Parser(Config config) {
        this.config = config;
        this.parser = new ProjectParser(config);
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
