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
import java.util.Set;

import org.apache.james.core.Username;
import org.apache.james.protocols.api.OIDCSASLParser;

abstract class AbstractOidcSaslMechanism implements SaslMechanism {
    @Override
    public boolean supports(SaslProtocol protocol) {
        return protocol == SaslProtocol.IMAP || protocol == SaslProtocol.SMTP;
    }

    @Override
    public Set<Class<?>> requiredServices(SaslProtocol protocol) {
        if (supports(protocol)) {
            return Set.of(BearerTokenSaslAuthenticationService.class);
        }
        return Set.of();
    }

    @Override
    public boolean isAvailable(SaslSessionContext context) {
        return context.service(BearerTokenSaslAuthenticationService.class).isPresent();
    }

    @Override
    public SaslExchange start(SaslInitialRequest request, SaslSessionContext context) {
        return new OidcSaslExchange(request.initialResponse(), context);
    }

    private static class OidcSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final SaslSessionContext context;

        private OidcSaslExchange(Optional<byte[]> initialResponse, SaslSessionContext context) {
            this.initialResponse = initialResponse;
            this.context = context;
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
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Failure("Malformed authentication command."));
        }

        private SaslStep authenticate(OIDCSASLParser.OIDCInitialResponse initialResponse) {
            return context.service(BearerTokenSaslAuthenticationService.class)
                .map(service -> service.authenticate(initialResponse.getToken(), Username.of(initialResponse.getAssociatedUser())))
                .map(this::toStep)
                .orElseGet(() -> new SaslStep.Failure("OIDC authentication is not available."));
        }

        private SaslStep toStep(SaslAuthenticationResult result) {
            if (result instanceof SaslAuthenticationResult.Success success) {
                return new SaslStep.Success(success.identity(), Optional.empty());
            }
            return new SaslStep.Failure(((SaslAuthenticationResult.Failure) result).reason());
        }
    }
}
