package org.sonatype.tycho.equinox.launching.internal;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.jar.Manifest;

import org.codehaus.plexus.PlexusContainer;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.tycho.TychoConstants;
import org.eclipse.osgi.util.ManifestElement;
import org.osgi.framework.Constants;
import org.sonatype.tycho.ArtifactDescriptor;
import org.sonatype.tycho.ArtifactKey;
import org.sonatype.tycho.equinox.launching.EclipseInstallation;

public class DefaultEquinoxLauncher
    extends AbstractEquinoxLauncher
    implements EclipseInstallation
{
    private final List<File> frameworkExtensions = new ArrayList<File>();

    private final File location;

    private final Set<String> bundlesToExplode = new HashSet<String>();

    public DefaultEquinoxLauncher( PlexusContainer plexus, File location )
    {
        super( plexus );
        this.location = location;
    }

    public void createInstallation()
    {
        Map<ArtifactKey, File> effective = new LinkedHashMap<ArtifactKey, File>();

        for ( ArtifactDescriptor artifact : bundles.getArtifacts( org.sonatype.tycho.ArtifactKey.TYPE_ECLIPSE_PLUGIN ) )
        {
            ArtifactKey key = artifact.getKey();
            File file = artifact.getLocation();
            Manifest mf = manifestReader.loadManifest( file );

            boolean directoryShape = bundlesToExplode.contains( key.getId() ) || manifestReader.isDirectoryShape( mf );

            if ( !file.isDirectory() && directoryShape )
            {
                String filename = key.getId() + "_" + key.getVersion();
                File unpacked = new File( location, "plugins/" + filename );

                unpacked.mkdirs();

                unpack( file, unpacked );

                effective.put( key, unpacked );
            }
            else
            {
                effective.put( key, file );
            }
        }

        try
        {
            location.mkdirs();
            
            Properties p = new Properties();

            String newOsgiBundles;

            // if ( shouldUseP2() )
            // {
            // createBundlesInfoFile( location );
            // createPlatformXmlFile( location );
            // newOsgiBundles = "org.eclipse.equinox.simpleconfigurator@1:start";
            // }
            // else if ( shouldUseUpdateManager() )
            // {
            // createPlatformXmlFile( location );
            // newOsgiBundles =
            // "org.eclipse.equinox.common@2:start, org.eclipse.update.configurator@3:start, org.eclipse.core.runtime@start";
            // }
            // else
            /* use plain equinox */{
                newOsgiBundles = toOsgiBundles( effective );
            }

            p.setProperty( "osgi.bundles", newOsgiBundles );

            // see https://bugs.eclipse.org/bugs/show_bug.cgi?id=234069
            p.setProperty( "osgi.bundlefile.limit", "100" );

            // @see SimpleConfiguratorConstants#PROP_KEY_EXCLUSIVE_INSTALLATION
            // p.setProperty("org.eclipse.equinox.simpleconfigurator.exclusiveInstallation", "false");

            p.setProperty( "osgi.install.area", "file:" + location.getAbsolutePath().replace( '\\', '/' ) );
            p.setProperty( "osgi.configuration.cascaded", "false" );
            p.setProperty( "osgi.framework", "org.eclipse.osgi" );
            p.setProperty( "osgi.bundles.defaultStartLevel", "4" );

            // fix osgi.framework
            String url = p.getProperty( "osgi.framework" );
            if ( url != null )
            {
                File file;
                ArtifactDescriptor desc = getBundle( url, null );
                if ( desc != null )
                {
                    url = "file:" + desc.getLocation().getAbsolutePath().replace( '\\', '/' );
                }
                else if ( url.startsWith( "file:" ) )
                {
                    String path = url.substring( "file:".length() );
                    file = new File( path );
                    if ( !file.isAbsolute() )
                    {
                        file = new File( location, path );
                    }
                    url = "file:" + file.getAbsolutePath().replace( '\\', '/' );
                }
            }
            if ( url != null )
            {
                p.setProperty( "osgi.framework", url );
            }

            if ( !frameworkExtensions.isEmpty() )
            {
                Collection<String> bundleNames = unpackFrameworkExtensions( frameworkExtensions );
                p.setProperty( "osgi.framework", copySystemBundle() );
                p.setProperty( "osgi.framework.extensions", StringUtils.join( bundleNames.iterator(), "," ) );
            }

            new File( location, "configuration" ).mkdir();
            FileOutputStream fos = new FileOutputStream( new File( location, TychoConstants.CONFIG_INI_PATH ) );
            try
            {
                p.store( fos, null );
            }
            finally
            {
                fos.close();
            }
        }
        catch ( IOException e )
        {
            throw new RuntimeException( "Exception creating test eclipse runtime", e );
        }
    }

    public File getLocation()
    {
        return location;
    }

    public void addBundlesToExplode( List<String> bundlesToExplode )
    {
        this.bundlesToExplode.addAll(bundlesToExplode);
    }

    public void addFrameworkExtensions( List<File> frameworkExtensions )
    {
        this.frameworkExtensions.addAll( frameworkExtensions );
    }

    private List<String> unpackFrameworkExtensions( Collection<File> frameworkExtensions )
        throws IOException
    {
        List<String> bundleNames = new ArrayList<String>();

        for ( File bundleFile : frameworkExtensions )
        {
            Manifest mf = manifestReader.loadManifest( bundleFile );
            ManifestElement[] id = manifestReader.parseHeader( Constants.BUNDLE_SYMBOLICNAME, mf );
            ManifestElement[] version = manifestReader.parseHeader( Constants.BUNDLE_VERSION, mf );

            if ( id == null || version == null )
            {
                throw new IOException( "Invalid OSGi manifest in bundle " + bundleFile );
            }

            bundleNames.add( id[0].getValue() );

            File bundleDir = new File( location, "plugins/" + id[0].getValue() + "_" + version[0].getValue() );
            if ( bundleFile.isFile() )
            {
                unpack( bundleFile, bundleDir );
            }
            else
            {
                FileUtils.copyDirectoryStructure( bundleFile, bundleDir );
            }
        }

        return bundleNames;
    }

    private String copySystemBundle()
        throws IOException
    {
        ArtifactDescriptor bundle = getSystemBundle();
        File srcFile = bundle.getLocation();
        File dstFile = new File( location, "plugins/" + srcFile.getName() );
        FileUtils.copyFileIfModified( srcFile, dstFile );

        return "file:" + dstFile.getAbsolutePath().replace( '\\', '/' );
    }
}
