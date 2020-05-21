package com.ledig;

import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.Query;
import org.apache.maven.index.*;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.updater.*;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.*;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.aether.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.util.*;


public class AetherApp
{
    private static RepositorySystem system;
    private static RemoteRepository central;
    private static RepositorySystemSession session;

    public static void main( String[] args ) throws DependencyResolutionException, ArtifactDescriptorException {

        Artifact root = new DefaultArtifact("com.twilio.sdk:twilio:7.1.0");

        Set<Artifact> visitedSet = new HashSet<>();
        Queue<Artifact> nodeQ = new LinkedList<>();
        nodeQ.add(root);

        // Setup + helper functions adapted from org.netbeans.modules.maven.indexer.ArtifactDependencyIndexCreator
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
        system = newRepositorySystem(locator);
        session = newSession(system);
        central = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

        while (!nodeQ.isEmpty()) {
            Artifact curArt = nodeQ.remove();

            System.out.println("Visiting artifact: " + curArt.toString());

            List<Dependency> dependencies = getDependencies(curArt);
            for (Dependency nextDep : dependencies) {
                if (visitedSet.contains(nextDep.getArtifact())) {
                    System.out.println("*** Encountered duplicate artifact: " + curArt);
                } else {
                    nodeQ.add(nextDep.getArtifact());
                    visitedSet.add(nextDep.getArtifact());
                }
            }
        }

    }

    private static List<Dependency> getDependencies(Artifact artifact) throws ArtifactDescriptorException {
        ArtifactDescriptorRequest artRequest = new ArtifactDescriptorRequest(artifact, Arrays.asList(central), null);
        ArtifactDescriptorResult artResult = system.readArtifactDescriptor(session, artRequest);
        return artResult.getDependencies();
    }

    private static RepositorySystem newRepositorySystem(DefaultServiceLocator locator) {
        locator.addService(RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class);
        locator.addService(TransporterFactory.class, FileTransporterFactory.class);
        locator.addService(TransporterFactory.class, HttpTransporterFactory.class);
        return locator.getService(RepositorySystem.class);
    }

    private static RepositorySystemSession newSession(RepositorySystem system) {
        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();
        LocalRepository localRepo = new LocalRepository("target/local-repo");
        session.setLocalRepositoryManager(system.newLocalRepositoryManager(session, localRepo));
        return session;
    }
}
