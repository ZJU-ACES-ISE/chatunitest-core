package zju.cst.aces.api.impl;

import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.shared.dependency.graph.DependencyGraphBuilder;
import org.apache.maven.shared.dependency.graph.DependencyNode;
import zju.cst.aces.api.Project;
import zju.cst.aces.parser.ProjectParser;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ProjectImpl implements Project {

    MavenProject project;
    List<String> classPaths;

    public ProjectImpl(MavenProject project) {
        this.project = project;
    }

    public ProjectImpl(MavenProject project, List<String> classPaths) {
        this.project = project;
        this.classPaths = classPaths;
    }

    @Override
    public Project getParent() {
        if (project.getParent() == null) {
            return null;
        }
        return new ProjectImpl(project.getParent());
    }

    @Override
    public File getBasedir() {
        return project.getBasedir();
    }

    @Override
    public String getPackaging() {
        return project.getPackaging();
    }

    @Override
    public String getGroupId() {
        return project.getGroupId();
    }

    @Override
    public String getArtifactId() {
        return project.getArtifactId();
    }

    @Override
    public List<String> getCompileSourceRoots() {
        return project.getCompileSourceRoots();
    }

    @Override
    public Path getArtifactPath() {
        return Paths.get(project.getBuild().getDirectory()).resolve(project.getBuild().getFinalName() + ".jar");
    }

    @Override
    public Path getBuildPath() {
        return Paths.get(project.getBuild().getOutputDirectory());
    }

    @Override
    public List<String> getClassPaths() {
        return this.classPaths;
    }
}
