/*
 * 	Copyright 2006 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.codehaus.tycho.osgicompiler;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactResolutionRequest;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.ProjectArtifact;
import org.apache.maven.repository.RepositorySystem;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SimpleSourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.StaleSourceScanner;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.tycho.BundleProject;
import org.codehaus.tycho.TychoProject;
import org.codehaus.tycho.UnknownEnvironmentException;
import org.codehaus.tycho.osgicompiler.copied.AbstractCompilerMojo;
import org.codehaus.tycho.osgicompiler.copied.CompilationFailureException;
import org.codehaus.tycho.osgitools.DefaultClasspathEntry;
import org.codehaus.tycho.osgitools.OsgiBundleProject;
import org.codehaus.tycho.osgitools.project.BuildOutputJar;
import org.codehaus.tycho.osgitools.project.EclipsePluginProject;
import org.codehaus.tycho.utils.ExecutionEnvironment;
import org.codehaus.tycho.utils.ExecutionEnvironmentUtils;
import org.codehaus.tycho.utils.MavenArtifactRef;
import org.sonatype.tycho.classpath.ClasspathEntry;
import org.sonatype.tycho.classpath.ClasspathEntry.AccessRule;
import org.sonatype.tycho.classpath.JavaCompilerConfiguration;
import org.sonatype.tycho.classpath.SourcepathEntry;

public abstract class AbstractOsgiCompilerMojo extends AbstractCompilerMojo implements JavaCompilerConfiguration {

    public static final String RULE_SEPARATOR = File.pathSeparator;

//  public static final String RULE_INCLUDE_ALL = "**/*";

    /**
     * Exclude all but keep looking for other another match
     */
    public static final String RULE_EXCLUDE_ALL = "?**/*";

    private static final Set<String> MATCH_ALL = Collections.singleton("**/*");
	private static final String MANIFEST_HEADER_BUNDLE_REQ_EXEC_ENV = "Bundle-RequiredExecutionEnvironment";
	private static final Pattern COMMA_SEP_INCLUDING_WHITESPACE = Pattern
			.compile("\\s*,\\s*");

	/**
	 * @parameter expression="${project}"
	 */
	private MavenProject project;

	/**
	 * If set to true, compiler will use source folders defined in build.properties 
	 * file and will ignore ${project.compileSourceRoots}/${project.testCompileSourceRoots}.
	 * 
	 * Compilation will fail with an error, if this parameter is set to true
	 * but the project does not have valid build.properties file.
	 *  
	 * @parameter default-value="true" 
	 */
	private boolean usePdeSourceRoots;

	/**
	 * Transitively add specified maven artifacts to compile classpath 
	 * in addition to elements calculated according to OSGi rules. 
	 * All packages from additional entries will be accessible at compile time. 
	 * 
	 * Useful when OSGi runtime classpath contains elements not defined
	 * using normal dependency mechanisms. For example, when Eclipse Equinox
	 * is started from application server with -Dosgi.parentClassloader=fwk
	 * parameter.
	 * 
	 * DO NOT USE. This is a stopgap solution to allow refactoring of tycho-p2 code
	 * to a separate set of components.
	 *  
	 * @parameter 
	 */
	private MavenArtifactRef[] extraClasspathElements;

	/** @parameter expression="${session}" */
	private MavenSession session;

	/** @component */
	private RepositorySystem repositorySystem;

	/**
	 * A list of inclusion filters for the compiler.
	 * 
	 * @parameter
	 */
	private Set<String> includes = new HashSet<String>();

	/**
	 * A list of exclusion filters for the compiler.
	 * 
	 * @parameter
	 */
	private Set<String> excludes = new HashSet<String>();

	/**
	 * A list of exclusion filters for non-java resource files which should not
	 * be copied to the output directory.
	 * 
	 * @parameter
	 */
	private Set<String> excludeResources = new HashSet<String>();
	
	/**
	 * Current build output jar
	 */
	private BuildOutputJar outputJar;

	/**
	 * @component role="org.codehaus.tycho.TychoProject"
	 */
	private Map<String, TychoProject> projectTypes;

	public void execute() throws MojoExecutionException, CompilationFailureException {
		if (usePdeSourceRoots) {
			getLog().info("Using compile source roots from build.properties");
		}

		for (BuildOutputJar jar : getEclipsePluginProject().getOutputJars()) {
			this.outputJar = jar;
			this.outputJar.getOutputDirectory().mkdirs();
			super.execute();
			copyResources();
		}

		// this does not include classes from nested jars
		BuildOutputJar dotOutputJar = getEclipsePluginProject().getDotOutputJar();
		if (dotOutputJar != null) {
			project.getArtifact().setFile(dotOutputJar.getOutputDirectory());
		}
	}

	/*
	 * mimics the behavior of the PDE incremental builder which by default copies all
	 * (non-java) resource files in source directories into the target folder
	 */
	private void copyResources() throws MojoExecutionException {
		for (String sourceRoot : getCompileSourceRoots()) {
			// StaleSourceScanner.getIncludedSources throws IllegalStateException
			// if directory doesnt't exist
			File sourceRootFile = new File(sourceRoot);
			if (!sourceRootFile.isDirectory()) {
				getLog().warn("Source directory " + sourceRoot + " does not exist");
				continue;
			}

			Set<String> excludes = new HashSet<String>();
			excludes.addAll(excludeResources);
			excludes.add("**/*.java");
			StaleSourceScanner scanner = new StaleSourceScanner(0L, MATCH_ALL, excludes);
			CopyMapping copyMapping = new CopyMapping();
			scanner.addSourceMapping(copyMapping);
			try {
				scanner.getIncludedSources(sourceRootFile, this.outputJar
						.getOutputDirectory());
				for (CopyMapping.SourceTargetPair sourceTargetPair : copyMapping
						.getSourceTargetPairs()) {
					FileUtils.copyFile(new File(sourceRoot, sourceTargetPair.source),
							sourceTargetPair.target);
				}
			} catch (InclusionScanException e) {
				throw new MojoExecutionException(
						"Exception while scanning for resource files in "
								+ sourceRoot, e);
			} catch (IOException e) {
				throw new MojoExecutionException(
						"Exception copying resource files from " + sourceRoot
								+ " to " + this.outputJar.getOutputDirectory(),
						e);
			}
		}
	}

	/** public for testing purposes */ 
    public EclipsePluginProject getEclipsePluginProject() throws MojoExecutionException {
        return ((OsgiBundleProject) getBundleProject()).getEclipsePluginProject(project);
    }

	@Override
	protected File getOutputDirectory() {
		return outputJar.getOutputDirectory();
	}

	public List<String> getClasspathElements() throws MojoExecutionException {
	    final List<String> classpath = new ArrayList<String>();
	    for ( ClasspathEntry cpe : getClasspath() ) {
            for (File location: cpe.getLocations()) {
                classpath.add(location.getAbsolutePath() + toString( cpe.getAccessRules() ));
            }
        }
	    return classpath;
	}

	private BundleProject getBundleProject() throws MojoExecutionException {
		TychoProject projectType = projectTypes.get(project.getPackaging());
	    if (!(projectType instanceof BundleProject)) {
	        throw new MojoExecutionException("Not a bundle project " + project.toString());
	    }
		return (BundleProject) projectType;
	}

	private String toString(List<AccessRule> rules) {
        StringBuilder result = new StringBuilder(); // include all
        if (rules != null) {
            result.append("[");
            for (AccessRule rule : rules) {
                if (result.length() > 1) result.append(RULE_SEPARATOR);
                result.append(rule.isDiscouraged()? "~": "+");
                result.append(rule.getPattern());
            }
            if (result.length() > 1) result.append(RULE_SEPARATOR);
            result.append(RULE_EXCLUDE_ALL);
            result.append("]");
        } else {
            // include everything, not strictly necessary, but lets make this obvious
            //result.append("[+**/*]");
        }
        return result.toString();
    }

    protected final List<String> getCompileSourceRoots() throws MojoExecutionException {
		return usePdeSourceRoots? getPdeCompileSourceRoots(): getConfiguredCompileSourceRoots();
	}

    public List<SourcepathEntry> getSourcepath() throws MojoExecutionException {
        ArrayList<SourcepathEntry> entries = new ArrayList<SourcepathEntry>();
        for(BuildOutputJar jar : getEclipsePluginProject().getOutputJars()) {
            final File outputDirectory = jar.getOutputDirectory();
            for(final File sourcesRoot : jar.getSourceFolders() ) {
                SourcepathEntry entry = new SourcepathEntry() {
                    public File getSourcesRoot() {
                        return sourcesRoot;
                    }
                    public File getOutputDirectory() {
                        return outputDirectory;
                    }
                    public List<String> getIncludes() {
                        return null;
                    }
                    public List<String> getExcludes() {
                        return null;
                    }
                };
                entries.add(entry);
            }
        }
        return entries;
    }

	protected abstract List<String> getConfiguredCompileSourceRoots();

	protected SourceInclusionScanner getSourceInclusionScanner(int staleMillis) {
		SourceInclusionScanner scanner = null;

		if (includes.isEmpty() && excludes.isEmpty()) {
			scanner = new StaleSourceScanner(staleMillis);
		} else {
			if (includes.isEmpty()) {
				includes.add("**/*.java");
			}
			scanner = new StaleSourceScanner(staleMillis, includes, excludes);
		}
		return scanner;
	}

	protected SourceInclusionScanner getSourceInclusionScanner(
			String inputFileEnding) {
		SourceInclusionScanner scanner = null;

		if (includes.isEmpty() && excludes.isEmpty()) {
			includes = Collections.singleton("**/*." + inputFileEnding);
			scanner = new SimpleSourceInclusionScanner(includes,
					Collections.EMPTY_SET);
		} else {
			if (includes.isEmpty()) {
				includes.add("**/*." + inputFileEnding);
			}
			scanner = new SimpleSourceInclusionScanner(includes, excludes);
		}

		return scanner;
	}

	protected List<String> getPdeCompileSourceRoots() throws MojoExecutionException {
		ArrayList<String> roots = new ArrayList<String>();
		for (File folder : outputJar.getSourceFolders()) {
			try {
				roots.add(folder.getCanonicalPath());
			} catch (IOException e) {
				throw new MojoExecutionException("Unexpected IOException", e);
			}
		}
		return roots;
	}

	@Override
	protected CompilerConfiguration getCompilerConfiguration(List<String> compileSourceRoots) throws MojoExecutionException {
		CompilerConfiguration compilerConfiguration = super.getCompilerConfiguration(compileSourceRoots);
		if (usePdeSourceRoots) {
			Properties props = getEclipsePluginProject().getBuildProperties();
			String encoding = props.getProperty("javacDefaultEncoding." + outputJar.getName());
			if (encoding != null) {
				compilerConfiguration.setSourceEncoding(encoding);
			}
		}
		configureSourceAndTargetLevel(compilerConfiguration);
		// TODO how to set target JRE? 
		// https://issues.sonatype.org/browse/TYCHO-9
		return compilerConfiguration;
	}

	private void configureSourceAndTargetLevel(
			CompilerConfiguration compilerConfiguration)
			throws MojoExecutionException {
		String[] executionEnvironments = getExecutionEnvironments();
		if (executionEnvironments.length == 0) {
			return;
		}
		compilerConfiguration.setSourceVersion(getSourceLevel(executionEnvironments));
		compilerConfiguration.setTargetVersion(getTargetLevel(executionEnvironments));
	}

	private String[] getExecutionEnvironments() throws MojoExecutionException {
		String requiredExecEnvs = getBundleProject().getManifestValue(
				MANIFEST_HEADER_BUNDLE_REQ_EXEC_ENV, project);
		if (requiredExecEnvs == null) {
			return new String[0];
		}
		return COMMA_SEP_INCLUDING_WHITESPACE.split(requiredExecEnvs.trim());
	}

	private String getMinimalTargetVersion(String[] executionEnvironments)
			throws UnknownEnvironmentException {
		if (executionEnvironments.length == 1) {
			ExecutionEnvironment executionEnvironment = ExecutionEnvironmentUtils
					.getExecutionEnvironment(executionEnvironments[0]);
			return executionEnvironment.getCompilerTargetLevel();
		}
		List<String> targetLevels = new ArrayList<String>(
				executionEnvironments.length);
		for (String executionEnvironment : executionEnvironments) {
			targetLevels.add(ExecutionEnvironmentUtils
					.getExecutionEnvironment(executionEnvironment).getCompilerTargetLevel());
		}
		return Collections.min(targetLevels);
	}

	private String getMinimalSourceVersion(String[] executionEnvironments)
			throws UnknownEnvironmentException {
		if (executionEnvironments.length == 1) {
			ExecutionEnvironment executionEnvironment = ExecutionEnvironmentUtils
					.getExecutionEnvironment(executionEnvironments[0]);
			return executionEnvironment.getCompilerSourceLevel();
		}
		List<String> sourceLevels = new ArrayList<String>(
				executionEnvironments.length);
		for (String executionEnvironment : executionEnvironments) {
			sourceLevels.add(ExecutionEnvironmentUtils.getExecutionEnvironment(
					executionEnvironment).getCompilerSourceLevel());
		}
		return Collections.min(sourceLevels);
	}

	public List<ClasspathEntry> getClasspath() throws MojoExecutionException {
        TychoProject projectType = getBundleProject();
	    ArrayList<ClasspathEntry> classpath = new ArrayList<ClasspathEntry>(((BundleProject) projectType).getClasspath(project));

        if (extraClasspathElements != null) {
            ArtifactRepository localRepository = session.getLocalRepository();
            List<ArtifactRepository> remoteRepositories = project.getRemoteArtifactRepositories();
            for (MavenArtifactRef a : extraClasspathElements) {
                Artifact artifact = repositorySystem.createArtifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), "jar");

                ArtifactResolutionRequest request = new ArtifactResolutionRequest();
                request.setArtifact( artifact );
                request.setLocalRepository( localRepository );
                request.setRemoteRepositories( remoteRepositories );
                request.setResolveRoot( true );
                request.setResolveTransitively( true );
                ArtifactResolutionResult result = repositorySystem.resolve( request );
                
                if (result.hasExceptions()) {
                    throw new MojoExecutionException("Could not resolve extra classpath entry", result.getExceptions().get(0));
                }

                for (Artifact b : result.getArtifacts()) {
                    MavenProject bProject = null;
                    if (b instanceof ProjectArtifact) {
                        bProject = ((ProjectArtifact) b).getProject();
                    }
                    ArrayList<File> bLocations = new ArrayList<File>();
                    bLocations.add(b.getFile()); // TODO properly handle multiple project locations maybe
                    classpath.add(new DefaultClasspathEntry( bProject, null, bLocations, null));
                }
            }
        }
        return classpath;
	}

	public String getExecutionEnvironment() throws MojoExecutionException {
	    String[] environments = getExecutionEnvironments();
	    return environments!= null && environments.length > 0? environments[0]: null;
	}

	public String getSourceLevel() throws MojoExecutionException {
        return getSourceLevel(getExecutionEnvironments());
	}

    private String getSourceLevel(String[] executionEnvironments) {
        if (source != null) {
            // explicit pom configuration wins
            return source;
        }
        if (executionEnvironments != null && executionEnvironments.length > 0) {
            try {
                return getMinimalSourceVersion(executionEnvironments);
            } catch (UnknownEnvironmentException e) {
                // fall through
            }
        }
        return DEFAULT_SOURCE_VERSION;
    }

    public String getTargetLevel() throws MojoExecutionException {
        return getTargetLevel(getExecutionEnvironments());
    }

    public String getTargetLevel(String[] executionEnvironments) {
        if (target != null) {
            // explicit pom configuration wins
            return target;
        }
        if (executionEnvironments != null && executionEnvironments.length > 0) {
            try {
                return getMinimalTargetVersion(executionEnvironments);
            } catch (UnknownEnvironmentException e) {
                // fall through
            }
        }
        return DEFAULT_TARGET_VERSION;
    }
}
