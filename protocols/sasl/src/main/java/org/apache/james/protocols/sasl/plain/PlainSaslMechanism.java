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

package org.apache.james.protocols.sasl.plain;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.sasl.SaslAuthenticationResult;
import org.apache.james.protocols.api.sasl.SaslAuthenticator;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslFailure;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;

import com.google.common.collect.ImmutableList;

public class PlainSaslMechanism implements SaslMechanism {
    public static final String NAME = SaslMechanismNames.PLAIN;
    public static final boolean ENABLED = true;
    public static final boolean REQUIRE_SSL = true;

    protected record PlainCredentials(Optional<Username> authorizationId, Username authenticationId, String password) {
    }

    protected static PlainCredentials credentials(Optional<Username> authorizationId, Username authenticationId, String password) {
        return new PlainCredentials(authorizationId, authenticationId, password);
    }

    private final boolean enabled;
    private final boolean requiresSsl;

    public PlainSaslMechanism() {
        this(true, false);
    }

    public PlainSaslMechanism(boolean enabled, boolean requiresSsl) {
        this.enabled = enabled;
        this.requiresSsl = requiresSsl;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public boolean isAvailableOnTransport(boolean channelEncrypted) {
        return enabled && (!requiresSsl || channelEncrypted);
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslAuthenticator authenticator) {
        return new PlainSaslExchange(request.initialResponse(), this::parse, authenticator);
    }

    /**
     * Verifies cleartext credentials directly for protocols whose command already exposes username/password,
     * for example IMAP LOGIN.
     */
    public SaslStep authenticate(Username authenticationId, String password, SaslAuthenticator authenticator) {
        return verify(credentials(Optional.empty(), authenticationId, password), authenticator);
    }

    private static class PlainSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final Function<byte[], Optional<PlainCredentials>> credentialsParser;
        private final SaslAuthenticator authenticator;

        private PlainSaslExchange(Optional<byte[]> initialResponse,
                                  Function<byte[], Optional<PlainCredentials>> credentialsParser,
                                  SaslAuthenticator authenticator) {
            this.initialResponse = initialResponse;
            this.credentialsParser = credentialsParser;
            this.authenticator = authenticator;
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.empty()));
        }

        @Override
        public SaslStep onResponse(byte[] clientResponse) {
            return authenticate(clientResponse);
        }

        @Override
        public void close() {
        }

        private SaslStep authenticate(byte[] clientResponse) {
            return credentialsParser.apply(clientResponse)
                .map(credentials -> verify(credentials, authenticator))
                .orElseGet(() -> new SaslStep.Failure(SaslFailure.malformed("Malformed authentication command.")));
        }
    }

    protected static SaslStep verify(PlainCredentials credentials, SaslAuthenticator authenticator) {
        SaslAuthenticationResult result = authenticator.authenticatePassword(
            credentials.authenticationId(), credentials.authorizationId(), credentials.password());
        return switch (result) {
            case SaslAuthenticationResult.Success success -> new SaslStep.Success(success.identity(), Optional.empty());
            case SaslAuthenticationResult.Failure failure -> new SaslStep.Failure(failure.failure());
        };
    }

    protected Optional<PlainCredentials> parse(byte[] clientResponse) {
        ImmutableList<String> tokens = Arrays.stream(new String(clientResponse, StandardCharsets.UTF_8).split("\0", -1))
            .collect(ImmutableList.toImmutableList());

        // Preserve legacy SMTP compatibility: some clients send a trailing NUL after the password.
        if (tokens.size() == 4 && tokens.get(3).isEmpty()) {
            return credentials(tokens.subList(0, 3));
        }
        return credentials(tokens);
    }

    private Optional<PlainCredentials> credentials(List<String> tokens) {
        if (tokens.size() == 2) {
            return Optional.of(credentials(Optional.empty(), Username.of(tokens.get(0)), tokens.get(1)));
        }
        if (tokens.size() == 3) {
            Optional<Username> authorizationId = Optional.of(tokens.get(0))
                .filter(value -> !value.isEmpty())
                .map(Username::of);
            return Optional.of(credentials(authorizationId, Username.of(tokens.get(1)), tokens.get(2)));
        }
        return Optional.empty();
    }
}
