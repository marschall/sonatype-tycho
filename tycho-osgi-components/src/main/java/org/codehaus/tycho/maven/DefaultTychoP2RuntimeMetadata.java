package org.codehaus.tycho.maven;

import java.util.ArrayList;
import java.util.List;

import org.apache.maven.model.Dependency;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.tycho.utils.TychoVersion;
import org.sonatype.tycho.p2runtime.TychoP2RuntimeMetadata;

@Component( role = TychoP2RuntimeMetadata.class, hint = TychoP2RuntimeMetadata.HINT_FRAMEWORK )
public class DefaultTychoP2RuntimeMetadata
    implements TychoP2RuntimeMetadata
{
    private static final List<Dependency> ARTIFACTS;

    static
    {
        ARTIFACTS = new ArrayList<Dependency>();

        String p2Version = TychoVersion.getTychoVersion();

        ARTIFACTS.add( newDependency( "org.sonatype.tycho", "tycho-p2-runtime", p2Version, "zip" ) );
    }

    public List<Dependency> getRuntimeArtifacts()
    {
        return ARTIFACTS;
    }

    private static Dependency newDependency( String groupId, String artifactId, String version, String type )
    {
        Dependency d = new Dependency();
        d.setGroupId( groupId );
        d.setArtifactId( artifactId );
        d.setVersion( version );
        d.setType( type );
        return d;
    }

}
