/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.jarsigner;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.StringJoiner;

import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Toolchain;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.api.settings.Proxy;
import org.apache.maven.shared.jarsigner.DefaultJarSigner;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.apache.maven.shared.jarsigner.JarSignerRequest;
import org.apache.maven.shared.jarsigner.JarSignerUtil;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.javatool.JavaToolException;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.codehaus.plexus.components.secdispatcher.SecDispatcherException;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

/**
 * Maven Jarsigner Plugin base class.
 *
 * @author <a href="cs@schulte.it">Christian Schulte</a>
 */
public abstract class AbstractJarsignerMojo implements Mojo {

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.verbose", defaultValue = "false")
    private boolean verbose;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.keystore")
    private String keystore;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.storetype")
    private String storetype;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.storepass")
    private String storepass;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.providerName")
    private String providerName;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.providerClass")
    private String providerClass;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.providerArg")
    private String providerArg;

    /**
     * See <a href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/jarsigner.html#Options">options</a>.
     */
    @Parameter(property = "jarsigner.alias")
    private String alias;

    /**
     * The maximum memory available to the JAR signer, e.g. <code>256M</code>. See <a
     * href="https://docs.oracle.com/javase/7/docs/technotes/tools/windows/java.html#Xms">-Xmx</a> for more details.
     */
    @Parameter(property = "jarsigner.maxMemory")
    private String maxMemory;

    /**
     * Archive to process. If set, neither the project artifact nor any attachments or archive sets are processed.
     */
    @Parameter(property = "jarsigner.archive")
    private Path archive;

    /**
     * The base directory to scan for JAR files using Ant-like inclusion/exclusion patterns.
     *
     * @since 1.1
     */
    @Parameter(property = "jarsigner.archiveDirectory")
    private Path archiveDirectory;

    /**
     * The Ant-like inclusion patterns used to select JAR files to process. The patterns must be relative to the
     * directory given by the parameter {@link #archiveDirectory}. By default, the pattern
     * <code>&#42;&#42;/&#42;.?ar</code> is used.
     *
     * @since 1.1
     */
    @Parameter
    private String[] includes = {"**/*.?ar"};

    /**
     * The Ant-like exclusion patterns used to exclude JAR files from processing. The patterns must be relative to the
     * directory given by the parameter {@link #archiveDirectory}.
     *
     * @since 1.1
     */
    @Parameter
    private String[] excludes = {};

    /**
     * List of additional arguments to append to the jarsigner command line. Each argument should be specified as a
     * separate element. For example, to specify the name of the signed jar, two elements are needed:
     * <ul>
     *     <li>Alternative using the command line: {@code -Djarsigner.arguments="-signedjar,my-project_signed.jar"}</li>
     *     <li>Alternative using the Maven POM configuration:</li>
     * </ul>
     * <pre>
     * {@code
     * <configuration>
     *   <arguments>
     *     <argument>-signedjar</argument>
     *     <argument>my-project_signed.jar</argument>
     *   </arguments>
     * </configuration>
     * }</pre>
     */
    @Parameter(property = "jarsigner.arguments")
    private String[] arguments;

    /**
     * Set to {@code true} to disable the plugin.
     */
    @Parameter(property = "jarsigner.skip", defaultValue = "false")
    private boolean skip;

    /**
     * Controls processing of the main artifact produced by the project.
     *
     * @since 1.1
     */
    @Parameter(property = "jarsigner.processMainArtifact", defaultValue = "true")
    private boolean processMainArtifact;

    /**
     * Controls processing of project attachments. If enabled, attached artifacts that are no JAR/ZIP files will be
     * automatically excluded from processing.
     *
     * @since 1.1
     */
    @Parameter(property = "jarsigner.processAttachedArtifacts", defaultValue = "true")
    private boolean processAttachedArtifacts;

    /**
     * Must be set to true if the password must be given via a protected
     * authentication path such as a dedicated PIN reader.
     *
     * @since 1.3
     */
    @Parameter(property = "jarsigner.protectedAuthenticationPath", defaultValue = "false")
    private boolean protectedAuthenticationPath;

    /**
     * A set of artifact classifiers describing the project attachments that should be processed. This parameter is only
     * relevant if {@link #processAttachedArtifacts} is <code>true</code>. If empty, all attachments are included.
     *
     * @since 1.2
     */
    @Parameter
    private String[] includeClassifiers;

    /**
     * A set of artifact classifiers describing the project attachments that should not be processed. This parameter is
     * only relevant if {@link #processAttachedArtifacts} is <code>true</code>. If empty, no attachments are excluded.
     *
     * @since 1.2
     */
    @Parameter
    private String[] excludeClassifiers;

    /**
     * Location of the working directory.
     *
     * @since 1.3
     */
    @Parameter(defaultValue = "${project.basedir}")
    private Path workingDirectory;

    @Inject
    private Log log;

    @Inject
    private Project project;

    @Inject
    private Session session;

    /** Not injectable in Maven 4.0.0-rc-7 (no DI binding); looked up from the session on first use. */
    private ProjectManager projectManager;

    @Inject
    private ArtifactManager artifactManager;

    private JarSigner jarSigner = new DefaultJarSigner();

    /**
     * To obtain a toolchain if possible.
     *
     * @since 1.3
     */
    @Inject
    private ToolchainManager toolchainManager;

    /**
     * Not injectable: the Maven 4 API has no service for decrypting values, see {@link SecDispatchers}.
     *
     * @since 1.3.2
     */
    private SecDispatcher securityDispatcher;

    protected Log getLog() {
        return log;
    }

    private ProjectManager getProjectManager() {
        if (projectManager == null) {
            projectManager = session.getService(ProjectManager.class);
        }
        return projectManager;
    }

    private SecDispatcher getSecurityDispatcher() {
        if (securityDispatcher == null) {
            securityDispatcher = SecDispatchers.create(session);
        }
        return securityDispatcher;
    }

    @Override
    public final void execute() throws MojoException {
        if (this.skip) {
            getLog().info(getMessage("disabled"));
            return;
        }

        validateParameters();

        Toolchain toolchain = getToolchain();
        if (toolchain != null) {
            getLog().info("Toolchain in maven-jarsigner-plugin: " + toolchain);
            jarSigner.setToolchain(new ToolchainAdapter(toolchain));
        }

        List<File> archives = findJarfiles();
        processArchives(archives);
        getLog().info(getMessage("processed", archives.size()));
    }

    /**
     * Finds all jar files, by looking at the Maven project and user configuration.
     *
     * @return a List of File objects
     * @throws MojoException if it was not possible to build a list of jar files
     */
    private List<File> findJarfiles() throws MojoException {
        if (this.archive != null) {
            // Only process this, but nothing more
            return Arrays.asList(this.archive.toFile());
        }

        List<File> archives = new ArrayList<>();
        if (processMainArtifact) {
            Optional<Path> mainArtifact = getProjectManager().getPath(project);
            if (mainArtifact.isPresent()) {
                getFileFromPath(mainArtifact.get(), project.getId()).ifPresent(archives::add);
            } else {
                logUnsupported(project.getId());
            }
        }

        if (processAttachedArtifacts) {
            Collection<String> includes = new HashSet<>();
            if (includeClassifiers != null) {
                includes.addAll(Arrays.asList(includeClassifiers));
            }

            Collection<String> excludes = new HashSet<>();
            if (excludeClassifiers != null) {
                excludes.addAll(Arrays.asList(excludeClassifiers));
            }

            for (ProducedArtifact artifact : getProjectManager().getAttachedArtifacts(project)) {
                if (!includes.isEmpty() && !includes.contains(artifact.getClassifier())) {
                    continue;
                }

                if (excludes.contains(artifact.getClassifier())) {
                    continue;
                }

                Optional<Path> path = artifactManager.getPath(artifact);
                if (path.isPresent()) {
                    getFileFromPath(path.get(), artifact.key()).ifPresent(archives::add);
                } else {
                    logUnsupported(artifact.key());
                }
            }
        } else {
            if (verbose) {
                getLog().info(getMessage("ignoringAttachments"));
            } else {
                getLog().debug(getMessage("ignoringAttachments"));
            }
        }

        if (archiveDirectory != null) {
            String includeList = joinStrings(includes);
            String excludeList = joinStrings(excludes);

            try {
                archives.addAll(FileUtils.getFiles(archiveDirectory.toFile(), includeList, excludeList));
            } catch (IOException e) {
                throw new MojoException("Failed to scan archive directory for JARs: " + e.getMessage(), e);
            }
        }

        return archives;
    }

    /**
     * Joins a string array with commas.
     *
     * @param strings the strings to join
     * @return the comma-separated string, or {@code null} if the input is {@code null}
     */
    private static String joinStrings(String[] strings) {
        if (strings == null) {
            return null;
        }
        StringJoiner joiner = new StringJoiner(",");
        for (String s : strings) {
            joiner.add(s);
        }
        return joiner.toString();
    }

    /**
     * Creates the jar signer request to be executed.
     *
     * @param archive the archive file to treat by jarsigner
     * @return the request
     * @throws MojoException if an exception occurs
     * @since 1.3
     */
    protected abstract JarSignerRequest createRequest(File archive) throws MojoException;

    /**
     * Gets a string representation of a {@code Commandline}.
     * <p>
     * This method creates the string representation by calling {@code commandLine.toString()} by default.
     * </p>
     *
     * @param commandLine The {@code Commandline} to get a string representation of.
     * @return The string representation of {@code commandLine}.
     * @throws NullPointerException if {@code commandLine} is {@code null}
     */
    protected String getCommandlineInfo(final Commandline commandLine) {
        if (commandLine == null) {
            throw new NullPointerException("commandLine");
        }

        String commandLineInfo = commandLine.toString();
        commandLineInfo = StringUtils.replace(commandLineInfo, this.storepass, "'*****'");
        return commandLineInfo;
    }

    public String getStoretype() {
        return storetype;
    }

    public String getStorepass() {
        return storepass;
    }

    /**
     * Examines the file of an artifact.
     *
     * @param path the file of the artifact
     * @param description what to call the artifact in log messages
     * @return An Optional containing the File, or Optional.empty() if the File is not a jar file.
     */
    private Optional<File> getFileFromPath(final Path path, final String description) {
        File file = path.toFile();
        if (JarSignerUtil.isZipFile(file)) {
            return Optional.of(file);
        }
        logUnsupported(description);
        return Optional.empty();
    }

    private void logUnsupported(final String description) {
        if (this.verbose) {
            getLog().info(getMessage("unsupported", description));
        } else if (getLog().isDebugEnabled()) {
            getLog().debug(getMessage("unsupported", description));
        }
    }

    /**
     * Pre-processes a given archive.
     *
     * @param archive The archive to process, must not be <code>null</code>.
     * @throws MojoException if pre-processing failed
     */
    protected void preProcessArchive(final File archive) throws MojoException {
        // Default implementation does nothing
    }

    /**
     * Validate the user supplied configuration/parameters.
     *
     * @throws MojoException if the user supplied configuration make further execution impossible
     */
    protected void validateParameters() throws MojoException {
        // Default implementation does nothing
    }

    /**
     * Process (sign/verify) a list of archives.
     *
     * @param archives list of jar files to process
     * @throws MojoException if an error occurs during the processing of archives
     */
    protected void processArchives(List<File> archives) throws MojoException {
        for (File file : archives) {
            processArchive(file);
        }
    }

    /**
     * Processes a given archive.
     *
     * @param archive The archive to process.
     * @throws NullPointerException if {@code archive} is {@code null}
     * @throws MojoException if processing {@code archive} fails
     */
    protected final void processArchive(final File archive) throws MojoException {
        if (archive == null) {
            throw new NullPointerException("archive");
        }

        preProcessArchive(archive);

        if (this.verbose) {
            getLog().info(getMessage("processing", archive));
        } else if (getLog().isDebugEnabled()) {
            getLog().debug(getMessage("processing", archive));
        }

        JarSignerRequest request = createRequest(archive);
        request.setVerbose(verbose);
        request.setAlias(alias);
        request.setArchive(archive);
        request.setKeystore(keystore);
        request.setStoretype(storetype);
        request.setProviderArg(providerArg);
        request.setProviderClass(providerClass);
        request.setProviderName(providerName);
        request.setWorkingDirectory(workingDirectory != null ? workingDirectory.toFile() : null);
        request.setMaxMemory(maxMemory);
        request.setProtectedAuthenticationPath(protectedAuthenticationPath);

        // Preserves 'file.encoding' the plugin is executed with.
        final List<String> additionalArguments = new ArrayList<>();

        boolean fileEncodingSeen = false;

        if (this.arguments != null) {
            for (final String argument : this.arguments) {
                if (argument.trim().startsWith("-J-Dfile.encoding=")) {
                    fileEncodingSeen = true;
                }

                additionalArguments.add(argument);
            }
        }

        if (!fileEncodingSeen) {
            additionalArguments.add(
                    "-J-Dfile.encoding=" + Charset.defaultCharset().displayName());
        }

        // Adds proxy information.
        Proxy proxy = getActiveProxy();
        if (proxy != null && StringUtils.isNotEmpty(proxy.getHost())) {
            additionalArguments.add("-J-Dhttp.proxyHost=" + proxy.getHost());
            additionalArguments.add("-J-Dhttps.proxyHost=" + proxy.getHost());
            additionalArguments.add("-J-Dftp.proxyHost=" + proxy.getHost());

            if (proxy.getPort() > 0) {
                additionalArguments.add("-J-Dhttp.proxyPort=" + proxy.getPort());
                additionalArguments.add("-J-Dhttps.proxyPort=" + proxy.getPort());
                additionalArguments.add("-J-Dftp.proxyPort=" + proxy.getPort());
            }

            if (StringUtils.isNotEmpty(proxy.getNonProxyHosts())) {
                additionalArguments.add("-J-Dhttp.nonProxyHosts=\"" + proxy.getNonProxyHosts() + "\"");

                additionalArguments.add("-J-Dftp.nonProxyHosts=\"" + proxy.getNonProxyHosts() + "\"");
            }
        }

        request.setArguments(
                !additionalArguments.isEmpty()
                        ? additionalArguments.toArray(new String[additionalArguments.size()])
                        : null);

        // Special handling for passwords through the Maven Security Dispatcher
        request.setStorepass(decrypt(storepass));

        try {
            executeJarSigner(jarSigner, request);
        } catch (JavaToolException e) {
            throw new MojoException(getMessage("commandLineException", e.getMessage()), e);
        }
    }

    /**
     * Mirrors {@code Settings.getActiveProxy()} of the Maven 3 settings model: the first proxy marked active.
     */
    private Proxy getActiveProxy() {
        if (session == null || session.getSettings() == null) {
            return null;
        }
        return session.getSettings().getProxies().stream()
                .filter(Proxy::isActive)
                .findFirst()
                .orElse(null);
    }

    /**
     * Executes jarsigner (execute signing or verification for a jar file).
     *
     * @param jarSigner the JarSigner execution interface
     * @param request the JarSignerRequest with parameters JarSigner should use
     * @throws JavaToolException if jarsigner could not be invoked
     * @throws MojoException if the invocation of jarsigner succeeded, but returned a non-zero exit code
     */
    protected abstract void executeJarSigner(JarSigner jarSigner, JarSignerRequest request)
            throws JavaToolException, MojoException;

    protected String decrypt(String encoded) throws MojoException {
        if (encoded == null) {
            return null;
        }
        try {
            return getSecurityDispatcher().decrypt(encoded);
        } catch (SecDispatcherException | IOException e) {
            getLog().error("error using security dispatcher: " + e.getMessage(), e);
            throw new MojoException("error using security dispatcher: " + e.getMessage(), e);
        }
    }

    /**
     * Gets a message for a given key from the resource bundle backing the implementation.
     *
     * @param key the key of the message to return
     * @param args arguments to format the message with
     * @return the message with key {@code key} from the resource bundle backing the implementation
     * @throws NullPointerException if {@code key} is {@code null}
     * @throws java.util.MissingResourceException
     *             if there is no message available matching {@code key} or accessing
     *             the resource bundle fails
     */
    String getMessage(final String key, final Object... args) {
        if (key == null) {
            throw new NullPointerException("key");
        }

        return new MessageFormat(ResourceBundle.getBundle("jarsigner").getString(key)).format(args);
    }

    private Toolchain getToolchain() {
        if (toolchainManager != null) {
            return toolchainManager.getToolchainFromBuildContext(session, "jdk").orElse(null);
        }
        return null;
    }
}
