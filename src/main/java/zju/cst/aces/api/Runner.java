package zju.cst.aces.api;

import zju.cst.aces.config.Config;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.runner.ClassRunner;
import zju.cst.aces.runner.MethodRunner;

import java.io.IOException;

public class Runner {
    Config config;

    public Runner(Config config) {
        this.config = config;
    }

    public void runClass(String fullClassName) throws IOException {
        new ClassRunner(fullClassName, config).start();
    }

    public void runMethod(String fullClassName, MethodInfo methodInfo) throws IOException {
        new MethodRunner(fullClassName, config, methodInfo).start();
    }

}
