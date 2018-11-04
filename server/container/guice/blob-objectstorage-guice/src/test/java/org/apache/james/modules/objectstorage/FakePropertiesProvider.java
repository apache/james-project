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

package org.apache.james.modules.objectstorage;

import java.io.FileNotFoundException;

import org.apache.commons.configuration.Configuration;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.lang3.StringUtils;
import org.apache.james.utils.PropertiesProvider;

import com.google.common.collect.ImmutableMap;

public class FakePropertiesProvider extends PropertiesProvider {
    private ImmutableMap<String, Configuration> configurations;

    public FakePropertiesProvider(ImmutableMap<String, Configuration> configurations) {
        super(null);
        this.configurations = configurations;
    }


    @Override
    public Configuration getConfiguration(String fileName) throws FileNotFoundException, ConfigurationException {
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
