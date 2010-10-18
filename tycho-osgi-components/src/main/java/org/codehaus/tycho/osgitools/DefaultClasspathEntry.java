package org.codehaus.tycho.osgitools;

import java.io.File;
import java.util.List;

import org.codehaus.tycho.ClasspathEntry;
import org.sonatype.tycho.ArtifactKey;

public class DefaultClasspathEntry
    implements ClasspathEntry
{
    private final ArtifactKey key;

    private final List<File> locations;

    private final List<AccessRule> rules;

    public DefaultClasspathEntry( ArtifactKey key, List<File> locations, List<AccessRule> rules )
    {
        this.key = key;
        this.locations = locations;
        this.rules = rules;
    }

    public List<File> getLocations()
    {
        return locations;
    }

    public List<AccessRule> getAccessRules()
    {
        return rules;
    }

    public ArtifactKey getArtifactKey()
    {
        return key;
    }
}
