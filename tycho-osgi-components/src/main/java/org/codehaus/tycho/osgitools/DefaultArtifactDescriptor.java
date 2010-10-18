package org.codehaus.tycho.osgitools;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;

public class DefaultArtifactDescriptor
    implements ArtifactDescriptor
{

    private final ArtifactKey key;

    private final File location;

    private final MavenProject project;

    public DefaultArtifactDescriptor( ArtifactKey key, File location, MavenProject project )
    {
        this.key = key;
        this.location = location;
        this.project = project;
    }

    public ArtifactKey getKey()
    {
        return key;
    }

    public File getLocation()
    {
        return location;
    }

    public MavenProject getMavenProject()
    {
        return project;
    }

    @Override
    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append( key.toString() ).append( ": " );
        if ( project != null )
        {
            sb.append( project.toString() );
        }
        else
        {
            sb.append( location );
        }
        return sb.toString();
    }
}
