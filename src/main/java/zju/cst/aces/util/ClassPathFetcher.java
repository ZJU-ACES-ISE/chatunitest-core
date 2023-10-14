package zju.cst.aces.util;
import org.apache.maven.project.DependencyResolutionException;
import org.eclipse.aether.*;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.collection.DependencyCollectionException;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.util.graph.visitor.PreorderNodeListGenerator;

import java.util.Collections;
import java.util.List;

public class ClassPathFetcher {

    private final RepositorySystem system;
    private final RepositorySystemSession session;

    public ClassPathFetcher() {
        system = Booter.newRepositorySystem();
        session = Booter.newRepositorySystemSession(system);
    }

    public List<String> getClasspath(String groupId, String artifactId, String version) throws DependencyResolutionException, DependencyCollectionException, org.eclipse.aether.resolution.DependencyResolutionException {
        Dependency dependency = new Dependency(new DefaultArtifact(groupId, artifactId, "", "jar", version), "compile");

        CollectRequest collectRequest = new CollectRequest();
        collectRequest.setRoot(dependency);
        collectRequest.setRepositories(Booter.newRepositories(system, session));

        DependencyRequest dependencyRequest = new DependencyRequest();
        dependencyRequest.setCollectRequest(collectRequest);

        DependencyNode rootNode = system.collectDependencies(session, collectRequest).getRoot();
        DependencyRequest depRequest = new DependencyRequest();
        depRequest.setRoot(rootNode);

        system.resolveDependencies(session, depRequest);

        PreorderNodeListGenerator nodeGen = new PreorderNodeListGenerator();
        rootNode.accept(nodeGen);

        return Collections.singletonList(nodeGen.getClassPath());
    }

    public static void main(String[] args) {
        ClassPathFetcher resolver = new ClassPathFetcher();
        try {
            List<String> classpath = resolver.getClasspath("org.springframework.boot", "spring-boot-starter-parent", "3.0.7");
            for (String path : classpath) {
                System.out.println(path);
            }
        } catch (DependencyResolutionException e) {
            e.printStackTrace();
        } catch (DependencyCollectionException e) {
            throw new RuntimeException(e);
        } catch (org.eclipse.aether.resolution.DependencyResolutionException e) {
            throw new RuntimeException(e);
        }
    }
}
