package org.sonatype.tycho.p2.facade.internal;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;
import org.sonatype.tycho.equinox.EquinoxRuntimeLocator;
import org.sonatype.tycho.equinox.launching.EclipseInstallation;
import org.sonatype.tycho.equinox.launching.EquinoxLauncherFactory;

/**
 * Convenience wrapper around {@link Commandline} to run Eclipse applications from tycho-p2-runtime
 * 
 * @author igor
 */
@Component( role = P2ApplicationLauncher.class, instantiationStrategy = "per-lookup" )
public class P2ApplicationLauncher
{
    @Requirement
    private Logger logger;

    @Requirement
    private EquinoxLauncherFactory launcherFactory;

    @Requirement
    private EquinoxRuntimeLocator runtimeLocator;

    private File workingDirectory;

    private String applicationName;

    private final List<String> vmargs = new ArrayList<String>();

    private final List<String> args = new ArrayList<String>();

    public void setWorkingDirectory( File workingDirectory )
    {
        this.workingDirectory = workingDirectory;
    }

    public void setApplicationName( String applicationName )
    {
        this.applicationName = applicationName;
    }

    public void addArguments( String... args )
    {
        for ( String arg : args )
        {
            this.args.add( arg );
        }
    }

    public void addVMArguments( String... vmargs )
    {
        for ( String vmarg : vmargs )
        {
            this.vmargs.add( vmarg );
        }
    }

    public int execute( int forkedProcessTimeoutInSeconds )
    {
        try
        {
            File installationFolder = newTemporaryFolder();

            try
            {
                EclipseInstallation installation = launcherFactory.createEclipseInstallation( installationFolder );

                List<File> locations = runtimeLocator.getRuntimeLocations();

                for ( File location : locations )
                {
                    if ( location.isDirectory() )
                    {
                        for ( File file : new File( location, "plugins" ).listFiles() )
                        {
                            installation.addBundle( file, false );
                        }
                    }
                    else
                    {
                        installation.addBundle( location, false );
                    }
                }

                installation.createInstallation();

                Commandline cli = new Commandline();

                cli.setWorkingDirectory( workingDirectory );

                String executable =
                    System.getProperty( "java.home" ) + File.separator + "bin" + File.separator + "java";
                if ( File.separatorChar == '\\' )
                {
                    executable = executable + ".exe";
                }
                cli.setExecutable( executable );

                cli.addArguments( new String[] { "-jar", installation.getLauncherJar().getCanonicalPath(), } );

                // logging

                if ( logger.isDebugEnabled() )
                {
                    cli.addArguments( new String[] { "-debug", "-consoleLog" } );
                }

                // application and application arguments

                cli.addArguments( new String[] { "-nosplash", "-application", applicationName } );

                cli.addArguments( args.toArray( new String[args.size()] ) );

                logger.info( "Command line:\n\t" + cli.toString() );

                StreamConsumer out = new StreamConsumer()
                {
                    public void consumeLine( String line )
                    {
                        System.out.println( line );
                    }
                };
                StreamConsumer err = new StreamConsumer()
                {
                    public void consumeLine( String line )
                    {
                        System.err.println( line );
                    }
                };

                return CommandLineUtils.executeCommandLine( cli, out, err, forkedProcessTimeoutInSeconds );
            }
            finally
            {
                FileUtils.deleteDirectory( installationFolder );
            }
        }
        catch ( Exception e )
        {
            // TODO better exception?
            throw new RuntimeException( e );
        }
    }

    private File newTemporaryFolder()
        throws IOException
    {
        File tmp = File.createTempFile( "tycho-p2-runtime", ".tmp" );
        tmp.delete();
        tmp.mkdir(); // anyone got any better idea?
        return tmp;
    }
}
