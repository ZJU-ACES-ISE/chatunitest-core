package zju.cst.aces.util;

import lombok.Data;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import org.codehaus.plexus.util.FileUtils;
import org.junit.platform.engine.TestEngine;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherConfig;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.TestMessage;
import zju.cst.aces.parser.ProjectParser;

import javax.tools.*;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

@Data
public class TestCompiler {
    public static String OS = System.getProperty("os.name").toLowerCase();
    public static File srcTestFolder = new File("src" + File.separator + "test" + File.separator + "java");
    public static File testBackupFolder = new File("src" + File.separator + "backup");
    public static File testOutputFolder;
    public static File buildFolder;
    public static File targetTestsFolder;
    public static File buildBackupFolder;
    public List<String> classpathElements;
    public String testName;
    public String fullTestName;
    public String code;

    public TestCompiler(Path testOutputPath, Path compileOutputPath, Path targetPath, List<String> classpathElements) {
        this.code = "";
        this.testOutputFolder = testOutputPath.toFile();
        this.buildFolder = compileOutputPath.toFile();
        this.buildBackupFolder = targetPath.resolve("test-classes-backup").toFile();
        this.targetTestsFolder = targetPath.resolve("test-classes").toFile();
        this.classpathElements = classpathElements;
    }
    public TestCompiler(String code, Path testOutputPath, Path compileOutputPath, Path targetPath, List<String> classpathElements) {
        this.code = code;
        this.testOutputFolder = testOutputPath.toFile();
        this.buildFolder = compileOutputPath.toFile();
        this.buildBackupFolder = targetPath.resolve("test-classes-backup").toFile();
        this.targetTestsFolder = targetPath.resolve("test-classes").toFile();
        this.classpathElements = classpathElements;
    }

    public TestExecutionSummary executeTest(String fullTestName) {
        this.fullTestName = fullTestName;
        try {
            List<URL> urls = new ArrayList<>();
            for (String classpath : this.classpathElements) {
                URL url = new File(classpath).toURI().toURL();
                urls.add(url);
            }
            urls.add(this.buildFolder.toURI().toURL());
            ClassLoader classLoader = new URLClassLoader(urls.toArray(new URL[0]), getClass().getClassLoader());

            // Use the ServiceLoader API to load TestEngine implementations
            ServiceLoader<TestEngine> testEngineServiceLoader = ServiceLoader.load(TestEngine.class, classLoader);

            // Create a LauncherConfig with the TestEngines from the ServiceLoader
            LauncherConfig launcherConfig = LauncherConfig.builder()
                    .enableTestEngineAutoRegistration(false)
                    .enableTestExecutionListenerAutoRegistration(false)
                    .addTestEngines(testEngineServiceLoader.findFirst().orElseThrow())
                    .build();

            Launcher launcher = LauncherFactory.create(launcherConfig);

            // Register a listener to collect test execution results.
            SummaryGeneratingListener listener = new SummaryGeneratingListener();
            launcher.registerTestExecutionListeners(listener);

            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                    .selectors(selectClass(classLoader.loadClass(fullTestName)))
                    .build();
            launcher.execute(request);

            TestExecutionSummary summary = listener.getSummary();
            return summary;
        } catch (Exception e) {
            throw new RuntimeException("In TestCompiler.executeTest: " + e);
        }
    }

    /**
     * Compile test file
     */
    public boolean compileTest(String className, Path outputPath, PromptInfo promptInfo) {
        if (this.code == "") {
            throw new RuntimeException("In TestCompiler.compileTest: code is empty");
        }
        this.testName = className;
        boolean result;
        try {
            if (!outputPath.toAbsolutePath().getParent().toFile().exists()) {
                outputPath.toAbsolutePath().getParent().toFile().mkdirs();
            }
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            StandardJavaFileManager fileManager = compiler.getStandardFileManager(null, null, null);

            SimpleJavaFileObject sourceJavaFileObject = new SimpleJavaFileObject(URI.create(className + ".java"),
                    JavaFileObject.Kind.SOURCE){
                public CharBuffer getCharContent(boolean b) {
                    return CharBuffer.wrap(code);
                }
            };

            Iterable<? extends JavaFileObject> compilationUnits = Arrays.asList(sourceJavaFileObject);
            Iterable<String> options = Arrays.asList("-classpath", String.join(this.OS.contains("win") ? ";" : ":", this.classpathElements),
                    "-d", buildFolder.toPath().toString());

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, compilationUnits);

            result = task.call();
            if (!result && promptInfo != null) {
                TestMessage testMessage = new TestMessage();
                List<String> errors = new ArrayList<>();
                diagnostics.getDiagnostics().forEach(diagnostic -> {
                    errors.add("Error in " + testName +
                            ": line " + diagnostic.getLineNumber() + " : "
                            + diagnostic.getMessage(null));
                });
                testMessage.setErrorType(TestMessage.ErrorType.COMPILE_ERROR);
                testMessage.setErrorMessage(errors);
                promptInfo.setErrorMsg(testMessage);

                exportError(errors, outputPath);
            }
        } catch (Exception e) {
            throw new RuntimeException("In TestCompiler.compileTest: " + e);
        }
        return result;
    }

    public void exportError(List<String> errors, Path outputPath) {
        try {
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputPath.toFile()));
            writer.write(code);
            writer.write("\n--------------------------------------------\n");
            writer.write(errors.stream().collect(Collectors.joining("\n")));
            writer.close();
        } catch (Exception e) {
            throw new RuntimeException("In TestCompiler.exportError: " + e);
        }
    }

    //TODO: only support MavenProject
    public static List<String> listClassPaths(MavenProject project, DependencyGraphBuilder dependencyGraphBuilder) {
        List<String> classPaths = new ArrayList<>();
        Path artifactPath = Paths.get(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName() + ".jar");
        if (!artifactPath.toFile().exists()) {
            throw new RuntimeException("In TestCompiler.listClassPaths: " + artifactPath + " does not exist. Run mvn install first.");
        }
        classPaths.add(artifactPath.toString());
        try {
            classPaths.addAll(project.getCompileClasspathElements());
            Class<?> clazz = project.getClass();
            Field privateField = clazz.getDeclaredField("projectBuilderConfiguration");
            privateField.setAccessible(true);
            ProjectBuildingRequest buildingRequest = new DefaultProjectBuildingRequest((DefaultProjectBuildingRequest) privateField.get(project));
            buildingRequest.setProject(project);
            DependencyNode root = dependencyGraphBuilder.buildDependencyGraph(buildingRequest, null);
            Set<DependencyNode> depSet = new HashSet<>();
            ProjectParser.walkDep(root, depSet);
            for (DependencyNode dep : depSet) {
                if (dep.getArtifact().getFile() != null) {
                    classPaths.add(dep.getArtifact().getFile().getAbsolutePath());
                }
            }
        } catch (Exception e) {
            System.out.println(e);
        }
        return classPaths;
    }

    /**
     * Copy generated tests to src/test/java and move the original src/test/java folder to a backup folder
     */
    public void copyAndBackupTestFolder() {
        restoreBackupFolder();
        if (srcTestFolder.exists()) {
            try {
                FileUtils.copyDirectoryStructure(srcTestFolder, testBackupFolder);
                FileUtils.deleteDirectory(srcTestFolder);
                FileUtils.copyDirectoryStructure(this.testOutputFolder, srcTestFolder);
            } catch (IOException e) {
                throw new RuntimeException("In TestCompiler.copyAndBackupTestFolder: " + e);
            }
        }
    }

    /**
     * Copy compiled generated tests to target/test-classes and move the original folder to a backup folder
     */
    public void copyAndBackupCompiledTest() {
        File target = this.targetTestsFolder;
        try {
            if (!buildBackupFolder.exists() && target.exists()) {
                FileUtils.copyDirectoryStructure(target, buildBackupFolder);
                FileUtils.deleteDirectory(target);
            }
            FileUtils.copyDirectoryStructure(buildFolder, target);
        } catch (IOException e) {
            throw new RuntimeException("In TestCompiler.copyAndBackupCompiledTest: " + e);
        }
    }

    /**
     * Restore the backup folder to src/test/java
     */
    public void restoreBackupFolder() {
        if (testBackupFolder.exists()) {
            try {
                if (srcTestFolder.exists()) {
                    FileUtils.deleteDirectory(srcTestFolder);
                }
                FileUtils.copyDirectoryStructure(testBackupFolder, srcTestFolder);
                FileUtils.deleteDirectory(testBackupFolder);
            } catch (IOException e) {
                throw new RuntimeException("In TestCompiler.restoreTestFolder: " + e);
            }
        }
        if (buildBackupFolder.exists()) {
            File target = this.targetTestsFolder;
            try {
                if (target.exists()) {
                    FileUtils.deleteDirectory(target);
                }
                FileUtils.copyDirectoryStructure(buildBackupFolder, target);
                FileUtils.deleteDirectory(buildBackupFolder);
            } catch (IOException e) {
                throw new RuntimeException("In TestCompiler.restoreTestFolder: " + e);
            }
        }
    }
}
