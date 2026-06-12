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

package org.apache.james.examples.imap.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslIdentity;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanism;
import org.apache.james.protocols.api.sasl.SaslStep;

public class ExampleTokenSaslMechanism implements SaslMechanism {
    public static final String NAME = "EXAMPLE-TOKEN";
    public static final String CONTINUATION_PROMPT = "Go ahead";
    public static final String SUCCESS_DATA_TOKEN_SUFFIX = ":server-data";
    public static final String SUCCESS_DATA = "Token accepted";

    private final ExampleTokenSaslConfiguration configuration;

    public ExampleTokenSaslMechanism(ExampleTokenSaslConfiguration configuration) {
        this.configuration = configuration;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public SaslExchange start(SaslInitialRequest request) {
        Optional<byte[]> initialResponse = request.initialResponse();
        return new ExampleTokenSaslExchange(initialResponse, configuration);
    }

    private static class ExampleTokenSaslExchange implements SaslExchange {
        private final Optional<byte[]> initialResponse;
        private final ExampleTokenSaslConfiguration configuration;

        private ExampleTokenSaslExchange(Optional<byte[]> initialResponse, ExampleTokenSaslConfiguration configuration) {
            this.initialResponse = initialResponse;
            this.configuration = configuration;
        }

        @Override
        public SaslStep firstStep() {
            return initialResponse
                .map(this::authenticate)
                .orElseGet(() -> new SaslStep.Challenge(Optional.of(CONTINUATION_PROMPT
                    .getBytes(StandardCharsets.UTF_8))));
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
            String token = new String(clientResponse, StandardCharsets.UTF_8);
            if (configuration.expectedToken().equals(token)) {
                return success(Optional.empty());
            }
            // allow client to request server to return data on success message, which may be used by Kerberos auth
            if ((configuration.expectedToken() + SUCCESS_DATA_TOKEN_SUFFIX).equals(token)) {
                return success(Optional.of(SUCCESS_DATA.getBytes(StandardCharsets.UTF_8)));
            }
            return new SaslStep.Failure("EXAMPLE-TOKEN authentication failed.");
        }

        private SaslStep success(Optional<byte[]> serverData) {
            return new SaslStep.Success(new SaslIdentity(configuration.authorizedUser(), configuration.authorizedUser()), serverData);
        }
    }
}
