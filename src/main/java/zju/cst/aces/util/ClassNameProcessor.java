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
            // 检查文件是否存在且可读
            if (Files.notExists(javaFilePath) || !Files.isReadable(javaFilePath)) {
                System.err.println("File not found or not readable: " + javaFilePath);
                return;
            }

            // 读取文件内容
            final String content = new String(Files.readAllBytes(javaFilePath));
            CompilationUnit compilationUnit = StaticJavaParser.parse(content);

            // 处理类
            compilationUnit.findAll(ClassOrInterfaceDeclaration.class).stream()
                    .filter(c -> !c.getNameAsString().endsWith("Test") && !c.getNameAsString().endsWith("Suite"))
                    .forEach(c -> {
                        try {
                            renameAndTrack(c.getNameAsString(), classNameCountMap, content);
                        } catch (IllegalArgumentException e) {
                            System.err.println("Error renaming class: " + c.getNameAsString() + " in file " + javaFilePath + ". " + e.getMessage());
                        }
                    });

            // 处理枚举
            compilationUnit.findAll(EnumDeclaration.class).stream()
                    .filter(e -> !e.getNameAsString().endsWith("Test") && !e.getNameAsString().endsWith("Suite"))
                    .forEach(e -> {
                        try {
                            renameAndTrack(e.getNameAsString(), enumNameCountMap, content);
                        } catch (IllegalArgumentException e1) {
                            System.err.println("Error renaming enum: " + e.getNameAsString() + " in file " + javaFilePath + ". " + e1.getMessage());
                        }
                    });

            // 写入更新后的内容
            Files.write(javaFilePath, content.getBytes());

        } catch (IOException e) {
            System.err.println("I/O error processing file: " + javaFilePath + ". " + e.getMessage());
        }
    }

    private String renameAndTrack(String originalName, Map<String, AtomicInteger> nameCountMap, String content) {
        if (originalName == null) {
            throw new IllegalArgumentException("Original name cannot be null.");
        }

        nameCountMap.putIfAbsent(originalName, new AtomicInteger(0));
        int count = nameCountMap.get(originalName).incrementAndGet();
        String newName = originalName + "_" + count;

        // 替换类或枚举名称
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
            System.err.println("I/O error: " + e.getMessage());
        }
    }
}
