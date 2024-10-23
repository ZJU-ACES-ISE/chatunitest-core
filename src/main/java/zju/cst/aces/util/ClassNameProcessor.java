package zju.cst.aces.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ClassNameProcessor {

    private static final Pattern INNER_CLASS_PATTERN = Pattern.compile("^(?!.*\\bpublic\\b).*\\bclass\\s+(\\w+)");
    private static final Pattern ENUM_PATTERN = Pattern.compile("\\benum\\s+(\\w+)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+");
    private static final Pattern COMMENT_PATTERN = Pattern.compile("//");

    public static void main(String[] args) throws IOException {
        String directoryPath = "D:\\APP\\IdeaProjects\\commons-lang\\src\\test\\java\\org\\apache\\commons\\lang3"; // use your path
        Path path = Paths.get(directoryPath);
        proccess(path);
    }

    public static void proccess(Path testPath) throws IOException {
        Map<String, Integer> classNameCount = new HashMap<>();
        Map<String, Integer> enumNameCount = new HashMap<>();

        // First pass: count occurrences of inner classes and enums
        Files.walk(testPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> countClassesAndEnums(path, classNameCount, enumNameCount));

        // Second pass: rename and replace classes and enums
        Files.walk(testPath)
                .filter(Files::isRegularFile)
                .filter(path -> path.toString().endsWith(".java"))
                .forEach(path -> renameClassesAndEnums(path, classNameCount, enumNameCount));
    }


    private static void countClassesAndEnums(Path path, Map<String, Integer> classNameCount, Map<String, Integer> enumNameCount) {
        try {
            List<String> lines = Files.readAllLines(path);
            for (String line : lines) {
                int commentIndex = line.indexOf("//");
                String codePart = commentIndex != -1 ? line.substring(0, commentIndex) : line;

                Matcher innerClassMatcher = INNER_CLASS_PATTERN.matcher(codePart);
                if (innerClassMatcher.find()) {
                    String className = innerClassMatcher.group(1);
                    if (!className.endsWith("Test")) { // Ignore inner classes with suffix 'Test'
                        classNameCount.put(className, classNameCount.getOrDefault(className, 0) + 1);
                    }
                }

                Matcher enumMatcher = ENUM_PATTERN.matcher(codePart);
                if (enumMatcher.find()) {
                    String enumName = enumMatcher.group(1);
                    enumNameCount.put(enumName, enumNameCount.getOrDefault(enumName, 0) + 1);
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void renameClassesAndEnums(Path path, Map<String, Integer> classNameCount, Map<String, Integer> enumNameCount) {
        try {
            List<String> lines = Files.readAllLines(path);
            Map<String, String> classNameMapping = new HashMap<>();
            int classCounter = 1;

            for (String line : lines) {
                int commentIndex = line.indexOf("//"); // Processing annotations
                String codePart = commentIndex != -1 ? line.substring(0, commentIndex) : line;

                Matcher innerClassMatcher = INNER_CLASS_PATTERN.matcher(codePart);
                if (innerClassMatcher.find()) {
                    String className = innerClassMatcher.group(1);
                    if (!className.endsWith("Test")) { // // Ignore inner classes with suffix 'Test'
                        String newClassName = className + "_" + classCounter++;
                        classNameMapping.put(className, newClassName);
                    }
                }

                Matcher enumMatcher = ENUM_PATTERN.matcher(codePart);
                if (enumMatcher.find()) {
                    String enumName = enumMatcher.group(1);
                    int enumCounter = enumNameCount.get(enumName);
                    String newEnumName = enumName + "_" + enumCounter;
                    enumNameCount.put(enumName, enumCounter + 1);
                    classNameMapping.put(enumName, newEnumName);
                }
            }

            List<String> newLines = new ArrayList<>();
            for (String line : lines) {
                if (IMPORT_PATTERN.matcher(line).find()) {
                    // Keep the import line unchanged
                    newLines.add(line);
                } else {
                    for (Map.Entry<String, String> entry : classNameMapping.entrySet()) {
                        line = line.replaceAll("\\b" + entry.getKey() + "\\b", entry.getValue());
                    }
                    newLines.add(line);
                }
            }

            Files.write(path, newLines);

            // Print the renamed classes and enums
            classNameMapping.forEach((original, renamed) ->
                    System.out.println("Original: " + original + " -> Renamed: " + renamed));

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}