package zju.cst.aces.api.impl;

import zju.cst.aces.util.LogFormatter;

import java.util.logging.ConsoleHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

public class LoggerImpl implements zju.cst.aces.api.Logger {

    java.util.logging.Logger log;

    public LoggerImpl() {
        this.log = java.util.logging.Logger.getLogger("ChatUniTest");
        Handler consoleHandler = new ConsoleHandler();
        consoleHandler.setLevel(Level.ALL);
        consoleHandler.setFormatter(new LogFormatter());
        this.log.addHandler(consoleHandler);
        this.log.setUseParentHandlers(false);
    }

    @Override
    public void info(String msg) {
        log.info(msg);
    }

    @Override
    public void warn(String msg) {
        log.warning(msg);
    }

    @Override
    public void error(String msg) {
        log.severe(msg);
    }

    @Override
    public void debug(String msg) {
        log.config(msg);
    }
}
