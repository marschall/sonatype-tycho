package org.codehaus.tycho.osgitools.targetplatform;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.WeakHashMap;

import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.osgitools.DefaultArtifactDescriptor;
import org.codehaus.tycho.osgitools.DefaultArtifactKey;
import org.osgi.framework.Version;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;

public class DefaultTargetPlatform
    implements TargetPlatform
{
    private static final Version VERSION_0_0_0 = new Version( "0.0.0" );

    private static final WeakHashMap<ArtifactKey, ArtifactKey> KEY_CACHE = new WeakHashMap<ArtifactKey, ArtifactKey>();

    private static final WeakHashMap<ArtifactKey, ArtifactDescriptor> ARTIFACT_CACHE =
        new WeakHashMap<ArtifactKey, ArtifactDescriptor>();

    protected final Map<ArtifactKey, ArtifactDescriptor> artifacts =
        new LinkedHashMap<ArtifactKey, ArtifactDescriptor>();

    protected final Map<File, ArtifactDescriptor> locations = new LinkedHashMap<File, ArtifactDescriptor>();

    protected final Set<Object/* IInstallableUnit */> installableUnits = new LinkedHashSet<Object>();

    public List<ArtifactDescriptor> getArtifacts( String type )
    {
        ArrayList<ArtifactDescriptor> result = new ArrayList<ArtifactDescriptor>();
        for ( Map.Entry<ArtifactKey, ArtifactDescriptor> entry : artifacts.entrySet() )
        {
            if ( type.equals( entry.getKey().getType() ) )
            {
                result.add( entry.getValue() );
            }
        }

        return result;
    }

    public List<ArtifactDescriptor> getArtifacts()
    {
        return new ArrayList<ArtifactDescriptor>( artifacts.values() );
    }

    public void addArtifactFile( ArtifactKey key, File location, Set<Object> installableUnits )
    {
        addArtifact( new DefaultArtifactDescriptor( key, location, null, installableUnits ) );
    }

    public void addArtifact( ArtifactDescriptor artifact )
    {
        ArtifactKey key = normalizeKey( artifact.getKey() );

        ArtifactKey cachedKey = KEY_CACHE.get( key );
        if ( cachedKey != null )
        {
            key = cachedKey;
        }
        else
        {
            KEY_CACHE.put( key, key );
        }

        artifact = normalizeArtifact( artifact );

        ArtifactDescriptor cachedArtifact = ARTIFACT_CACHE.get( key );
        if ( cachedArtifact != null && eq( cachedArtifact.getLocation(), artifact.getLocation() )
            && eq( cachedArtifact.getMavenProject(), artifact.getMavenProject() ) )
        {
            artifact = cachedArtifact;
        }
        else
        {
            ARTIFACT_CACHE.put( key, artifact );
        }

        artifacts.put( key, artifact );
        locations.put( artifact.getLocation(), artifact );
    }

    private ArtifactDescriptor normalizeArtifact( ArtifactDescriptor artifact )
    {
        try
        {
            File location = artifact.getLocation().getCanonicalFile();
            if ( !location.equals( artifact.getLocation() ) )
            {
                return new DefaultArtifactDescriptor( artifact.getKey(), location, artifact.getMavenProject(),
                                                      artifact.getInstallableUnits() );
            }
            return artifact;
        }
        catch ( IOException e )
        {
            // not sure what good this will do to the caller
            return artifact;
        }
    }

    protected ArtifactKey normalizeKey( ArtifactKey key )
    {
        if ( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_TEST_PLUGIN.equals( key.getType() ) )
        {
            // normalize eclipse-test-plugin... after all, a bundle is a bundle.
            key =
                new DefaultArtifactKey( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN, key.getId(),
                                        key.getVersion() );
        }
        return key;
    }

    private static <T> boolean eq( T a, T b )
    {
        return a != null ? a.equals( b ) : b == null;
    }

    /**
     * @deprecated get rid of me, I am not used for anything
     */
    public void addSite( File location )
    {
    }

    public void dump()
    {
        for ( Map.Entry<ArtifactKey, ArtifactDescriptor> entry : artifacts.entrySet() )
        {
            System.out.println( entry.getKey() + "\t" + entry.getValue() );
        }
    }

    public boolean isEmpty()
    {
        return artifacts.isEmpty();
    }

    public ArtifactDescriptor getArtifact( String type, String id, String version )
    {
        if ( type == null || id == null )
        {
            // TODO should we throw something instead?
            return null;
        }

        // features with matching id, sorted by version, highest version first
        SortedMap<Version, ArtifactDescriptor> relevantArtifacts =
            new TreeMap<Version, ArtifactDescriptor>( new Comparator<Version>()
            {
                public int compare( Version o1, Version o2 )
                {
                    return -o1.compareTo( o2 );
                };
            } );

        for ( Map.Entry<ArtifactKey, ArtifactDescriptor> entry : this.artifacts.entrySet() )
        {
            ArtifactKey key = entry.getKey();
            if ( type.equals( key.getType() ) && id.equals( key.getId() ) )
            {
                relevantArtifacts.put( Version.parseVersion( key.getVersion() ), entry.getValue() );
            }
        }

        if ( relevantArtifacts.isEmpty() )
        {
            return null;
        }

        if ( version == null )
        {
            return relevantArtifacts.get( relevantArtifacts.firstKey() ); // latest version
        }

        Version parsedVersion = new Version( version );
        if ( VERSION_0_0_0.equals( parsedVersion ) )
        {
            return relevantArtifacts.get( relevantArtifacts.firstKey() ); // latest version
        }

        String qualifier = parsedVersion.getQualifier();

        if ( qualifier == null || "".equals( qualifier ) || ANY_QUALIFIER.equals( qualifier ) )
        {
            // latest qualifier
            for ( Map.Entry<Version, ArtifactDescriptor> entry : relevantArtifacts.entrySet() )
            {
                if ( baseVersionEquals( parsedVersion, entry.getKey() ) )
                {
                    return entry.getValue();
                }
            }
        }

        // perfect match or nothing
        return relevantArtifacts.get( parsedVersion );
    }

    private static boolean baseVersionEquals( Version v1, Version v2 )
    {
        return v1.getMajor() == v2.getMajor() && v1.getMinor() == v2.getMinor() && v1.getMicro() == v2.getMicro();
    }

    public void addMavenProject( ArtifactKey key, MavenProject project, Set<Object> installableUnits )
    {
        DefaultArtifactDescriptor artifact = new DefaultArtifactDescriptor( key, project.getBasedir(), project, installableUnits );
        addArtifact( artifact );
    }

    public MavenProject getMavenProject( File location )
    {
        ArtifactDescriptor artifact = getArtifact( location );
        return artifact != null ? artifact.getMavenProject() : null;
    }

    public ArtifactDescriptor getArtifact( File location )
    {
        try
        {
            location = location.getCanonicalFile();
            return locations.get( location );
        }
        catch ( IOException e )
        {
            return null;
        }
    }

    public ArtifactDescriptor getArtifact( ArtifactKey key )
    {
        return artifacts.get( normalizeKey( key ) );
    }

    public void removeAll( String type, String id )
    {
        Iterator<Entry<ArtifactKey, ArtifactDescriptor>> iter = artifacts.entrySet().iterator();
        while ( iter.hasNext() )
        {
            ArtifactKey key = iter.next().getKey();
            if ( key.getType().equals( type ) && key.getId().equals( id ) )
            {
                iter.remove();
            }
        }
    }

    public Set<Object> getInstallableUnits()
    {
        return installableUnits;
    }

    public void addInstallableUnits( Set<Object/* IInstallableUnit */> installableUnits )
    {
        this.installableUnits.addAll( installableUnits );
    }

    public void toDebugString( StringBuilder sb, String linePrefix )
    {
        for ( ArtifactDescriptor artifact : artifacts.values() )
        {
            sb.append( linePrefix );
            sb.append( artifact.getKey().toString() );
            sb.append( ": " );
            MavenProject project = artifact.getMavenProject();
            if ( project != null )
            {
                sb.append( project.toString() );
            }
            else
            {
                sb.append( artifact.getLocation() );
            }
            sb.append( "\n" );
        }
    }
}
