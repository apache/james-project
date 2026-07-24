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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import java.nio.file.Path;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;

class GssapiJdkInteropTest {
    private static final String SERVER_NAME = "mail.example.test";

    @TempDir
    Path temporaryDirectory;

    @Test
    @ResourceLock(KerberosTestFixture.KRB5_CONFIGURATION_RESOURCE)
    void shouldInteroperateWithJdkGssapiClient() throws Exception {
        try (KerberosTestFixture kerberos = new KerberosTestFixture(temporaryDirectory)) {
            KerberosTestFixture.Service service = kerberos.provisionService("imap", SERVER_NAME);
            GssapiSaslConfiguration configuration = new GssapiSaslConfiguration(
                service.serviceName(), service.serverName(), service.principal(), service.keyTab(), true);
            SaslMechanism mechanism = new GssapiSaslMechanism(
                configuration, new KerberosLoginContextFactory(), new JdkSaslServerFactory());

            try (GssapiTestClient client = kerberos.client(service);
                    SaslExchange exchange = mechanism.start(
                        new SaslInitialRequest("GSSAPI", Optional.of(client.initialResponse())),
                        allowingSelfAuthorization())) {
                SaslStep result = completeExchange(exchange, client);

                assertThat(result).isInstanceOfSatisfying(SaslStep.Success.class, success -> {
                    assertThat(success.identity().authenticationId()).isEqualTo(Username.of(KerberosTestFixture.USER_PRINCIPAL));
                    assertThat(success.identity().authorizationId()).isEqualTo(Username.of(KerberosTestFixture.USER_PRINCIPAL));
                    assertThat(success.serverData()).isEmpty();
                });
                assertThat(client.isComplete()).isTrue();
            }
        }
    }

    private SaslStep completeExchange(SaslExchange exchange, GssapiTestClient client) throws Exception {
        SaslStep step = exchange.firstStep();
        for (int round = 0; round < 10 && step instanceof SaslStep.Challenge challenge; round++) {
            step = exchange.onResponse(client.evaluate(challenge.payload().orElseGet(() -> new byte[0])));
        }
        if (step instanceof SaslStep.Challenge) {
            fail("GSSAPI exchange did not complete within 10 rounds");
        }
        return step;
    }

    private SaslAuthenticator allowingSelfAuthorization() {
        return new SaslAuthenticator() {
            @Override
            public SaslAuthenticationResult authenticatePassword(Username authenticationId,
                                                                 Optional<Username> authorizationId,
                                                                 String password) {
                throw new UnsupportedOperationException("Password authentication is not used by GSSAPI");
            }

            @Override
            public SaslAuthenticationResult authorize(SaslIdentity identity) {
                return new SaslAuthenticationResult.Success(identity);
            }
        };
    }
}
