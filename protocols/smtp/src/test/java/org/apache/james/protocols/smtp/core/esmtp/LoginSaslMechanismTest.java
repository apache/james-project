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

package org.apache.james.protocols.smtp.core.esmtp;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.junit.jupiter.api.Test;

class LoginSaslMechanismTest {
    private static final Username USERNAME = Username.of("user@example.com");
    private static final SaslIdentity IDENTITY = new SaslIdentity(USERNAME, USERNAME);
    private static final SaslAuthenticator UNUSED_AUTHENTICATOR = new SaslAuthenticator() {
        @Override
        public SaslAuthenticationResult authenticatePassword(Username authenticationId, Optional<Username> authorizationId, String password) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SaslAuthenticationResult authorize(SaslIdentity identity) {
            throw new UnsupportedOperationException();
        }
    };

    private final RecordingPlainMechanism plainMechanism = new RecordingPlainMechanism();
    private final LoginSaslMechanism testee = new LoginSaslMechanism(plainMechanism);

    @Test
    void nameShouldBeLogin() {
        assertThat(testee.name()).isEqualTo(SaslMechanismNames.LOGIN);
    }

    @Test
    void isAvailableOnTransportShouldDelegateToPlainMechanism() {
        plainMechanism.availableOnEncryptedTransportOnly = true;

        assertThat(testee.isAvailableOnTransport(false)).isFalse();
        assertThat(testee.isAvailableOnTransport(true)).isTrue();
    }

    @Test
    void firstStepShouldAskForUsernameWhenInitialResponseIsAbsent() {
        SaslExchange exchange = testee.start(new SaslInitialRequest(SaslMechanismNames.LOGIN, Optional.empty()), UNUSED_AUTHENTICATOR);

        SaslStep step = exchange.firstStep();

        assertThat(((SaslStep.Challenge) step).payload())
            .hasValueSatisfying(payload -> assertThat(payload).containsExactly(bytes("Username:")));
    }

    @Test
    void shouldAskForPasswordAfterUsernameResponse() {
        SaslExchange exchange = testee.start(new SaslInitialRequest(SaslMechanismNames.LOGIN, Optional.empty()), UNUSED_AUTHENTICATOR);
        exchange.firstStep();

        SaslStep step = exchange.onResponse(bytes("user@example.com"));

        assertThat(((SaslStep.Challenge) step).payload())
            .hasValueSatisfying(payload -> assertThat(payload).containsExactly(bytes("Password:")));
    }

    @Test
    void firstStepShouldAskForPasswordWhenInitialResponseContainsUsername() {
        SaslExchange exchange = testee.start(new SaslInitialRequest(SaslMechanismNames.LOGIN, Optional.of(bytes("user@example.com"))), UNUSED_AUTHENTICATOR);

        SaslStep step = exchange.firstStep();

        assertThat(((SaslStep.Challenge) step).payload())
            .hasValueSatisfying(payload -> assertThat(payload).containsExactly(bytes("Password:")));
    }

    @Test
    void shouldDelegateUsernameAndPasswordToPlainMechanism() {
        SaslExchange exchange = testee.start(new SaslInitialRequest(SaslMechanismNames.LOGIN, Optional.empty()), UNUSED_AUTHENTICATOR);
        exchange.firstStep();
        exchange.onResponse(bytes("user@example.com"));

        SaslStep step = exchange.onResponse(bytes("secret"));

        assertThat(((SaslStep.Success) step).identity()).isEqualTo(IDENTITY);
        assertThat(plainMechanism.initialResponse)
            .containsExactly(bytes("\0user@example.com\0secret"));
    }

    @Test
    void closeShouldCloseDelegatedPlainExchange() {
        SaslExchange exchange = testee.start(new SaslInitialRequest(SaslMechanismNames.LOGIN, Optional.of(bytes("user@example.com"))), UNUSED_AUTHENTICATOR);
        exchange.firstStep();
        exchange.onResponse(bytes("secret"));

        exchange.close();

        assertThat(plainMechanism.exchangeClosed).isTrue();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static class RecordingPlainMechanism implements SaslMechanism {
        private boolean availableOnEncryptedTransportOnly;
        private boolean exchangeClosed;
        private byte[] initialResponse;

        @Override
        public String name() {
            return SaslMechanismNames.PLAIN;
        }

        @Override
        public boolean isAvailableOnTransport(boolean channelEncrypted) {
            return !availableOnEncryptedTransportOnly || channelEncrypted;
        }

        @Override
        public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
            initialResponse = request.initialResponse().orElseThrow().clone();
            return new SaslExchange() {
                @Override
                public SaslStep firstStep() {
                    return new SaslStep.Success(IDENTITY, Optional.empty());
                }

                @Override
                public SaslStep onResponse(byte[] clientResponse) {
                    throw new UnsupportedOperationException();
                }

                @Override
                public void close() {
                    exchangeClosed = true;
                }
            };
        }
    }
}
