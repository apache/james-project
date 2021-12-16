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

package org.apache.james.protocols.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;

/**
 * https://datatracker.ietf.org/doc/rfc7628/
 */
public class OIDCSASLParser {
    public static final char SASL_SEPARATOR = 1;
    public static final String PREFIX_TOKEN = "Bearer ";

    public static class OIDCInitialResponse {
        private final String user;
        private final String token;

        public OIDCInitialResponse(String user, String token) {
            this.user = user;
            this.token = token;
        }

        public String getUser() {
            return user;
        }

        public String getToken() {
            return token;
        }
    }

    public static Optional<OIDCInitialResponse> parse(String initialResponse) {
        Optional<String> decodeResult = decodeBase64(initialResponse);

        if (decodeResult.isPresent()) {
            String decodeValueWithoutDanglingPart = decodeResult.filter(value -> value.startsWith("n,"))
                .map(value -> value.substring(2))
                .orElse(decodeResult.get());
            List<String> parts = Splitter.on(SASL_SEPARATOR).splitToList(decodeValueWithoutDanglingPart);
            ImmutableList<String> tokenPart = parts.stream().filter(part -> part.startsWith("auth=")).collect(ImmutableList.toImmutableList());
            ImmutableList<String> userPart = parts.stream().filter(part -> part.startsWith("user=")).collect(ImmutableList.toImmutableList());
            if (tokenPart.size() == 1 && userPart.size() == 1) {
                return Optional.of(new OIDCInitialResponse(userPart.get(0).substring(5), tokenPart.get(0).substring(5).replace(PREFIX_TOKEN, "")));
            }
        }
        return Optional.empty();
    }

    private static Optional<String> decodeBase64(String line) {
        try {
            String lineWithoutTrailingCrLf = StringUtils.replace(line, "\r\n", "");
            return Optional.of(new String(Base64.getDecoder().decode(lineWithoutTrailingCrLf), StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
