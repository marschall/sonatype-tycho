package org.sonatype.tycho.equinox.launching;

import java.io.File;

import org.osgi.framework.Version;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;

public interface EquinoxLauncher
{
    public static final Version EQUINOX_VERSION_3_3_0 = Version.parseVersion( "3.3.0" );

    public static final String EQUINOX_LAUNCHER = "org.eclipse.equinox.launcher";

    // configuration

    void addBundleStartLevel( BundleStartLevel level );

    void addBundle( ArtifactKey key, File basedir );

    void addBundle( ArtifactDescriptor artifact );

    void addBundle( File file, boolean override );

    // introspection

    ArtifactDescriptor getSystemBundle();

    ArtifactDescriptor getBundle( String symbolicName, String highestVersion );

    // launching

    File getLauncherJar();
}
