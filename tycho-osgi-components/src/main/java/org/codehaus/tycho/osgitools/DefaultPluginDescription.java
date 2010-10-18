package org.codehaus.tycho.osgitools;

import java.io.File;

import org.apache.maven.project.MavenProject;
import org.codehaus.tycho.PluginDescription;
import org.codehaus.tycho.model.PluginRef;
import org.sonatype.tycho.ArtifactKey;

public class DefaultPluginDescription
    extends DefaultArtifactDescriptor
    implements PluginDescription
{

    private PluginRef pluginRef;

    public DefaultPluginDescription( ArtifactKey key, File location, MavenProject project, PluginRef pluginRef )
    {
        super( key, location, project );
        this.pluginRef = pluginRef;
    }

    public PluginRef getPluginRef()
    {
        return pluginRef;
    }

}
