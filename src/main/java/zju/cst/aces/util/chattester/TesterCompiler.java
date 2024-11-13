package zju.cst.aces.util.chattester;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.comments.LineComment;
import zju.cst.aces.dto.PromptInfo;
import zju.cst.aces.dto.TestMessage;
import zju.cst.aces.util.TestCompiler;

import javax.tools.*;
import java.net.URI;
import java.nio.CharBuffer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class TesterCompiler extends TestCompiler {

    public TesterCompiler(Path testOutputPath, Path compileOutputPath, Path targetPath, List<String> classpathElements) {
        super(testOutputPath, compileOutputPath, targetPath, classpathElements);
    }

    @Override
    public boolean compileTest(String className, Path outputPath, PromptInfo promptInfo) {
        if (this.code.isEmpty()) {
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
            Iterable<String> options = Arrays.asList("-classpath", String.join(OS.contains("win") ? ";" : ":", this.classpathElements),
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
                    promptInfo.setUnitTest(addBuggyPrompt(promptInfo.getUnitTest(),"<Buggy Line>: " + diagnostic.getMessage(null), (int) diagnostic.getLineNumber()));
                });
                promptInfo.setUnitTest(promptInfo.getUnitTest().replace("//<Buggy Line>", "<Buggy Line>"));
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

    public String addBuggyPrompt(String code, String errorMsg, int lineNum) {
        CompilationUnit cu = StaticJavaParser.parse(code);

        Optional<Node> firstNodeAfterLine = cu.findFirst(Node.class, node ->
                node.getBegin().isPresent() && node.getBegin().get().line >= lineNum);

        firstNodeAfterLine.ifPresent(node -> {
            node.setComment(new LineComment(errorMsg));
        });
        return cu.toString();
    }
}
