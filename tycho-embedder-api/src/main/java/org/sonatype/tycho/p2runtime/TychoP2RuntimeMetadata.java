package org.sonatype.tycho.p2runtime;

import java.io.File;
import java.util.List;

import org.apache.maven.execution.MavenSession;

/**
 * Tycho P2 runtime metadata.
 */
public interface TychoP2RuntimeMetadata
{
    /**
     * Bundle manifest attribute name, if set, bundle is not intended for use when Tycho is embedded in another
     * Equinox-based application. The only usecase currently is Equinox secure storage provder implementation used by
     * Tycho to suppress password requests for transient secure storage.
     */
    public static final String NOEMBED = "Tycho-NoEmbed";

    /**
     * Determines, resolves and if necessary unpacks artifacts of Tycho P2 runtime. Returns null if Tycho P2 runtime is
     * not available. If returned file is a directory, it is assumed to have plugins/ subdirectory containing OSGi
     * bundle jar files. First element of the returned collection will point at org.eclipse.osgi bundle either directly
     * or as a file under plugins/ subdirectory.
     */
    public List<File> resolve( MavenSession session );

    /**
     * List of facade packages imported by Tycho P2 runtime from the host application. Classes from these packages are
     * used to pass information to and from Tycho P2 runtime and thus must come from a "bridge" classloader visible by
     * both Tycho P2 runtime and the host application.
     */
    public List<String> getFacadePackages();
}
