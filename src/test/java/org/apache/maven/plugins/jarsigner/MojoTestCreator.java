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
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.UnaryOperator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.maven.api.ProducedArtifact;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.Mojo;
import org.apache.maven.api.services.ArtifactManager;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.api.services.ToolchainManager;
import org.apache.maven.api.settings.Settings;
import org.apache.maven.plugins.jarsigner.JarsignerSignMojo.WaitStrategy;
import org.apache.maven.shared.jarsigner.JarSigner;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Creates and configures a Mojo instance to be used in testing.
 *
 * @param <T> The type of Mojo
 */
public class MojoTestCreator<T extends AbstractJarsignerMojo> {

    private final Logger logger = LoggerFactory.getLogger(MojoTestCreator.class);

    private final Class<T> clazz;
    private final ProjectStub project;
    private final File projectDir;
    private final JarSigner jarSigner;
    private ToolchainManager toolchainManager;
    private UnaryOperator<String> decryptor;
    private WaitStrategy waitStrategy;
    private Log log;
    private List<Field> fields;
    private Settings settings;
    private final ArtifactManager artifactManager = mock(ArtifactManager.class);

    public MojoTestCreator(Class<T> clazz, ProjectStub project, File projectDir, JarSigner jarSigner) throws Exception {
        this.clazz = clazz;
        this.project = project;
        this.projectDir = projectDir;
        this.jarSigner = jarSigner;

        decryptor = str -> str; // Simple SecDispatcher that only returns parameter
        fields = getAllFields(clazz);
    }

    public void setToolchainManager(ToolchainManager toolchainManager) {
        this.toolchainManager = toolchainManager;
    }

    public void setSecDispatcher(UnaryOperator<String> decryptor) {
        this.decryptor = decryptor;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

    public void setWaitStrategy(WaitStrategy waitStrategy) {
        this.waitStrategy = waitStrategy;
    }

    public void setLog(Log log) {
        this.log = log;
    }

    /**
     * Creates and configures the Mojo instance.
     *
     * @param configuration user supplied configuration
     */
    public T configure(Map<String, String> configuration, Class<?>... parameterTypes) throws Exception {
        T mojo = clazz.getDeclaredConstructor(parameterTypes).newInstance();
        setDefaultValues(mojo);

        setAttribute(mojo, "project", mock(Project.class));
        if (settings != null) {
            Session session = mock(Session.class);
            when(session.getSettings()).thenReturn(settings);
            setAttribute(mojo, "session", session);
        }
        setAttribute(mojo, "projectManager", createProjectManager());
        setAttribute(mojo, "artifactManager", artifactManager);
        setAttribute(mojo, "jarSigner", jarSigner);
        setAttribute(mojo, "securityDispatcher", createSecDispatcher());
        if (toolchainManager != null) {
            setAttribute(mojo, "toolchainManager", toolchainManager);
        }
        if (waitStrategy != null) {
            ((JarsignerSignMojo) mojo).setWaitStrategy(waitStrategy);
        }
        if (log != null) {
            setAttribute(mojo, "log", log);
        }

        for (Map.Entry<String, String> entry : configuration.entrySet()) {
            Field field = getField(mojo, entry.getKey());
            setFieldByStringValue(mojo, field, entry.getValue());
        }

        return mojo;
    }

    private ProjectManager createProjectManager() {
        ProjectManager projectManager = mock(ProjectManager.class);
        when(projectManager.getPath(any())).thenAnswer(invocation -> {
            TestArtifacts.TestArtifact artifact = project.getArtifact();
            return artifact == null
                    ? Optional.empty()
                    : Optional.of(artifact.getFile().toPath());
        });
        when(projectManager.getAttachedArtifacts(any())).thenAnswer(invocation -> {
            List<TestArtifacts.TestArtifact> attached = project.getAttachedArtifacts();
            List<ProducedArtifact> result = new ArrayList<>();
            if (attached != null) {
                for (TestArtifacts.TestArtifact testArtifact : attached) {
                    ProducedArtifact artifact = mock(ProducedArtifact.class);
                    when(artifact.getClassifier()).thenReturn(testArtifact.getClassifier());
                    when(artifact.key()).thenReturn(testArtifact.key());
                    when(artifactManager.getPath(artifact))
                            .thenReturn(Optional.of(testArtifact.getFile().toPath()));
                    result.add(artifact);
                }
            }
            return result;
        });
        return projectManager;
    }

    private SecDispatcher createSecDispatcher() throws Exception {
        SecDispatcher securityDispatcher = mock(SecDispatcher.class);
        when(securityDispatcher.decrypt(any())).thenAnswer(invocation -> decryptor.apply(invocation.getArgument(0)));
        return securityDispatcher;
    }

    private void setFieldByStringValue(Object instance, Field field, String stringValue) throws Exception {
        field.setAccessible(true);
        Class<?> fieldType = field.getType();
        if (fieldType.equals(String.class)) {
            field.set(instance, stringValue);
        } else if (fieldType.equals(int.class)) {
            field.setInt(instance, Integer.parseInt(stringValue));
        } else if (fieldType.equals(boolean.class)) {
            field.setBoolean(instance, Boolean.parseBoolean(stringValue));
        } else if (fieldType.equals(File.class)) {
            field.set(instance, new File(stringValue));
        } else if (fieldType.equals(Path.class)) {
            field.set(instance, new File(stringValue).toPath());
        } else if (fieldType.equals(String[].class)) {
            if (stringValue.isEmpty()) {
                // Maven defaults to empty list if no default value exists
                field.set(instance, new String[0]);
            } else {
                String[] values = stringValue.split(",");
                field.set(instance, values);
            }
        } else {
            if (!stringValue.startsWith("${")) {
                logger.warn(
                        "Not implemented support to set of field of type {} to value {}",
                        fieldType.getSimpleName(),
                        stringValue);
            }
        }
    }

    private Field getField(Object instance, String fieldName) {
        return fields.stream()
                .filter(f -> f.getName().equals(fieldName))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Could not find field " + fieldName + " in class "
                        + instance.getClass().getName()));
    }

    private void setAttribute(Object instance, String fieldName, Object value) throws Exception {
        Field field = getField(instance, fieldName);
        field.setAccessible(true);
        field.set(instance, value);
    }

    private void setDefaultValues(Mojo mojo) throws Exception {
        Map<String, String> defaultConfiguration = PluginXmlParser.getMojoDefaultConfiguration(mojo.getClass());
        for (String parameterName : defaultConfiguration.keySet()) {
            String defaultValue = defaultConfiguration.get(parameterName);
            defaultValue = substituteParameterValueVariables(defaultValue);
            Field field = getField(mojo, parameterName);
            setFieldByStringValue(mojo, field, defaultValue.toString());
        }
    }

    private String substituteParameterValueVariables(String parameterValue) {
        parameterValue = parameterValue.replaceAll(
                Pattern.quote("${project.basedir}"), Matcher.quoteReplacement(projectDir.getPath()));
        return parameterValue;
    }

    static List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> currentClazz = clazz;
        while (currentClazz != null) {
            fields.addAll(Arrays.asList(currentClazz.getDeclaredFields()));
            currentClazz = currentClazz.getSuperclass();
        }
        return fields;
    }
}
