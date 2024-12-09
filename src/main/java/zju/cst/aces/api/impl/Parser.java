package zju.cst.aces.api.impl;

import lombok.Data;
import zju.cst.aces.api.*;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.parser.ProjectParser;

import zju.cst.aces.api.PreProcess;

import java.nio.file.Path;

@Data
public class Parser implements PreProcess {

    ProjectParser parser;

    Project project;
    Path parseOutput;
    Logger log;

    Config config;

    public Parser(ProjectParser parser, Project project, Path parseOutput, Logger log) {
        this.parser = parser;
        this.project = project;
        this.parseOutput = parseOutput;
        this.log = log;
        this.config = ProjectParser.config;
    }

    @Override
    public void process() {
        this.parse();
    }

    public void parse() {
        try {
            Task.checkTargetFolder(project);
        } catch (RuntimeException e) {
            getLog().error(e.toString());
            return;
        }
        if (project.getPackaging().equals("pom")) {
            log.info(String.format("\n==========================\n[%s] Skip pom-packaging ...", config.pluginSign));
            return;
        }
        if (!parseOutput.toFile().exists()) {
            log.info(String.format("\n==========================\n[%s] Parsing class info ...", config.pluginSign));
            parser.parse();
            log.info(String.format("\n==========================\n[%s] Parse finished", config.pluginSign));
        } else {
            log.info(String.format("\n==========================\n[%s] Parse output already exists, skip parsing!", config.pluginSign));
        }
    }
}
