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
import java.util.List;

import org.apache.commons.configuration2.HierarchicalConfiguration;
import org.apache.commons.configuration2.tree.ImmutableNode;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.protocols.lib.SslConfig;
import org.apache.james.server.core.configuration.ConfigurationProvider;
import org.apache.james.utils.KeystoreCreator;

public class ProtocolConfigurationSanitizer implements ConfigurationSanitizer {
    private final ConfigurationProvider configurationProvider;
    private final KeystoreCreator keystoreCreator;
    private final FileSystem fileSystem;
    private final RunArguments runArguments;
    private final String component;

    public ProtocolConfigurationSanitizer(ConfigurationProvider configurationProvider, KeystoreCreator keystoreCreator,
                                          FileSystem fileSystem, RunArguments runArguments, String component) {
        this.configurationProvider = configurationProvider;
        this.keystoreCreator = keystoreCreator;
        this.fileSystem = fileSystem;
        this.runArguments = runArguments;
        this.component = component;
    }

    @Override
    public void sanitize() throws Exception {
        if (runArguments.contain(RunArguments.Argument.GENERATE_KEYSTORE)) {
            HierarchicalConfiguration<ImmutableNode> config = configurationProvider.getConfiguration(component);
            List<HierarchicalConfiguration<ImmutableNode>> configs = config.configurationsAt(component);

            for (HierarchicalConfiguration<ImmutableNode> serverConfig : configs) {
                SslConfig sslConfig = SslConfig.parse(serverConfig);

                if (sslConfig.getKeystore() != null
                    && !exists(sslConfig.getKeystore())) {

                    keystoreCreator.generateKeystore(sslConfig.getKeystore(), sslConfig.getSecret(), sslConfig.getKeystoreType());
                }
            }
        }
    }

    private boolean exists(String file) {
        try {
            return fileSystem.getFile(file).exists();
        } catch (FileNotFoundException e) {
            return false;
        }
    }
}
