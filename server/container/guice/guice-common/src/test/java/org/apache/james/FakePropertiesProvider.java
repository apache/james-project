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

package org.apache.james;

import java.io.FileNotFoundException;
import java.util.Arrays;
import java.util.Optional;

import org.apache.commons.configuration2.Configuration;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableMap;

public class FakePropertiesProvider extends PropertiesProvider {

    public static final FileSystem NULL_FILE_SYSTEM = null;

    private ImmutableMap<String, Configuration> configurations;

    public FakePropertiesProvider(ImmutableMap<String, Configuration> configurations) {
        super(NULL_FILE_SYSTEM, org.apache.james.server.core.configuration.Configuration.builder()
            .workingDirectory("fakePath")
            .build()
            .configurationPath());
        this.configurations = configurations;
    }


    @Override
    public Configuration getConfiguration(String fileName) throws FileNotFoundException {
        if (configurations.containsKey(fileName)) {
            return configurations.get(fileName);
        } else {
            throw new FileNotFoundException(
                "no configuration defined for " +
                    fileName +
                    " know configurations are (" +
                    StringUtils.join(configurations.keySet(), ",") +
                    ")");
        }
    }

    @Override
    public Configuration getConfigurations(String... filenames) throws FileNotFoundException {
        return Arrays.stream(filenames)
            .map(filename -> Optional.ofNullable(configurations.get(filename)))
            .flatMap(Optional::stream)
            .findFirst()
            .orElseThrow(() -> new FileNotFoundException(
                "no configuration defined for " +
                    StringUtils.join(filenames, ",") +
                    " know configurations are (" +
                    StringUtils.join(configurations.keySet(), ",") +
                    ")"));
    }

    public static FakePropertiesProviderBuilder builder() {
        return new FakePropertiesProviderBuilder();
    }

    public static class FakePropertiesProviderBuilder {
        private final ImmutableMap.Builder<String, Configuration> configurations;

        public FakePropertiesProviderBuilder() {
            configurations = new ImmutableMap.Builder<>();
        }

        public FakePropertiesProviderBuilder register(String filename,
                                                      Configuration configuration) {
            configurations.put(filename, configuration);
            return this;
        }

        public FakePropertiesProvider build() {
            return new FakePropertiesProvider(configurations.build());
        }
    }
}
