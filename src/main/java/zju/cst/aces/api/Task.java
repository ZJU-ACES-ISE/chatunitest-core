package zju.cst.aces.api;

import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.parser.ProjectParser;
import zju.cst.aces.runner.AbstractRunner;
import zju.cst.aces.util.Counter;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Task {

    Config config;
    Logger log;
    Runner runner;

    public Task(Config config, Runner runner) {
        this.config = config;
        this.log = config.getLogger();
        this.runner = runner;
    }

    public void startMethodTask(String className, String methodName) {
        try {
            checkTargetFolder(config.getProject());
        } catch (RuntimeException e) {
            log.error(e.toString());
            return;
        }
        if (config.getProject().getPackaging().equals("pom")) {
            log.info(String.format("\n==========================\n[%s] Skip pom-packaging ...",config.pluginSign));
            return;
        }

        Phase phase = new Phase(config);
        phase.new Preparation().execute();

        log.info(String.format("\n==========================\n[%s] Generating tests for class: < ",config.pluginSign) + className
                + "> method: < " + methodName + " > ...");

        try {
            String fullClassName = getFullClassName(config, className);
            ClassInfo classInfo = AbstractRunner.getClassInfo(config, fullClassName);
            MethodInfo methodInfo = null;
            if (methodName.matches("\\d+")) { // use method id instead of method name
                String methodId = methodName;
                for (String mSig : classInfo.methodSigs.keySet()) {
                    if (classInfo.methodSigs.get(mSig).equals(methodId)) {
                        methodInfo = AbstractRunner.getMethodInfo(config, classInfo, mSig);
                        break;
                    }
                }
                if (methodInfo == null) {
                    throw new IOException("Method " + methodName + " in class " + fullClassName + " not found");
                }
                try {
                    this.runner.runMethod(fullClassName, methodInfo);
                } catch (Exception e) {
                    log.error("Error when generating tests for " + methodName + " in " + className + " " + config.getProject().getArtifactId() + "\n" + e.getMessage());
                }
            } else {
                for (String mSig : classInfo.methodSigs.keySet()) {
                    if (mSig.split("\\(")[0].equals(methodName)) {
                        methodInfo = AbstractRunner.getMethodInfo(config, classInfo, mSig);
                        if (methodInfo == null) {
                            throw new IOException("Method " + methodName + " in class " + fullClassName + " not found");
                        }
                        try {
                            this.runner.runMethod(fullClassName, methodInfo);
                            return;
                        } catch (Exception e) {
                            log.error("Error when generating tests for " + methodName + " in " + className + " " + config.getProject().getArtifactId() + "\n" + e.getMessage());
                        }
                    }
                }
            }
            throw new IOException("Method " + methodName + " in class " + fullClassName + " not found");
        } catch (IOException e) {
            log.warn("Method not found: " + methodName + " in " + className + " " + config.getProject().getArtifactId());
        } finally {
            log.info(String.format("\n==========================\n[%s] Generation finished", config.pluginSign));
        }
    }

    public void startClassTask(String className) {
        try {
            checkTargetFolder(config.getProject());
        } catch (RuntimeException e) {
            log.error(e.toString());
            return;
        }
        if (config.getProject().getPackaging().equals("pom")) {
            log.info(String.format("\n==========================\n[%s] Skip pom-packaging ...",config.pluginSign));
            return;
        }
        Phase phase = new Phase(config);
        phase.new Preparation().execute();
        log.info(String.format("\n==========================\n[%s] Generating tests for class < " + className + " > ...",config.pluginSign));
        try {
            this.runner.runClass(getFullClassName(config, className));
        } catch (IOException e) {
            log.warn("Class not found: " + className + " in " + config.getProject().getArtifactId());
        }
        log.info(String.format("\n==========================\n[%s] Generation finished",config.pluginSign));
    }

    public void startProjectTask() {
        Project project = config.getProject();
        try {
            checkTargetFolder(project);
        } catch (Exception e) {
            log.error(e.toString());
            return;
        }
        if (project.getPackaging().equals("pom")) {
            log.info(String.format("\n==========================\n[%s] Skip pom-packaging ...",config.pluginSign));
            return;
        }
        Phase phase = new Phase(config);
        phase.new Preparation().execute();
        List<String> classPaths = ProjectParser.scanSourceDirectory(project);

        try {
            config.setJobCount(new AtomicInteger(Counter.countMethod(config.getTmpOutput())));
        } catch (IOException e) {
            log.error("Error when counting methods: " + e);
        }

        if (config.isEnableMultithreading() == true) {
            projectJob(classPaths);
        } else {
            for (String classPath : classPaths) {
                String className = classPath.substring(classPath.lastIndexOf(File.separator) + 1, classPath.lastIndexOf("."));
                try {
                    String fullClassName = getFullClassName(config, className);
                    log.info(String.format("\n==========================\n[%s] Generating tests for class < ",config.pluginSign) + className + " > ...");
                    ClassInfo info = AbstractRunner.getClassInfo(config, fullClassName);
                    if (!Counter.filter(info)) {
                        config.getLogger().info("Skip class: " + classPath);
                        continue;
                    }

                    this.runner.runClass(fullClassName);
                } catch (IOException e) {
                    log.error(String.format("[%s] Generate tests for class ",config.pluginSign) + className + " failed: " + e);
                }
            }
        }

        log.info(String.format("\n==========================\n[%s] Generation finished",config.pluginSign));
    }

    public void projectJob(List<String> classPaths) {
        ExecutorService executor = Executors.newFixedThreadPool(config.getClassThreads());
        List<Future<String>> futures = new ArrayList<>();
        for (String classPath : classPaths) {
            Callable<String> callable = new Callable<String>() {
                @Override
                public String call() throws Exception {
                    String className = classPath.substring(classPath.lastIndexOf(File.separator) + 1, classPath.lastIndexOf("."));
                    try {
                        String fullClassName = getFullClassName(config, className);
                        log.info(String.format("\n==========================\n[%s] Generating tests for class < ",config.pluginSign) + className + " > ...");
                        ClassInfo info = AbstractRunner.getClassInfo(config, fullClassName);
                        if (!Counter.filter(info)) {
                            return "Skip class: " + classPath;
                        }
                        runner.runClass(fullClassName);
                    } catch (IOException e) {
                        log.error(String.format("[%s] Generate tests for class ",config.pluginSign) + className + " failed: " + e);
                    }
                    return "Processed " + classPath;
                }
            };
            Future<String> future = executor.submit(callable);
            futures.add(future);
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            public void run() {
                executor.shutdownNow();
            }
        });

        for (Future<String> future : futures) {
            try {
                String result = future.get();
                System.out.println(result);
            } catch (InterruptedException | ExecutionException e) {
                e.printStackTrace();
            }
        }

        executor.shutdown();
    }

    public static String getFullClassName(Config config, String name) throws IOException {
        if (isFullName(name)) {
            return name;
        }
        Path classMapPath = config.getClassNameMapPath();
        Map<String, List<String>> classMap = config.getGSON().fromJson(Files.readString(classMapPath, StandardCharsets.UTF_8), Map.class);
        if (classMap.containsKey(name)) {
            if (classMap.get(name).size() > 1) {
                throw new RuntimeException((String.format("[%s] Multiple classes Named ",config.pluginSign)) + name + ": " + classMap.get(name)
                        + " Please use full qualified name!");
            }
            return classMap.get(name).get(0);
        }
        return name;
    }

    public static boolean isFullName(String name) {
        if (name.contains(".")) {
            return true;
        }
        return false;
    }

    /**
     * Check if the classes is compiled
     * @param project
     */
    public static void checkTargetFolder(Project project) {
        if (project.getPackaging().equals("pom")) {
            return;
        }
        if (!new File(project.getBuildPath().toString()).exists()) {
            throw new RuntimeException("In ProjectTestMojo.checkTargetFolder: " +
                    "The project is not compiled to the target directory. " +
                    "Please run 'mvn install' first.");
        }
    }
}
