package org.sonatype.tycho.equinox.launching.internal;

import java.io.File;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.tycho.equinox.launching.EclipseInstallation;
import org.sonatype.tycho.equinox.launching.EquinoxLauncherFactory;

@Component( role = EquinoxLauncherFactory.class )
public class DefaultEquinoxLauncherFactory
    implements EquinoxLauncherFactory
{
    @Requirement
    private PlexusContainer plexus;

    public EclipseInstallation createEclipseInstallation( File installationLocation )
    {
        return new DefaultEquinoxLauncher( plexus, installationLocation );
    }
}
