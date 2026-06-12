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
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.OIDCSASLParser;

public final class OidcSaslMechanisms {
    static SaslExchange start(Optional<byte[]> initialResponse) {
        return new OidcSaslExchange(initialResponse);
    }

    private OidcSaslMechanisms() {
    }

    private static class OidcSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;

        private OidcSaslExchange(Optional<byte[]> initialResponse) {
            this.initialResponse = initialResponse;
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
            return OIDCSASLParser.parseDecoded(new String(clientResponse, StandardCharsets.US_ASCII))
                .map(response -> (SaslStep) new SaslStep.Credentials(new SaslCredentials.BearerToken(
                    response.getToken(), Username.of(response.getAssociatedUser()))))
                .orElseGet(() -> new SaslStep.Failure("Malformed authentication command."));
        }
    }
}
