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

package org.apache.james.protocols.api.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;

import org.apache.james.core.Username;

import com.google.common.collect.ImmutableList;

public class PlainSaslMechanism implements SaslMechanism {
    public static final String NAME = "PLAIN";

    protected record PlainCredentials(Optional<Username> authorizationId, Username authenticationId, String password) {
    }

    protected static PlainCredentials credentials(Optional<Username> authorizationId, Username authenticationId, String password) {
        return new PlainCredentials(authorizationId, authenticationId, password);
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request) {
        return new PlainSaslExchange(request.initialResponse(), this::parse);
    }

    private static class PlainSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final Function<byte[], Optional<PlainCredentials>> credentialsParser;

        private PlainSaslExchange(Optional<byte[]> initialResponse, Function<byte[], Optional<PlainCredentials>> credentialsParser) {
            this.initialResponse = initialResponse;
            this.credentialsParser = credentialsParser;
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
        public void abort() {
        }

        @Override
        public void close() {
        }

        private SaslStep authenticate(byte[] clientResponse) {
            return credentialsParser.apply(clientResponse)
                .map(credentials -> (SaslStep) new SaslStep.Credentials(new SaslCredentials.Password(
                    credentials.authenticationId(), credentials.authorizationId(), credentials.password())))
                .orElseGet(() -> new SaslStep.Failure("Malformed authentication command."));
        }
    }

    protected Optional<PlainCredentials> parse(byte[] clientResponse) {
        ImmutableList<String> tokens = Arrays.stream(new String(clientResponse, StandardCharsets.UTF_8).split("\0", -1))
            .collect(ImmutableList.toImmutableList());

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
