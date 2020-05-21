package com.ledig;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.MultiFields;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.util.Bits;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.index.ArtifactInfo;
import org.apache.maven.index.ArtifactInfoFilter;
import org.apache.maven.index.ArtifactInfoGroup;
import org.apache.maven.index.Field;
import org.apache.maven.index.FlatSearchRequest;
import org.apache.maven.index.FlatSearchResponse;
import org.apache.maven.index.GroupedSearchRequest;
import org.apache.maven.index.GroupedSearchResponse;
import org.apache.maven.index.Grouping;
import org.apache.maven.index.Indexer;
import org.apache.maven.index.IteratorSearchRequest;
import org.apache.maven.index.IteratorSearchResponse;
import org.apache.maven.index.MAVEN;
import org.apache.maven.index.context.IndexCreator;
import org.apache.maven.index.context.IndexUtils;
import org.apache.maven.index.context.IndexingContext;
import org.apache.maven.index.expr.SourcedSearchExpression;
import org.apache.maven.index.expr.UserInputSearchExpression;
import org.apache.maven.index.search.grouping.GAGrouping;
import org.apache.maven.index.updater.IndexUpdateRequest;
import org.apache.maven.index.updater.IndexUpdateResult;
import org.apache.maven.index.updater.IndexUpdater;
import org.apache.maven.index.updater.ResourceFetcher;
import org.apache.maven.index.updater.WagonHelper;
import org.apache.maven.project.*;
import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.apache.maven.wagon.Wagon;
import org.apache.maven.wagon.events.TransferEvent;
import org.apache.maven.wagon.events.TransferListener;
import org.apache.maven.wagon.observers.AbstractTransferListener;
import org.codehaus.plexus.DefaultContainerConfiguration;
import org.codehaus.plexus.DefaultPlexusContainer;
import org.codehaus.plexus.PlexusConstants;
import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.PlexusContainerException;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.plexus.util.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;




import java.io.File;
import java.io.IOException;
import java.util.*;


public class App
{

    public static void main( String[] args ) throws IOException, PlexusContainerException, ComponentLookupException, ProjectBuildingException, DependencyResolutionException {
        final DefaultContainerConfiguration config = new DefaultContainerConfiguration();
        config.setClassPathScanning( PlexusConstants.SCANNING_INDEX );
        PlexusContainer plexusContainer = new DefaultPlexusContainer( config );

        Indexer ind = plexusContainer.lookup( Indexer.class );

        IndexUpdater indexUpdater = plexusContainer.lookup( IndexUpdater.class );

        Wagon httpWagon = plexusContainer.lookup( Wagon.class, "http" );

        // Cache locations
        File centralLocalCache = new File( "target/central-cache" );
        File centralIndexDir = new File( "target/central-index" );

        // Set up index creators
        List<IndexCreator> indexers = new ArrayList<>();
        indexers.add(plexusContainer.lookup( IndexCreator.class, "min" ) );
        indexers.add(plexusContainer.lookup( IndexCreator.class, "jarContent" ));
        indexers.add(plexusContainer.lookup( IndexCreator.class, "maven-plugin" ));

        IndexingContext centralContext = ind.createIndexingContext("central-context",
                "central", centralLocalCache, centralIndexDir,
                "https://repo1.maven.org/maven2", null, true, true, indexers);


        // Change to true to re-update index
        if ( false )
        {
            System.out.println( "Updating Index..." );
            System.out.println( "This might take a while on first run, so please be patient!" );
            // Create ResourceFetcher implementation to be used with IndexUpdateRequest
            // Here, we use Wagon based one as shorthand, but all we need is a ResourceFetcher implementation
            TransferListener listener = new AbstractTransferListener()
            {
                public void transferStarted( TransferEvent transferEvent )
                {
                    System.out.print( "  Downloading " + transferEvent.getResource().getName() );
                }

                public void transferProgress( TransferEvent transferEvent, byte[] buffer, int length )
                {
                }

                public void transferCompleted( TransferEvent transferEvent )
                {
                    System.out.println( " - Done" );
                }
            };
            ResourceFetcher resourceFetcher = new WagonHelper.WagonFetcher( httpWagon, listener, null, null );

            Date centralContextCurrentTimestamp = centralContext.getTimestamp();
            IndexUpdateRequest updateRequest = new IndexUpdateRequest( centralContext, resourceFetcher );
            IndexUpdateResult updateResult = indexUpdater.fetchAndUpdateIndex( updateRequest );
            if ( updateResult.isFullUpdate() )
            {
                System.out.println( "Full update happened!" );
            }
            else if ( updateResult.getTimestamp().equals( centralContextCurrentTimestamp ) )
            {
                System.out.println( "No update needed, index is up to date!" );
            }
            else
            {
                System.out.println(
                        "Incremental update happened, change covered " + centralContextCurrentTimestamp + " - "
                                + updateResult.getTimestamp() + " period." );
            }

            System.out.println();
        }

        System.out.println();
        System.out.println( "Using index" );
        System.out.println( "===========" );


        // Perform a query

        Query q =  ind.constructQuery( MAVEN.GROUP_ID, new SourcedSearchExpression( "org.apache.maven.indexer" ) );

        BooleanQuery boolQ = new BooleanQuery.Builder()
                .add( q, Occur.MUST )
                .build();

        FlatSearchResponse response = ind.searchFlat( new FlatSearchRequest(boolQ, centralContext) );
        Set<ArtifactInfo> resList = response.getResults();
        System.out.println("Received " + resList.size() + " results:\n");

        for (ArtifactInfo info : resList) {
            System.out.println("Name: " + info.getName() + " v" + info.getVersion());
            System.out.println("--------------------------");

            DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();
            RepositorySystem system = newRepositorySystem(locator);
            RepositorySystemSession session = newSession(system);
            org.eclipse.aether.artifact.Artifact artifact = infoToAetherArt(info);
            RemoteRepository central = new RemoteRepository.Builder("central", "default", "https://repo1.maven.org/maven2/").build();

            CollectRequest collectRequest = new CollectRequest(new Dependency(artifact, JavaScopes.COMPILE), Arrays.asList(central));
            DependencyFilter filter = DependencyFilterUtils.classpathFilter(JavaScopes.COMPILE);
            DependencyRequest request = new DependencyRequest(collectRequest, filter);
            DependencyResult result = system.resolveDependencies(session, request);

            for (ArtifactResult artifactResult : result.getArtifactResults()) {
                org.eclipse.aether.artifact.Artifact curArt = artifactResult.getArtifact();
                System.out.println(curArt.getGroupId() + "." + curArt.getArtifactId() + "." + curArt.getVersion());
            }

            System.out.println("--------------------------");
            System.out.println();
        }
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

//    private static Artifact infoToArt(ArtifactInfo info) {
//        return new DefaultArtifact(info.getGroupId(), info.getArtifactId(), info.getVersion(),
//                "compile",
//                info.getPackaging() != null ? info.getPackaging() : "jar",
//                info.getClassifier(), new DefaultArtifactHandler());
//    }

    private static org.eclipse.aether.artifact.Artifact infoToAetherArt(ArtifactInfo info) {
        return new org.eclipse.aether.artifact.DefaultArtifact(info.getGroupId(), info.getArtifactId(),
                info.getClassifier(),
                info.getFileExtension(),
                info.getVersion());
    }

//    private static MavenProject getProject(Artifact art) throws ProjectBuildingException {
//        ProjectBuilder builder = new DefaultProjectBuilder();
//        ProjectBuildingRequest req = new DefaultProjectBuildingRequest();
//        req.setProject(null);
//        //req.setRemoteRepositories();
//        ProjectBuildingResult res = builder.build(art, req);
//        return res.getProject();
//    }
}
