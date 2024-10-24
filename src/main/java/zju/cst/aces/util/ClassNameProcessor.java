package zju.cst.aces.util;


import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public class ClassNameProcessor {

    private Map<String, AtomicInteger> classNameCountMap = new HashMap<>();
    private Map<String, AtomicInteger> enumNameCountMap = new HashMap<>();

    public void processJavaFiles(Path testPath) throws IOException {
        try (Stream<Path> paths = Files.walk(testPath)) {
            // Filter Java files and process each one
            paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().endsWith(".java"))
                    .forEach(this::processJavaFile);
        }
    }

    private void processJavaFile(Path javaFilePath) {
        try {
            final String[] content = {new String(Files.readAllBytes(javaFilePath))};
            CompilationUnit compilationUnit = StaticJavaParser.parse(content[0]);

            // Process classes
            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> !c.getNameAsString().endsWith("Test") && !c.getNameAsString().endsWith("Suite"))
                    .forEach(c -> content[0] = renameAndTrack(c.getNameAsString(), classNameCountMap, content[0]));

            // Process enums
            compilationUnit.findAll(EnumDeclaration.class).stream()
                    .filter(e -> !e.getNameAsString().endsWith("Test") && !e.getNameAsString().endsWith("Suite"))
                    .forEach(e -> content[0] = renameAndTrack(e.getNameAsString(), enumNameCountMap, content[0]));

            Files.write(javaFilePath, content[0].getBytes());

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String renameAndTrack(String originalName, Map<String, AtomicInteger> nameCountMap, String content) {
        nameCountMap.putIfAbsent(originalName, new AtomicInteger(0));
        int count = nameCountMap.get(originalName).incrementAndGet();
        String newName = originalName + "_" + count;

        // Replace class or enum name in the content, but skip import statements
        StringBuilder newContent = new StringBuilder();
        String[] lines = content.split("\n");
        for (String line : lines) {
            if (line.startsWith("import ")) {
                newContent.append(line).append("\n");
            } else {
                newContent.append(line.replaceAll("\\b" + originalName + "\\b", newName)).append("\n");
            }
        }
        return newContent.toString();
    }

    public static void main(String[] args) {
        try {
            Path testPath = Paths.get("D:\\APP\\IdeaProjects\\commons-lang\\src\\test\\java\\org\\apache\\commons\\lang3");
            ClassNameProcessor processor = new ClassNameProcessor();
            processor.processJavaFiles(testPath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}