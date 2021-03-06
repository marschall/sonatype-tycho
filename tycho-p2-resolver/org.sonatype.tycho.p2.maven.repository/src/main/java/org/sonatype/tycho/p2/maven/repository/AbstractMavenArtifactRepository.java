package org.sonatype.tycho.p2.maven.repository;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.equinox.p2.metadata.IArtifactKey;
import org.eclipse.equinox.p2.query.IQuery;
import org.eclipse.equinox.p2.query.IQueryResult;
import org.eclipse.equinox.p2.query.IQueryable;
import org.eclipse.equinox.p2.repository.artifact.IArtifactDescriptor;
import org.eclipse.equinox.p2.repository.artifact.spi.AbstractArtifactRepository;
import org.eclipse.equinox.p2.repository.artifact.spi.ArtifactDescriptor;
import org.sonatype.tycho.p2.maven.repository.xmlio.ArtifactsIO;
import org.sonatype.tycho.p2.repository.GAV;
import org.sonatype.tycho.p2.repository.RepositoryLayoutHelper;
import org.sonatype.tycho.p2.repository.RepositoryReader;
import org.sonatype.tycho.p2.repository.TychoRepositoryIndex;

public abstract class AbstractMavenArtifactRepository
    extends AbstractArtifactRepository
{
    public static final String VERSION = "1.0.0";

    private static final IArtifactDescriptor[] ARTIFACT_DESCRIPTOR_ARRAY = new IArtifactDescriptor[0];

    protected Map<IArtifactKey, Set<IArtifactDescriptor>> descriptorsMap = new HashMap<IArtifactKey, Set<IArtifactDescriptor>>();

    protected Set<IArtifactDescriptor> descriptors = new HashSet<IArtifactDescriptor>();

    private final RepositoryReader contentLocator;

    private final TychoRepositoryIndex projectIndex;

    protected AbstractMavenArtifactRepository( URI uri, TychoRepositoryIndex projectIndex,
        RepositoryReader contentLocator )
    {
        super( Activator.getProvisioningAgent(), "Maven Local Repository", AbstractMavenArtifactRepository.class.getName(), VERSION, uri, null, null, null );
        this.projectIndex = projectIndex;
        this.contentLocator = contentLocator;

        loadMaven();
    }

    protected void loadMaven()
    {
        ArtifactsIO io = new ArtifactsIO();

        for ( GAV gav : projectIndex.getProjectGAVs() )
        {
            try
            {
                InputStream is = contentLocator.getContents(
                    gav,
                    RepositoryLayoutHelper.CLASSIFIER_P2_ARTIFACTS,
                    RepositoryLayoutHelper.EXTENSION_P2_ARTIFACTS );
                try
                {
                    Set<IArtifactDescriptor> gavDescriptors = (Set<IArtifactDescriptor>) io.readXML( is );

                    if ( !gavDescriptors.isEmpty() )
                    {
                        IArtifactKey key = gavDescriptors.iterator().next().getArtifactKey();
                        descriptorsMap.put( key, gavDescriptors );
                        descriptors.addAll( gavDescriptors );
                    }
                }
                finally
                {
                    is.close();
                }
            }
            catch ( IOException e )
            {
                // too bad
            }
        }
    }

    @Override
    public boolean contains( IArtifactDescriptor descriptor )
    {
        return descriptor != null && descriptors.contains( descriptor );
    }

    @Override
    public boolean contains( IArtifactKey key )
    {
        return key != null && descriptorsMap.containsKey( key );
    }

    @Override
    public IArtifactDescriptor[] getArtifactDescriptors( IArtifactKey key )
    {
        Set<IArtifactDescriptor> descriptors = descriptorsMap.get( key );
        if ( descriptors == null )
        {
            return ARTIFACT_DESCRIPTOR_ARRAY;
        }
        return descriptors.toArray( ARTIFACT_DESCRIPTOR_ARRAY );
    }

    protected GAV getP2GAV( IArtifactDescriptor descriptor )
    {
        IArtifactKey key = descriptor.getArtifactKey();
        StringBuffer version = new StringBuffer();
        key.getVersion().toString( version );
        return RepositoryLayoutHelper.getP2Gav( key.getClassifier(), key.getId(), version.toString() );
    }

    protected RepositoryReader getContentLocator()
    {
        return contentLocator;
    }

    public abstract IStatus resolve( IArtifactDescriptor descriptor );

    @Override
    public void addDescriptor( IArtifactDescriptor descriptor )
    {
        super.addDescriptor( descriptor );

        descriptors.add( descriptor );

        IArtifactKey key = descriptor.getArtifactKey();

        Set<IArtifactDescriptor> keyDescriptors = descriptorsMap.get( key );

        if ( keyDescriptors == null )
        {
            keyDescriptors = new HashSet<IArtifactDescriptor>();
            descriptorsMap.put( key, keyDescriptors );
        }

        keyDescriptors.add( descriptor );
    }

    public GAV getGAV( IArtifactDescriptor descriptor )
    {
        GAV gav = RepositoryLayoutHelper.getGAV( ( (ArtifactDescriptor) descriptor ).getProperties() );

        if ( gav == null )
        {
            gav = getP2GAV( descriptor );
        }

        return gav;
    }

    public IQueryResult<IArtifactKey> query( IQuery<IArtifactKey> query, IProgressMonitor monitor )
    {
        return query.perform( descriptorsMap.keySet().iterator() );
    }

    public IQueryable<IArtifactDescriptor> descriptorQueryable()
    {
        return new IQueryable<IArtifactDescriptor>()
        {
            public IQueryResult<IArtifactDescriptor> query( IQuery<IArtifactDescriptor> query, IProgressMonitor monitor )
            {
                return query.perform( descriptors.iterator() );
            }
        };
    }
}
