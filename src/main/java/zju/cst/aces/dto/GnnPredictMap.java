package zju.cst.aces.dto;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import zju.cst.aces.api.Project;
import zju.cst.aces.api.config.Config;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Method Example Map
 */
public class GnnPredictMap {

    public static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    Map<String, TreeSet<String>> predict;

    public GnnPredictMap(Config config) {
        loadPredictMap(config);
    }

    public void loadPredictMap(Config config) {
        this.predict = new HashMap<>();
        Path path = config.getGnnPredictPath();

        if (!path.toFile().exists()) {
            return;
        }
        try {
            String projectName = config.getProject().getArtifactId();
            Project parent = config.project.getParent();
            while(parent != null && parent.getBasedir() != null) {
                projectName = parent.getArtifactId();
                parent = parent.getParent();
            }
            this.predict = (Map<String, TreeSet<String>>) GSON.fromJson(Files.readString(path, StandardCharsets.UTF_8), Map.class).get(projectName);
        } catch (Exception e) {
            throw new RuntimeException("In GnnPredictMap.loadPredictMap: " + e);
        }
    }

    public List<String> getPredict(String fullClassName) {
        return new ArrayList<>(this.predict.get(fullClassName));
    }
}
