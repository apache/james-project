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

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;

/**
 * SMTP-only AUTH LOGIN framing backed by the configured PLAIN mechanism.
 */
public class LoginSaslMechanism implements SaslMechanism {
    private static final byte[] USERNAME_PROMPT = "Username:".getBytes(StandardCharsets.US_ASCII);
    private static final byte[] PASSWORD_PROMPT = "Password:".getBytes(StandardCharsets.US_ASCII);

    private final SaslMechanism plainMechanism;

    LoginSaslMechanism(SaslMechanism plainMechanism) {
        this.plainMechanism = plainMechanism;
    }

    static Optional<SaslExchange> delegatedExchange(SaslExchange exchange) {
        if (exchange instanceof LoginSaslExchange loginSaslExchange) {
            return loginSaslExchange.plainExchange;
        }
        return Optional.empty();
    }

    SaslMechanism plainMechanism() {
        return plainMechanism;
    }

    @Override
    public String name() {
        return SaslMechanismNames.LOGIN;
    }

    @Override
    public boolean isAvailableOnTransport(boolean channelEncrypted) {
        return plainMechanism.isAvailableOnTransport(channelEncrypted);
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new LoginSaslExchange(request.initialResponse(), authenticator);
    }

    private class LoginSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final SaslAuthenticator authenticator;
        private Optional<String> username;
        private Optional<SaslExchange> plainExchange;

        private LoginSaslExchange(Optional<byte[]> initialResponse, SaslAuthenticator authenticator) {
            this.initialResponse = initialResponse;
            this.authenticator = authenticator;
            this.username = Optional.empty();
            this.plainExchange = Optional.empty();
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::recordUsernameThenChallengePassword)
                .orElseGet(() -> new SaslStep.Challenge(Optional.of(USERNAME_PROMPT)));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return username
                .map(username -> authenticate(username, clientResponse))
                .orElseGet(() -> recordUsernameThenChallengePassword(clientResponse));
        }

        @Override
        public void close() {
            plainExchange.ifPresent(SaslExchange::close);
        }

        private SaslStep recordUsernameThenChallengePassword(byte[] clientResponse) {
            username = Optional.of(new String(clientResponse, StandardCharsets.UTF_8));
            return new SaslStep.Challenge(Optional.of(PASSWORD_PROMPT));
        }

        private SaslStep authenticate(String username, byte[] password) {
            SaslInitialRequest plainRequest = new SaslInitialRequest(SaslMechanismNames.PLAIN,
                Optional.of(plainInitialResponse(username, password)));
            SaslExchange exchange = startPlainExchange(plainRequest);
            plainExchange = Optional.of(exchange);
            return exchange.firstStep();
        }

        private SaslExchange startPlainExchange(SaslInitialRequest plainRequest) {
            return plainMechanism.start(plainRequest, authenticator);
        }

        private byte[] plainInitialResponse(String username, byte[] password) {
            byte[] usernameBytes = username.getBytes(StandardCharsets.UTF_8);
            byte[] response = new byte[usernameBytes.length + password.length + 2];
            System.arraycopy(usernameBytes, 0, response, 1, usernameBytes.length);
            System.arraycopy(password, 0, response, usernameBytes.length + 2, password.length);
            return response;
        }
    }
}
