package org.sonatype.tycho.equinox.launching;

import java.io.File;

public interface EquinoxLauncherFactory
{
    public EclipseInstallation createEclipseInstallation( File installationLocation );
}
