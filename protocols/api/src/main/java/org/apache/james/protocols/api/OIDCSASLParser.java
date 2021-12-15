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
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;

public class OIDCSASLParser {
    public static final char SASL_SEPARATOR = 1;

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
        String decodedPayload = decodeBase64(initialResponse);
        if (!decodedPayload.startsWith("n,")) {
            return Optional.empty();
        }
        List<String> parts = Splitter.on(SASL_SEPARATOR).splitToList(decodedPayload);
        if (parts.size() == 4 && parts.get(2).isEmpty() && parts.get(3).isEmpty()) {
            Map<String, String> part1 = parseSASLPart(parts.get(0));
            Map<String, String> part2 = parseSASLPart(parts.get(1));

            String authToken = part2.get("auth");
            String user = part1.get("user");

            return Optional.of(new OIDCInitialResponse(user, authToken));
        } else {
            return Optional.empty();
        }
    }

    private static String decodeBase64(String line) {
        String lineWithoutTrailingCrLf = StringUtils.replace(line, "\r\n", "");
        return new String(Base64.getDecoder().decode(lineWithoutTrailingCrLf), StandardCharsets.UTF_8);
    }

    private static Map<String, String> parseSASLPart(String part) {
        return Splitter.on(',')
            .splitToList(part)
            .stream()
            .filter(s -> s.contains("="))
            .flatMap(s -> {
                int i = s.indexOf('=');
                if (i == 0 || i == s.length() - 1) {
                    return Stream.empty();
                }
                return Stream.of(Pair.of(s.substring(0, i), s.substring(i + 1)));
            })
            .collect(ImmutableMap.toImmutableMap(Pair::getLeft, Pair::getRight));
    }

}
