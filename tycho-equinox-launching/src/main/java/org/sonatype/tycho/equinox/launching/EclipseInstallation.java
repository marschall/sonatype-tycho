package org.sonatype.tycho.equinox.launching;

import java.io.File;
import java.util.List;

public interface EclipseInstallation
    extends EquinoxLauncher
{
    void addFrameworkExtensions( List<File> frameworkExtensions );

    void addBundlesToExplode( List<String> bundlesToExplode );

    File getLocation();

    void createInstallation();
}
