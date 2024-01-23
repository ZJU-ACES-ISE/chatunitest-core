package zju.cst.aces.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import zju.cst.aces.api.config.Config;
import zju.cst.aces.dto.ClassInfo;
import zju.cst.aces.dto.MethodInfo;
import zju.cst.aces.parser.ClassParser;
import zju.cst.aces.parser.ProjectParser;
import zju.cst.aces.runner.MethodRunner;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Random;

public class Counter {
    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public static void main(String[] args) throws IOException {
        String classInfoPath = "/private/tmp/chatunitest-info/commons-cli/class-info";
        String allMethodCsvPath = "/private/tmp/chatunitest-info/commons-csv/focal_methods_all.csv";
        String sampledCsvPath = "/private/tmp/chatunitest-info/commons-csv/focal_methods_sampled.csv";
        int sampleSize = 10; // Specify the desired sample size
        countClassMethod(Paths.get(classInfoPath), allMethodCsvPath);
        sampleMethods(allMethodCsvPath, sampledCsvPath, sampleSize);
    }

    public static void sampleMethods(String inputCsvPath, String sampledCsvPath, int sampleSize) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(inputCsvPath));

        // Skip header line
        List<String> dataLines = lines.subList(1, lines.size());

        // Shuffle data lines randomly
        Collections.shuffle(dataLines, new Random());

        // Take a sample of the specified size
        List<String> sampledLines = dataLines.subList(0, Math.min(sampleSize, dataLines.size()));

        // Write sampled data to a new CSV file
        try (FileWriter csvWriter = new FileWriter(sampledCsvPath)) {
            // Write header
            csvWriter.append("Class Name,Method Signature\n");

            // Write sampled data
            for (String line : sampledLines) {
                csvWriter.append(line).append("\n");
            }

            System.out.println("Sampled CSV file created successfully at: " + sampledCsvPath);
        }
    }

    public static Map<String, List<String>> countClassMethod(Path parseOutputPath) throws IOException {
        Map<String, List<String>> testMap = new HashMap<>();
        // get all json files names "class.json"
        List<String> classJsonFiles = Files.walk(parseOutputPath)
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .filter(f -> f.endsWith("class.json"))
                .collect(Collectors.toList());

        for (String classJsonFile : classJsonFiles) {
            File classInfoFile = new File(classJsonFile);
            ClassInfo classInfo = GSON.fromJson(Files.readString(classInfoFile.toPath(), StandardCharsets.UTF_8), ClassInfo.class);

            if (!filter(classInfo)) {
                continue;
            }
            List<String> methodList = new ArrayList<>();
            for (String mSig : classInfo.methodSigs.keySet()) {
                MethodInfo methodInfo = getMethodInfo(parseOutputPath, classInfo, mSig);
                if (!filter(methodInfo)) {
                    continue;
                }
                methodList.add(mSig);
            }
            testMap.put(classInfo.fullClassName, methodList);
        }

        // Print testMap
        for (String className : testMap.keySet()) {
            System.out.println("-----------------------");
            System.out.println(className + ":\n");
            testMap.get(className).forEach(m -> {
                System.out.println(m + "\n");
            });
            System.out.println("\n");
        }

        System.out.println("Total class count: " + testMap.size());
        System.out.println("Total method count: " + testMap.values().stream().mapToInt(List::size).sum());
        return testMap;
    }


    public static void countClassMethod(Path parseOutputPath, String outputCsvPath) throws IOException {
        Map<String, List<String>> testMap = new HashMap<>();

        // get all json files names "class.json"
        List<String> classJsonFiles = Files.walk(parseOutputPath)
                .filter(Files::isRegularFile)
                .map(Path::toString)
                .filter(f -> f.endsWith("class.json"))
                .collect(Collectors.toList());

        for (String classJsonFile : classJsonFiles) {
            File classInfoFile = new File(classJsonFile);
            ClassInfo classInfo = GSON.fromJson(Files.readString(classInfoFile.toPath(), StandardCharsets.UTF_8), ClassInfo.class);

            if (!filter(classInfo)) {
                continue;
            }

            List<String> methodList = new ArrayList<>();
            for (String mSig : classInfo.methodSigs.keySet()) {
                MethodInfo methodInfo = getMethodInfo(parseOutputPath, classInfo, mSig);
                if (!filter(methodInfo)) {
                    continue;
                }
                methodList.add(mSig);
            }

            testMap.put(classInfo.fullClassName, methodList);
        }

        // Write to CSV
        try (FileWriter csvWriter = new FileWriter(outputCsvPath)) {
            // Write header
            csvWriter.append("Class Name,Method Signature\n");

            // Write data
            for (String className : testMap.keySet()) {
                List<String> methods = testMap.get(className);
                for (String method : methods) {
//                    csvWriter.append(className).append(",").append(method).append("\n");
                    csvWriter.append(className).append(",\"").append(method).append("\"\n");
                }
            }

            System.out.println("CSV file created successfully at: " + outputCsvPath);
        }

        System.out.println("Total class count: " + testMap.size());
        System.out.println("Total method count: " + testMap.values().stream().mapToInt(List::size).sum());
    }

    public static MethodInfo getMethodInfo(Path parseOutputPath, ClassInfo info, String mSig) throws IOException {
        String packagePath = info.getPackageName()
                .replace("package ", "")
                .replace(".", File.separator)
                .replace(";", "");
        Path depMethodInfoPath = parseOutputPath
                .resolve(packagePath)
                .resolve(info.className)
                .resolve(ClassParser.getFilePathBySig(mSig, info));
        if (!depMethodInfoPath.toFile().exists()) {
            return null;
        }
        return GSON.fromJson(Files.readString(depMethodInfoPath, StandardCharsets.UTF_8), MethodInfo.class);
    }

    public static boolean filter(ClassInfo classInfo) {
        if (!classInfo.isPublic || classInfo.isAbstract || classInfo.isInterface) {
            return false;
        }
        return true;
    }

    public static boolean filter(MethodInfo methodInfo) {
        if (methodInfo == null
                || methodInfo.isConstructor || methodInfo.isGetSet || methodInfo.isBoolean || !methodInfo.isPublic) {
            return false;
        }
        return true;
    }
}
