package org.sonatype.tycho.equinox.launching.internal;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.jar.Manifest;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.UnArchiver;
import org.codehaus.plexus.component.repository.exception.ComponentLookupException;
import org.codehaus.tycho.osgitools.BundleReader;
import org.codehaus.tycho.osgitools.DefaultArtifactKey;
import org.codehaus.tycho.osgitools.targetplatform.DefaultTargetPlatform;
import org.eclipse.osgi.framework.adaptor.FrameworkAdaptor;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Constants;
import org.osgi.framework.Version;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;
import org.sonatype.tycho.equinox.launching.BundleStartLevel;
import org.sonatype.tycho.equinox.launching.EquinoxLauncher;

public abstract class AbstractEquinoxLauncher
    implements EquinoxLauncher
{
    private static final Map<String, BundleStartLevel> DEFAULT_START_LEVEL = new HashMap<String, BundleStartLevel>();

    static
    {
        setDefaultStartLevel( "org.eclipse.equinox.common", 2 );
        setDefaultStartLevel( "org.eclipse.core.runtime", 4 );
        setDefaultStartLevel( "org.eclipse.equinox.simpleconfigurator", 1 );
        setDefaultStartLevel( "org.eclipse.update.configurator", 3 );
        setDefaultStartLevel( "org.eclipse.osgi", -1 );
        setDefaultStartLevel( "org.eclipse.equinox.ds", 1 );
    }

    private static void setDefaultStartLevel( String id, int level )
    {
        DEFAULT_START_LEVEL.put( id, new BundleStartLevel( id, level, true ) );
    }

    protected final PlexusContainer plexus;

    protected final BundleReader manifestReader;

    protected DefaultTargetPlatform bundles = new DefaultTargetPlatform();

    private Map<String, BundleStartLevel> startLevel = new HashMap<String, BundleStartLevel>( DEFAULT_START_LEVEL );

    protected AbstractEquinoxLauncher( PlexusContainer plexus )
    {
        this.plexus = plexus;
        try
        {
            this.manifestReader = plexus.lookup( BundleReader.class );
        }
        catch ( ComponentLookupException e )
        {
            throw new IllegalStateException( "Could not lookup required component", e );
        }
    }

    protected String toOsgiBundles( Map<ArtifactKey, File> bundles )
        throws IOException
    {
        StringBuilder result = new StringBuilder();
        for ( Map.Entry<ArtifactKey, File> entry : bundles.entrySet() )
        {
            BundleStartLevel level = startLevel.get( entry.getKey().getId() );
            if ( level != null && level.getLevel() == -1 )
            {
                continue; // system bundle
            }
            if ( result.length() > 0 )
            {
                result.append( "," );
            }
            result.append( appendAbsolutePath( entry.getValue() ) );
            if ( level != null )
            {
                result.append( '@' ).append( level.getLevel() );
                if ( level.isAutoStart() )
                {
                    result.append( ":start" );
                }
            }
        }
        return result.toString();
    }

    private String appendAbsolutePath( File file )
        throws IOException
    {
        String url = file.getAbsolutePath().replace( '\\', '/' );
        return "reference:file:" + url;
    }

    public void addBundleStartLevel( BundleStartLevel level )
    {
        startLevel.put( level.getId(), level );
    }

    public ArtifactDescriptor getBundle( String symbolicName, String highestVersion )
    {
        return bundles.getArtifact( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN, symbolicName, highestVersion );
    }

    public ArtifactDescriptor getSystemBundle()
    {
        return bundles.getArtifact( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN,
                                    FrameworkAdaptor.FRAMEWORK_SYMBOLICNAME, null );
    }

    public void addBundle( File file, boolean override )
    {
        Manifest mf = manifestReader.loadManifest( file );

        ManifestElement[] id = manifestReader.parseHeader( Constants.BUNDLE_SYMBOLICNAME, mf );
        ManifestElement[] version = manifestReader.parseHeader( Constants.BUNDLE_VERSION, mf );

        if ( id == null || version == null )
        {
            throw new IllegalArgumentException( "Not a bundle " + file.getAbsolutePath() );
        }

        if ( override )
        {
            bundles.removeAll( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN, id[0].getValue() );
        }

        bundles.addArtifactFile( new DefaultArtifactKey( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN,
                                                         id[0].getValue(), version[0].getValue() ), file, null );
    }

    public void addBundle( ArtifactDescriptor artifact )
    {
        bundles.addArtifact( artifact );
    }

    public void addBundle( ArtifactKey key, File file )
    {
        bundles.addArtifactFile( key, file, null );
    }

    protected void unpack( File source, File destination )
    {
        UnArchiver unzip;
        try
        {
            unzip = plexus.lookup( UnArchiver.class, "zip" );
        }
        catch ( ComponentLookupException e )
        {
            throw new RuntimeException( "Could not lookup required component", e );
        }
        destination.mkdirs();
        unzip.setSourceFile( source );
        unzip.setDestDirectory( destination );
        try
        {
            unzip.extract();
        }
        catch ( ArchiverException e )
        {
            throw new RuntimeException( "Unable to unpack jar " + source, e );
        }
    }

    public File getLauncherJar()
    {
        ArtifactDescriptor systemBundle = getSystemBundle();
        Version osgiVersion = Version.parseVersion( systemBundle.getKey().getVersion() );
        if ( osgiVersion.compareTo( EQUINOX_VERSION_3_3_0 ) < 0 )
        {
            throw new IllegalArgumentException( "Eclipse 3.2 and earlier are not supported." );
            // return new File(state.getTargetPlaform(), "startup.jar").getCanonicalFile();
        }
        else
        {
            // assume eclipse 3.3 or 3.4
            ArtifactDescriptor launcher = getBundle( EQUINOX_LAUNCHER, null );
            if ( launcher == null )
            {
                throw new IllegalArgumentException( "Could not find " + EQUINOX_LAUNCHER
                    + " bundle in the test runtime." );
            }
            try
            {
                return launcher.getLocation().getCanonicalFile();
            }
            catch ( IOException e )
            {
                return launcher.getLocation().getAbsoluteFile();
            }
        }
    }

}
