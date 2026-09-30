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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Test utility class to create Artifact objects, jar file or other files that Maven attaches to a project
 */
class TestArtifacts {
    static final String TEST_GROUPID = "org.test-group";
    static final String TEST_ARTIFACTID = "test-artifact";
    static final String TEST_VERSION = "9.10.2";
    static final String TEST_TYPE = "jar";
    static final String TEST_CLASSIFIER = "";

    static TestArtifact createArtifact(File file) throws IOException {
        return createArtifact(file, TEST_TYPE, TEST_CLASSIFIER);
    }

    static TestArtifact createArtifact(File file, String type, String classifier) throws IOException {
        return new TestArtifact(file, type, classifier);
    }

    /** What is left of an artifact for these tests: its file, type and classifier. */
    static final class TestArtifact {
        private final File file;
        private final String type;
        private final String classifier;

        TestArtifact(File file, String type, String classifier) {
            this.file = file;
            this.type = type;
            this.classifier = classifier;
        }

        File getFile() {
            return file;
        }

        String getClassifier() {
            return classifier;
        }

        String key() {
            return TEST_GROUPID + ":" + TEST_ARTIFACTID + ":" + type + ":" + classifier + ":" + TEST_VERSION;
        }
    }

    static TestArtifact createJarArtifact(File directory, String filename) throws IOException {
        return createJarArtifact(directory, filename, TEST_CLASSIFIER);
    }

    public static TestArtifact createJarArtifact(File directory, String filename, String classifier)
            throws IOException {
        return createJarArtifact(directory, filename, classifier, TEST_TYPE);
    }

    public static TestArtifact createJarArtifact(File directory, String filename, String classifier, String type)
            throws IOException {
        File file = new File(directory, filename);
        createDummyZipFile(file);
        return createArtifact(file, type, classifier);
    }

    static TestArtifact createPomArtifact(File directory, String filename) throws IOException {
        File file = new File(directory, filename);
        createDummyXMLFile(file);
        return createArtifact(file, TEST_TYPE, "");
    }

    /** Create a dummy JAR/ZIP file, enough to pass ZipInputStream.getNextEntry() */
    static File createDummyZipFile(File zipFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(zipFile))) {
            ZipEntry entry = new ZipEntry("dummy-entry.txt");
            zipOutputStream.putNextEntry(entry);
        }
        return zipFile;
    }

    /** Create a dummy signed JAR, enough to pass JarSignerUtil.isArchiveSigned() */
    static File createDummySignedJarFile(File jarFile) throws IOException {
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(jarFile))) {
            ZipEntry entry = new ZipEntry("dummy-entry.txt");
            zipOutputStream.putNextEntry(entry);
            zipOutputStream.putNextEntry(new ZipEntry("META-INF/dummy.RSA"));
        }

        return jarFile;
    }

    /** Create a dummy XML file, for example to simulate a pom.xml file */
    static File createDummyXMLFile(File xmlFile) throws IOException {
        Files.write(xmlFile.toPath(), "<project/>".getBytes());
        return xmlFile;
    }
}
