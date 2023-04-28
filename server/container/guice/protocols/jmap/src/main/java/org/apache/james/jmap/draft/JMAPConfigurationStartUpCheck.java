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
import org.apache.james.RunArguments.Argument;
import org.apache.james.jmap.draft.crypto.SecurityKeyLoader;
import org.apache.james.lifecycle.api.StartUpCheck;
import org.apache.james.utils.KeystoreCreator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JMAPConfigurationStartUpCheck implements StartUpCheck {

    private static final Logger LOGGER = LoggerFactory.getLogger(JMAPConfigurationStartUpCheck.class);
    public static final String CHECK_NAME = "JMAPConfigurationStartUpCheck";

    private final SecurityKeyLoader securityKeyLoader;
    private final JMAPDraftConfiguration jmapConfiguration;
    private final RunArguments runArguments;
    private final KeystoreCreator keystoreCreator;

    @Inject
    JMAPConfigurationStartUpCheck(SecurityKeyLoader securityKeyLoader,
                                  JMAPDraftConfiguration jmapConfiguration,
                                  RunArguments runArguments,
                                  KeystoreCreator keystoreCreator) {
        this.securityKeyLoader = securityKeyLoader;
        this.jmapConfiguration = jmapConfiguration;
        this.runArguments = runArguments;
        this.keystoreCreator = keystoreCreator;
    }

    @Override
    public CheckResult check() {
        if (jmapConfiguration.isEnabled()) {
            return checkSecurityKey();
        }

        return CheckResult.builder()
            .checkName(checkName())
            .resultType(ResultType.GOOD)
            .build();
    }

    private CheckResult checkSecurityKey() {
        try {
            loadSecurityKey();
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.GOOD)
                .build();
        } catch (Exception e) {
            LOGGER.error("Cannot load security key from jmap configuration", e);
            return CheckResult.builder()
                .checkName(checkName())
                .resultType(ResultType.BAD)
                .description(e.getMessage())
                .build();
        }
    }

    private void loadSecurityKey() throws Exception {
        try {
            securityKeyLoader.load();
        } catch (FileNotFoundException e) {
            if (runArguments.contain(Argument.GENERATE_KEYSTORE)) {
                LOGGER.warn("Can not load asymmetric key from configuration file", e);
                LOGGER.warn("James will auto-generate an asymmetric key.");

                keystoreCreator.generateKeystore(
                    jmapConfiguration.getKeystore()
                        .orElseThrow(() -> new IllegalArgumentException("Can not auto-generate keystore as the keystore location is missing from the configuration")),
                    jmapConfiguration.getSecret()
                        .orElseThrow(() -> new IllegalArgumentException("Can not auto-generate keystore as the keystore secret is missing from the configuration")),
                    jmapConfiguration.getKeystoreType());

                securityKeyLoader.load();
            } else {
                throw e;
            }
        }
    }

    @Override
    public String checkName() {
        return CHECK_NAME;
    }

}
