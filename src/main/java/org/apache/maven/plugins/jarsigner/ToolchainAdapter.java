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

import org.apache.maven.api.Toolchain;

/**
 * Hands a Maven 4 toolchain to maven-jarsigner. The library finds the tool by calling {@code findTool(String)}
 * reflectively on the class of the object it is given, which fails for the non-public toolchain class Maven 4 creates
 * and then silently falls back to the JDK running Maven. This class is public for that reason.
 */
public final class ToolchainAdapter {

    private final Toolchain toolchain;

    ToolchainAdapter(Toolchain toolchain) {
        this.toolchain = toolchain;
    }

    /**
     * Called reflectively by maven-jarsigner.
     *
     * @param toolName the name of the tool, such as {@code jarsigner}
     * @return the path of the tool, or {@code null} if the toolchain has none
     */
    public String findTool(String toolName) {
        return toolchain.findTool(toolName);
    }

    @Override
    public String toString() {
        return toolchain.toString();
    }
}
