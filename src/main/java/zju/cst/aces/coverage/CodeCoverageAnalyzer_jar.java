package zju.cst.aces.coverage;

import zju.cst.aces.api.config.Config;

import java.io.*;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CodeCoverageAnalyzer_jar {
    public Map<String, Object> analyzeCoverage(String testSourceCode, String targetTestName, String targetClassName, String methodSignature, String targetClassCompiledDir, String targetClassSourceDir, List<String> dependencies, Config config) throws Exception {

        // 将测试代码保存到一个临时文件中
        File tempFile = File.createTempFile("testSourceCode", ".java");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(tempFile))) {
            writer.write(testSourceCode);
        }

        List<String> command = new ArrayList<>();
        command.add("java");
        command.add("-cp");
        // 加载jar包
//        command.add(config.coverageAnalyzer_jar_path);
        command.add("zju.cst.aces.CodeCoverageAnalyzerRunner");

        // Adding parameters, wrapping those with spaces in quotes
        command.add(tempFile.getAbsolutePath());
        command.add(wrapInQuotes(targetTestName));
        command.add(wrapInQuotes(targetClassName));
        command.add(wrapInQuotes(methodSignature));
        command.add(wrapInQuotes(targetClassCompiledDir));
        command.add(wrapInQuotes(targetClassSourceDir));

        // Adding dependencies
        for (String dependency : dependencies) {
            command.add(wrapInQuotes(dependency));
        }

        ProcessBuilder processBuilder = new ProcessBuilder(command);
        Process process = null;

        try {
            process = processBuilder.start();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 PrintWriter writer = new PrintWriter(process.getOutputStream())) {

                String line;
                double lineCoverage = 0.0;
                StringBuilder methodCode = new StringBuilder();
                List<String> uncoveredLines = new ArrayList<>();
                Map<String, Object> resultMap = new HashMap<>();

                while ((line = reader.readLine()) != null) {
                    // 解析输出结果
                    if (line.startsWith("lineCoverage=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            lineCoverage = Double.parseDouble(parts[1]);
                        }
                    } else if (line.startsWith("methodCode=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            methodCode.append(parts[1]).append("\n");
                        }
                    } else if (line.startsWith("uncoveredLines=")) {
                        String[] parts = line.split("=");
                        if (parts.length > 1) {
                            String[] lines = parts[1].split(",");
                            for (String uncoveredLine : lines) {
                                uncoveredLines.add(uncoveredLine);
                            }
                        }
                    } else {
                        methodCode.append(line).append("\n");
                    }
                }

                resultMap.put("lineCoverage", lineCoverage);
                resultMap.put("methodCode", methodCode);
                resultMap.put("uncoveredLines", uncoveredLines);
                return resultMap;
            }
        } finally {
            if (process != null) {
                process.destroy();
            }
            // 删除临时文件
            tempFile.delete();
        }
    }

    private String wrapInQuotes(String input) {
        if (input.contains(" ")) {
            return "\"" + input + "\"";
        }
        return input;
    }

    private String getResourcePath(String resource) throws IOException {
        URL resourceUrl = getClass().getClassLoader().getResource(resource);
        if (resourceUrl == null) {
            throw new FileNotFoundException("Resource not found: " + resource);
        }
        return new File(resourceUrl.getFile()).getAbsolutePath();
    }

}
