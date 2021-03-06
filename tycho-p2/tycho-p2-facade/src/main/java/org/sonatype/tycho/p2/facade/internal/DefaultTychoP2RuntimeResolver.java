package org.sonatype.tycho.p2.facade.internal;

import java.io.File;
import java.util.List;

import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.LegacySupport;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.sonatype.tycho.equinox.EquinoxRuntimeLocator;
import org.sonatype.tycho.p2runtime.TychoP2RuntimeResolver;

@Component( role = TychoP2RuntimeResolver.class )
public class DefaultTychoP2RuntimeResolver
    implements TychoP2RuntimeResolver
{
    @Requirement
    private EquinoxRuntimeLocator location;

    @Requirement
    private LegacySupport sessionContext;

    public List<File> getRuntimeLocations( MavenSession session )
        throws MavenExecutionException
    {
        MavenSession oldSession = sessionContext.getSession();

        sessionContext.setSession( session );
        try
        {
            return location.getRuntimeLocations();
        }
        catch ( MavenExecutionException e )
        {
            throw e;
        }
        catch ( Exception e )
        {
            throw new MavenExecutionException( "Could not resolve Tycho P2 runtime", e );
        }
        finally
        {
            sessionContext.setSession( oldSession );
        }
    }

}
