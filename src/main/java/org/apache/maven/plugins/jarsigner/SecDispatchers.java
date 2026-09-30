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

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.apache.maven.api.Session;
import org.codehaus.plexus.components.secdispatcher.Cipher;
import org.codehaus.plexus.components.secdispatcher.Dispatcher;
import org.codehaus.plexus.components.secdispatcher.MasterSource;
import org.codehaus.plexus.components.secdispatcher.SecDispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.DefaultSecDispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.cipher.AESGCMNoPadding;
import org.codehaus.plexus.components.secdispatcher.internal.dispatchers.LegacyDispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.dispatchers.MasterDispatcher;
import org.codehaus.plexus.components.secdispatcher.internal.sources.EnvMasterSource;
import org.codehaus.plexus.components.secdispatcher.internal.sources.GpgAgentMasterSource;
import org.codehaus.plexus.components.secdispatcher.internal.sources.PinEntryMasterSource;
import org.codehaus.plexus.components.secdispatcher.internal.sources.SystemPropertyMasterSource;

/**
 * Builds the password decryptor by hand. The Maven 4 API (4.0.0-rc-7) offers no service to decrypt a configuration
 * value, and the decryptor Maven uses for {@code settings.xml} is not reachable from a plugin, so this mirrors the
 * wiring of Maven's own standalone runner.
 */
final class SecDispatchers {

    private SecDispatchers() {}

    static SecDispatcher create(Session session) {
        Map<String, Cipher> ciphers = new HashMap<>();
        ciphers.put(AESGCMNoPadding.CIPHER_ALG, new AESGCMNoPadding());

        Map<String, MasterSource> sources = new HashMap<>();
        sources.put(EnvMasterSource.NAME, new EnvMasterSource());
        sources.put(GpgAgentMasterSource.NAME, new GpgAgentMasterSource());
        sources.put(PinEntryMasterSource.NAME, new PinEntryMasterSource());
        sources.put(SystemPropertyMasterSource.NAME, new SystemPropertyMasterSource());

        Map<String, Dispatcher> dispatchers = new HashMap<>();
        dispatchers.put(LegacyDispatcher.NAME, new LegacyDispatcher());
        dispatchers.put(MasterDispatcher.NAME, new MasterDispatcher(ciphers, sources));

        String userHome = session.getSystemProperties().getOrDefault("user.home", System.getProperty("user.home"));
        Path configurationFile = Paths.get(userHome, ".m2", "settings-security4.xml");
        return new DefaultSecDispatcher(dispatchers, configurationFile);
    }
}
