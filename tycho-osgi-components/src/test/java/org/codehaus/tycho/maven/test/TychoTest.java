package org.codehaus.tycho.maven.test;

import java.io.File;
import java.io.IOException;
import java.util.List;

import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.logging.Logger;
import org.codehaus.tycho.ClasspathEntry;
import org.codehaus.tycho.TargetPlatform;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.osgitools.DefaultBundleReader;
import org.codehaus.tycho.osgitools.OsgiBundleProject;
import org.codehaus.tycho.testing.AbstractTychoMojoTestCase;
import org.codehaus.tycho.testing.CompoundRuntimeException;
import org.sonatype.tycho.ArtifactKey;

public class TychoTest extends AbstractTychoMojoTestCase {

	protected Logger logger;

    @Override
	protected void setUp() throws Exception {
		super.setUp();
		logger = new SilentLog();
	}

	@Override
	protected void tearDown() throws Exception {
	    logger = null;
	    super.tearDown();
	}

	public void testModuleOrder() throws Exception {
		File pom = new File(getBasedir("projects/moduleorder"), "pom.xml");

		List<MavenProject> projects = getSortedProjects(pom);
		assertEquals(5, projects.size());

		MavenProject p002 = (MavenProject) projects.get(1);
		MavenProject p001 = (MavenProject) projects.get(2);
		MavenProject p004 = (MavenProject) projects.get(3); // feature
		MavenProject p003 = (MavenProject) projects.get(4); // site

		assertEquals("moduleorder.p001", p001.getArtifactId());
		assertEquals("moduleorder.p002", p002.getArtifactId());
		assertEquals("moduleorder.p003", p003.getArtifactId());
		assertEquals("moduleorder.p004", p004.getArtifactId());
	}

	protected List<MavenProject> getSortedProjects( File pom ) throws Exception
	{
        MavenExecutionRequest request = newMavenExecutionRequest(pom);
        return getSortedProjects( request );
	}

    private List<MavenProject> getSortedProjects( MavenExecutionRequest request )
    {
        request.getProjectBuildingRequest().setProcessPlugins( false ); // really dirty hack
	    MavenExecutionResult result = maven.execute( request );
	    if ( result.hasExceptions() )
	    {
	        throw new CompoundRuntimeException( result.getExceptions() );
	    }
        return result.getTopologicallySortedProjects();
    }

	public void testResolutionError() throws Exception {
		File pom = new File(getBasedir("projects/resolutionerror/p001"), "pom.xml");

		try 
		{
		    getSortedProjects(pom);
		    fail();
		} 
		catch ( Exception e )
		{
//	        List<Exception> exceptions = result.getExceptions();
//	        assertEquals(1, exceptions.size());
	        assertTrue(e.getMessage().contains("Missing Constraint: Import-Package: moduleorder.p002"));
		}
	}

	public void testFeatureMissingFeature() throws Exception {
        File pom = new File(getBasedir("projects/resolutionerror/feature_missing_feature"), "pom.xml");
        try 
        {
            getSortedProjects(pom);
            fail();
        } 
        catch ( Exception e )
        {
            assertTrue(e.getMessage().contains("Could not resolve feature feature.not.found_0.0.0"));
        }
	}

    public void testFeatureMissingPlugin() throws Exception {
        File pom = new File(getBasedir("projects/resolutionerror/feature_missing_plugin"), "pom.xml");
        try 
        {
            getSortedProjects(pom);
            fail();
        } 
        catch ( Exception e )
        {
            assertTrue(e.getMessage().contains("Could not resolve plugin plugin.not.found_0.0.0"));
        }
    }
	
	public void testProjectPriority() throws Exception {
		File platform = new File(getBasedir(), "src/test/resources/projects/projectpriority/platform");
		File pom = new File(getBasedir("projects/projectpriority"), "pom.xml");

		MavenExecutionRequest request = newMavenExecutionRequest(pom);
		request.getUserProperties().put("tycho.targetPlatform", platform.getCanonicalPath());

		List<MavenProject> projects = getSortedProjects( request );

		MavenProject p002 = (MavenProject) projects.get(2);

		List<Dependency> dependencies = p002.getModel().getDependencies();
		Dependency dependency = dependencies.get(0);
		assertEquals("0.0.1", dependency.getVersion());
	}

	public void testFragment() throws Exception {
		File pom = new File(getBasedir("projects/fragment"), "pom.xml");

		List<MavenProject> projects = getSortedProjects(pom);

		MavenProject host = projects.get(1);
		MavenProject dep = projects.get(2);
		MavenProject fragment = projects.get(3);
		MavenProject fragment2 = projects.get(4);
		MavenProject client = projects.get(5);

		assertEquals("host", host.getArtifactId());
		// host does not know anything about fragments
		List<Dependency> hostDependencies = host.getModel().getDependencies();
		assertEquals(0, hostDependencies.size());

		assertEquals("fragment", fragment.getArtifactId());
		List<Dependency> fragmentDependencies = fragment.getModel().getDependencies();
		// host first, then fragment dependency
		assertEquals(2, fragmentDependencies.size());
		assertEquals("host", fragmentDependencies.get(0).getArtifactId()); 
		assertEquals("dep", fragmentDependencies.get(1).getArtifactId());

		assertEquals("fragment2", fragment2.getArtifactId());
		// host only
		List<Dependency> fragment2Dependencies = fragment2.getModel().getDependencies();
		assertEquals(1, fragment2Dependencies.size());
		assertEquals("host", fragment2Dependencies.get(0).getArtifactId());

		assertEquals("client", client.getArtifactId());
		// depends on host and because host has ExtensibleAPI also depends fragment and fragent2
		List<Dependency> clientDependencies = client.getModel().getDependencies();
		assertEquals(4, clientDependencies.size());
		assertEquals("host", clientDependencies.get(0).getArtifactId());
		assertEquals("fragment", clientDependencies.get(1).getArtifactId());
        assertEquals("dep", clientDependencies.get(2).getArtifactId());
		assertEquals("fragment2", clientDependencies.get(3).getArtifactId());
	}

	public void testPre30() throws Exception {
        File pom = new File(getBasedir("projects/dummy"), "pom.xml");

	    MavenExecutionRequest request = newMavenExecutionRequest(pom);
        request.getUserProperties().put("tycho.targetPlatform", new File("src/test/resources/targetplatforms/pre-3.0").getCanonicalPath());

        MavenProject project = getSortedProjects(request).get(0);

        TychoProject projectType = lookup( TychoProject.class, project.getPackaging() );
        TargetPlatform platform = projectType.getTargetPlatform( project );

		assertNotNull(platform.getArtifact(ArtifactKey.TYPE_ECLIPSE_PLUGIN, "testjar", "1.0.0"));
		assertNotNull(platform.getArtifact(ArtifactKey.TYPE_ECLIPSE_PLUGIN, "testdir", "1.0.0"));

		File cacheDir = new File( request.getLocalRepository().getBasedir(), DefaultBundleReader.CACHE_PATH );

		assertTrue(new File(cacheDir, "testdir_1.0.0/META-INF/MANIFEST.MF").canRead());
		assertTrue(new File(cacheDir, "testjar_1.0.0/META-INF/MANIFEST.MF").canRead());
	}

	public void testMNGECLIPSE942() throws Exception {
        File pom = new File(getBasedir("projects/dummy"), "pom.xml");

        MavenExecutionRequest request = newMavenExecutionRequest(pom);
        request.getUserProperties().put("tycho.targetPlatform", new File("src/test/resources/targetplatforms/MNGECLIPSE-942").getCanonicalPath());

        MavenProject project = getSortedProjects(request).get(0);
        TychoProject projectType = lookup( TychoProject.class, project.getPackaging() );
        TargetPlatform platform = projectType.getTargetPlatform( project );

		assertEquals(2, platform.getArtifacts( ArtifactKey.TYPE_ECLIPSE_PLUGIN ).size());
		assertNotNull(platform.getArtifact(ArtifactKey.TYPE_ECLIPSE_PLUGIN, "org.junit4.nl_ru", null));
	}

    public void testMissingClasspathEntries()  throws Exception {
        File basedir = getBasedir( "projects/missingentry" );
        File pom = new File( basedir, "pom.xml" );
        MavenExecutionRequest request = newMavenExecutionRequest( pom );
        request.getUserProperties().put("tycho.targetPlatform", new File("src/test/resources/targetplatforms/missingentry").getCanonicalPath());
        request.getProjectBuildingRequest().setProcessPlugins( false );

        MavenProject project = getSortedProjects(request).get(0);

        OsgiBundleProject projectType = (OsgiBundleProject) lookup(TychoProject.class, project.getPackaging());

        List<ClasspathEntry> classpath = projectType.getClasspath(project);
        assertEquals(3, classpath.size());
        assertEquals(1, classpath.get(1).getLocations().size());
        assertEquals(canonicalFile("src/test/resources/targetplatforms/missingentry/plugins/dirbundle_0.0.1"), 
                     classpath.get(1).getLocations().get(0).getCanonicalFile());
        assertEquals(1, classpath.get(2).getLocations().size());
        assertEquals(canonicalFile("src/test/resources/targetplatforms/missingentry/plugins/jarbundle_0.0.1.jar"), 
                     classpath.get(2).getLocations().get(0).getCanonicalFile());
    }

    private File canonicalFile(String path) throws IOException {
        return new File(path).getCanonicalFile();
    }

}
