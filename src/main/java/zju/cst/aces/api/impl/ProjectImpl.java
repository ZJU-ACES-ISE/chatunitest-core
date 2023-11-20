package zju.cst.aces.api.impl;

import org.apache.maven.project.MavenProject;
import zju.cst.aces.api.Project;

import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class ProjectImpl implements Project {

    MavenProject project;

    public ProjectImpl(MavenProject project) {
        this.project = project;
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

}
