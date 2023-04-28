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

package org.apache.james.jmap.draft;

import java.io.FileNotFoundException;

import javax.inject.Inject;

import org.apache.james.RunArguments;
import org.apache.james.filesystem.api.FileSystem;
import org.apache.james.lifecycle.api.ConfigurationSanitizer;
import org.apache.james.utils.KeystoreCreator;

public class JmapConfigurationSanitizer implements ConfigurationSanitizer {
    private final JMAPDraftConfiguration jmapDraftConfiguration;
    private final KeystoreCreator keystoreCreator;
    private final FileSystem fileSystem;
    private final RunArguments runArguments;

    @Inject
    public JmapConfigurationSanitizer(JMAPDraftConfiguration jmapDraftConfiguration, KeystoreCreator keystoreCreator, FileSystem fileSystem, RunArguments runArguments) {
        this.jmapDraftConfiguration = jmapDraftConfiguration;
        this.keystoreCreator = keystoreCreator;
        this.fileSystem = fileSystem;
        this.runArguments = runArguments;
    }

    @Override
    public void sanitize() throws Exception {
        if (jmapDraftConfiguration.isEnabled()
            && jmapDraftConfiguration.getKeystore().isPresent()
            && !keystoreExists()
            && runArguments.contain(RunArguments.Argument.GENERATE_KEYSTORE)) {

            keystoreCreator.generateKeystore(jmapDraftConfiguration.getKeystore().get(),
                jmapDraftConfiguration.getSecret()
                    .orElseThrow(() -> new IllegalArgumentException("Can not auto-generate keystore as the keystore secret is missing from the configuration")),
                jmapDraftConfiguration.getKeystoreType());
        }
    }

    private boolean keystoreExists() {
        try {
            return fileSystem.getFile(jmapDraftConfiguration.getKeystore().get()).exists();
        } catch (FileNotFoundException e) {
            return false;
        }
    }
}
