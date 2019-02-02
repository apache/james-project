/****************************************************************
 * Licensed to the Apache Software Foundation (ASF) under one   *
 * or more contributor license agreements.  See the NOTICE file *
 * distributed with this work for additional information        *
 * regarding copyright ownership.  The ASF licenses this file   *
 * to you under the Apache License, Version 2.0 (the            *
 * "License"); you may not use this file except in compliance   *
 * with the License.  You may obtain a copy of the License at   *
 *                                                              *
 *   http://www.apache.org/licenses/LICENSE-2.0                 *
 *                                                              *
 * Unless required by applicable law or agreed to in writing,   *
 * software distributed under the License is distributed on an  *
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY       *
 * KIND, either express or implied.  See the License for the    *
 * specific language governing permissions and limitations      *
 * under the License.                                           *
 ****************************************************************/

package org.apache.james.server.core.configuration;

import java.io.File;
import java.util.Optional;

import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.filesystem.api.JamesDirectoriesProvider;
import org.apache.james.server.core.JamesServerResourceLoader;
import org.apache.james.server.core.MissingArgumentException;

public class Configuration {

    public static final String WORKING_DIRECTORY = "working.directory";

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private Optional<String> rootDirectory;
        private Optional<String> configurationPath;

        private Builder() {
            rootDirectory = Optional.empty();
            configurationPath = Optional.empty();
        }

        public Builder workingDirectory(String path) {
            rootDirectory = Optional.of(path);
            return this;
        }

        public Builder workingDirectory(File file) {
            rootDirectory = Optional.of(file.getAbsolutePath());
            return this;
        }

        public Builder useWorkingDirectoryEnvProperty() {
            rootDirectory = Optional
                .ofNullable(System.getProperty(WORKING_DIRECTORY));
            if (!rootDirectory.isPresent()) {
                throw new MissingArgumentException("Server needs a working.directory env entry");
            }
            return this;
        }

        public Builder configurationPath(String path) {
            configurationPath = Optional.of(path);
            return this;
        }

        public Builder configurationFromClasspath() {
            configurationPath = Optional.of(FileSystem.CLASSPATH_PROTOCOL);
            return this;
        }

        public Configuration build() {
            return new Configuration(
                rootDirectory
                    .orElseThrow(() -> new MissingArgumentException("Server needs a working.directory env entry")),
                configurationPath.orElse(FileSystem.FILE_PROTOCOL_AND_CONF));
        }
    }

    private final String configurationPath;
    private final JamesDirectoriesProvider directoriesProvider;

    private Configuration(String rootDirectory, String configurationPath) {
        this.configurationPath = configurationPath;
        this.directoriesProvider = new JamesServerResourceLoader(rootDirectory);
    }

    public String configurationPath() {
        return configurationPath;
    }

    public JamesDirectoriesProvider directories() {
        return directoriesProvider;
    }

}
