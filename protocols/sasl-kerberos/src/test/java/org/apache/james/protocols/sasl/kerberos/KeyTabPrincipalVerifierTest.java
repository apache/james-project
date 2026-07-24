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

package org.apache.james.protocols.sasl.kerberos;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.apache.commons.configuration2.ex.ConfigurationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

class KeyTabPrincipalVerifierTest {
    private static final String SERVER_NAME = "mail.example.test";

    @TempDir
    Path temporaryDirectory;

    @Test
    @ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
    void shouldFindConfiguredPrincipalInKeyTab() throws Exception {
        try (KerberosTestFixture kerberos = new KerberosTestFixture(temporaryDirectory)) {
            KerberosTestFixture.Service service = kerberos.provisionService("imap", SERVER_NAME);
            GssapiSaslConfiguration configuration = new GssapiSaslConfiguration(
                service.serviceName(), service.serverName(), service.principal(), service.keyTab(), true);

            assertThatCode(() -> new KeyTabPrincipalVerifier().verify(configuration))
                .doesNotThrowAnyException();
        }
    }

    @Test
    @ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
    void shouldRejectKeyTabWithoutConfiguredPrincipal() throws Exception {
        try (KerberosTestFixture kerberos = new KerberosTestFixture(temporaryDirectory)) {
            KerberosTestFixture.Service service = kerberos.provisionService("smtp", SERVER_NAME);
            GssapiSaslConfiguration configuration = new GssapiSaslConfiguration(
                "imap", SERVER_NAME, "imap/" + SERVER_NAME + "@" + KerberosTestFixture.REALM, service.keyTab(), true);

            assertThatThrownBy(() -> new KeyTabPrincipalVerifier().verify(configuration))
                .isInstanceOf(ConfigurationException.class)
                .hasMessage("The configured GSSAPI keytab does not contain the configured principal");
        }
    }
}
