package zju.cst.aces.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JsonResponseProcessor {

    private static final Pattern JSON_PATTERN = Pattern.compile("```[json]*([\\s\\S]*?)```");

    // Inner class to represent the structure of the JSON data
    public static class JsonData {
        private List<String> invokedOutsideVars;
        private List<String> invokedOutsideMethods;
        private String summarization;
        private List<Step> steps;

        // 无参构造函数
        public JsonData() {}

        public List<Step> getSteps() {
            return steps;
        }

        public static class Step {
            private String desp;
            private String code;

            // 无参构造函数
            public Step() {}

            public Step(String desp, String code) {
                this.desp = desp;
                this.code = code;
            }

            public String getDesp() {
                return desp;
            }

            public String getCode() {
                return code;
            }

            @Override
            public String toString() {
                return "Step{" +
                        "desp='" + desp + '\'' +
                        ", code='" + code + '\'' +
                        '}';
            }
        }
    }

    /**
     * Extracts JSON content from a response string.
     *
     * @param response The response string containing JSON content.
     * @return The extracted JSON string or null if no JSON content is found.
     */
    public static String getJsonContentByResponse(String response) {
        Matcher matcher = JSON_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    /**
     * Extracts specific information from a JSON string and returns it as a JsonData object.
     *
     * @param jsonString The JSON string from which to extract information.
     * @return A JsonData object containing the extracted information or null if extraction fails.
     */
    public static JsonData extractInfoFromJson(String jsonString) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            JsonNode jsonNode = objectMapper.readTree(jsonString);
            // Check for required keys
            if (jsonNode.has("invoked_outside_vars") &&
                    jsonNode.has("invoked_outside_methods") &&
                    jsonNode.has("summarization") &&
                    jsonNode.has("steps")) {

                // Convert JsonNode to JsonData
                JsonData jsonData = new JsonData();
                jsonData.invokedOutsideVars = objectMapper.convertValue(jsonNode.get("invoked_outside_vars"), List.class);
                jsonData.invokedOutsideMethods = objectMapper.convertValue(jsonNode.get("invoked_outside_methods"), List.class);
                jsonData.summarization = jsonNode.get("summarization").asText();

                // Convert steps
                List<JsonData.Step> steps = new ArrayList<>();
                for (JsonNode stepNode : jsonNode.get("steps")) {
                    String desp = stepNode.get("desp").asText();
                    String code = stepNode.get("code").asText();
                    steps.add(new JsonData.Step(desp, code));
                }
                jsonData.steps = steps;

                return jsonData;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return null;
    }

    /**
     * Writes the JSON information to a file.
     *
     * @param jsonNode The JsonNode containing information to write.
     */
    public static void writeJsonToFile(JsonNode jsonNode, Path fullDirectoryPath) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.enable(SerializationFeature.INDENT_OUTPUT);

        // Create the directory structure
        File directory = new File(fullDirectoryPath.toString());
        if (!directory.exists()) {
            directory.mkdirs(); // Create the directory if it doesn't exist
        }

        // Define the file path
        Path filePath = fullDirectoryPath.resolve("slice.json");

        try (FileWriter fileWriter = new FileWriter(filePath.toFile())) {
            objectMapper.writeValue(fileWriter, jsonNode);
            System.out.println("JSON information successfully written to " + filePath);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Reads JSON data from a file and converts it to a JsonData object.
     *
     * @param filePath The path of the file to read.
     * @return A JsonData object containing the JSON data or null if reading fails.
     */
    public static JsonData readJsonFromFile(Path filePath) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readValue(filePath.toFile(), JsonData.class);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void main(String[] args) {
        // Initialize with className and methodName
        String response = "Your response string here with ```json content```.";

        // Step 1: Extract JSON content from response
        String jsonContent = getJsonContentByResponse(response);
        if (jsonContent != null) {
            // Step 2: Extract information from JSON content
            JsonData info = extractInfoFromJson(jsonContent);
            if (info != null) {
                System.out.println("Extracted JSON Info: " + info.toString());
                Path outputPath = Paths.get("output_directory"); // Specify your output directory
                // Step 3: Write the extracted JSON information to a file
                writeJsonToFile(new ObjectMapper().valueToTree(info), outputPath);

                // Step 4: Read the JSON data back from the file
                JsonData readInfo = readJsonFromFile(outputPath.resolve("slice.json"));
                if (readInfo != null) {
                    // Accessing the steps
                    for (JsonData.Step step : readInfo.getSteps()) {
                        System.out.println("Step Description: " + step.getDesp());
                        System.out.println("Step Code: " + step.getCode());
                    }
                } else {
                    System.out.println("Failed to read JSON data from file.");
                }
            } else {
                System.out.println("Failed to extract required information from JSON.");
            }
        } else {
            System.out.println("No JSON content found in the response.");
        }
    }
}