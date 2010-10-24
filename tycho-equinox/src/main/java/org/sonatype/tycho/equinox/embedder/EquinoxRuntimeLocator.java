package org.sonatype.tycho.equinox.embedder;

import java.io.File;
import java.util.List;

public interface EquinoxRuntimeLocator
{
    // TODO do we need more specific exception type here?
    public List<File> getRuntimeLocations()
        throws Exception;
}
