package zju.cst.aces.api;

import lombok.Data;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import java.util.logging.Logger;

import zju.cst.aces.config.Config;

@Data
public class Configuration {

    Config config;

    public Configuration(MavenSession session, MavenProject project, DependencyGraphBuilder dependencyGraphBuilder, Logger log) {
        this.config = new Config.ConfigBuilder(session, project, dependencyGraphBuilder, log)
                .build();
    }

    public Config getConfig() {
        return config;
    }

    public void print() {
        if (isConfigured()) {
            printConfiguration();
        } else {
            throw new RuntimeException("Configuration is not set.");
        }
    }

    private void printConfiguration() {
        Logger log = config.getLog();
        log.info("\n========================== Configuration ==========================\n");
        log.info(" Multithreading >>>> " + config.isEnableMultithreading());
        if (config.isEnableMultithreading()) {
            log.info(" - Class threads: " + config.getClassThreads() + ", Method threads: " + config.getMethodThreads());
        }
        log.info(" Stop when success >>>> " + config.isStopWhenSuccess());
        log.info(" No execution >>>> " + config.isNoExecution());
        log.info(" Enable Merge >>>> " + config.isEnableMerge());
        log.info(" --- ");
        log.info(" TestOutput Path >>> " + config.getTestOutput());
        log.info(" TmpOutput Path >>> " + config.getTmpOutput());
        log.info(" Prompt path >>> " + config.getPromptPath());
        log.info(" Example path >>> " + config.getExamplePath());
        log.info(" MaxThreads >>> " + config.getMaxThreads());
        log.info(" TestNumber >>> " + config.getTestNumber());
        log.info(" MaxRounds >>> " + config.getMaxRounds());
        log.info(" MinErrorTokens >>> " + config.getMinErrorTokens());
        log.info(" MaxPromptTokens >>> " + config.getMaxPromptTokens());
        log.info(" SleepTime >>> " + config.getSleepTime());
        log.info(" DependencyDepth >>> " + config.getDependencyDepth());
        log.info("\n===================================================================\n");
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private boolean isConfigured() {
        return config != null;
    }
}
