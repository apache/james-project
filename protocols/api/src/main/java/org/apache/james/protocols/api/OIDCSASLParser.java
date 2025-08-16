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
import java.util.Optional;
import java.util.StringTokenizer;

import org.apache.commons.lang3.StringUtils;

/**
 * https://datatracker.ietf.org/doc/rfc7628/
 */
public class OIDCSASLParser {
    public static final char SASL_SEPARATOR = 1;
    public static final String PREFIX_TOKEN = "Bearer ";
    public static final String TOKEN_PART_PREFIX = "auth=";
    public static final String XOAUTH2_USER_PART_PREFIX = "user=";
    public static final String OAUTHBEARER_USER_PART_PREFIX = "a=";
    public static final int TOKEN_PART_INDEX = TOKEN_PART_PREFIX.length();
    public static final int XOAUTH2_USER_PART_INDEX = XOAUTH2_USER_PART_PREFIX.length();
    public static final int OAUTHBEARER_USER_PART_INDEX = OAUTHBEARER_USER_PART_PREFIX.length();

    public static class OIDCInitialResponse {
        private final String associatedUser;
        private final String token;

        public OIDCInitialResponse(String user, String token) {
            this.associatedUser = user;
            this.token = token;
        }

        public String getToken() {
            return token;
        }

        public String getAssociatedUser() {
            return associatedUser;
        }
    }

    public static Optional<OIDCInitialResponse> parse(String initialResponse) {
        Optional<String> decodeResult = decodeBase64(initialResponse);

        if (decodeResult.isPresent()) {
            String decodeValueWithoutDanglingPart = decodeResult.filter(value -> value.startsWith("n,"))
                .map(value -> value.substring(2))
                .orElse(decodeResult.get());

            StringTokenizer stringTokenizer = new StringTokenizer(decodeValueWithoutDanglingPart, String.valueOf(SASL_SEPARATOR));
            String tokenPart = null;
            String userPart = null;
            int tokenPartCounter = 0;
            int userPartCounter = 0;

            while (stringTokenizer.hasMoreTokens()) {
                String stringToken = stringTokenizer.nextToken();
                if (stringToken.startsWith(TOKEN_PART_PREFIX)) {
                    tokenPart = StringUtils.replace(stringToken.substring(TOKEN_PART_INDEX), PREFIX_TOKEN, "");
                    tokenPartCounter++;
                } else if (stringToken.startsWith(XOAUTH2_USER_PART_PREFIX)) {
                    userPart = stringToken.substring(XOAUTH2_USER_PART_INDEX);
                    userPartCounter++;
                } else if (stringToken.startsWith(OAUTHBEARER_USER_PART_PREFIX)) {
                    userPart = stringToken.substring(OAUTHBEARER_USER_PART_INDEX);
                    userPartCounter++;
                }
            }

            if (tokenPart != null && userPart != null && tokenPartCounter == 1 && userPartCounter == 1) {
                return Optional.of(new OIDCInitialResponse(userPart, tokenPart));
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
