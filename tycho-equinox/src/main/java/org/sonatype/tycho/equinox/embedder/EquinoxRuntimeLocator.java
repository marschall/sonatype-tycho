package org.sonatype.tycho.equinox.embedder;

import java.io.File;
import java.util.List;

public interface EquinoxRuntimeLocator
{
    public List<File> getRuntimeLocations()
        throws Exception;
}
