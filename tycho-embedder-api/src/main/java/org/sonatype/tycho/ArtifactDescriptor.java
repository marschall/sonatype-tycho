package org.sonatype.tycho;

import java.io.File;

import org.apache.maven.project.MavenProject;

public interface ArtifactDescriptor
{
    public ArtifactKey getKey();

    public File getLocation();

    public MavenProject getMavenProject();

}
