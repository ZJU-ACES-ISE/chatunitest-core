package zju.cst.aces.api;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

public interface Project {
    Project getParent();
    File getBasedir();
    /**
     * Get the project packaging type.
     */
    String getPackaging();
    String getGroupId();
    String getArtifactId();
    List<String> getCompileSourceRoots();
    Path getArtifactPath();
    Path getBuildPath();
    List<String> getClassPaths();

}
