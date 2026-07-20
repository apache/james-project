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
import java.util.Base64;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;

/**
 * Wire encoding shared by the line based protocols carrying SASL exchanges (IMAP, SMTP, POP3).
 *
 * Those protocols agree on the encoding rules defined by RFC 4422: payloads travel base64
 * encoded on a single line, "*" cancels the exchange, and an empty line acknowledges final
 * server data. Only the framing of the encoded payload is protocol specific and thus stays
 * in the respective command handlers.
 */
public class SaslCodec {
    private static final String ABORT = "*";
    private static final String EMPTY_INITIAL_CLIENT_RESPONSE = "=";

    /**
     * Converts a protocol authentication command into a protocol-neutral SASL initial request.
     */
    public static SaslInitialRequest initialRequest(String mechanismName, Optional<String> initialClientResponse) {
        return new SaslInitialRequest(mechanismName, initialClientResponse.map(SaslCodec::decodeInitialClientResponse));
    }

    /**
     * Decodes a client continuation line into the bytes to feed to the SASL exchange.
     */
    public static byte[] decodeClientResponse(byte[] line) {
        return decodeBase64(stripTrailingCrlf(line));
    }

    /**
     * Encodes a SASL payload (challenge or final server data) for the wire.
     */
    public static String encode(Optional<byte[]> payload) {
        return payload
            .map(Base64.getEncoder()::encodeToString)
            .orElse("");
    }

    /**
     * Detects SASL client cancellation.
     */
    public static boolean isAbort(byte[] line) {
        return ABORT.equals(stripTrailingCrlf(line));
    }

    /**
     * Detects the empty client response used to acknowledge final SASL server data.
     */
    public static boolean isEmptyClientResponse(byte[] line) {
        return stripTrailingCrlf(line).isEmpty();
    }

    private static byte[] decodeBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private static byte[] decodeInitialClientResponse(String value) {
        if (value.equals(EMPTY_INITIAL_CLIENT_RESPONSE)) {
            return new byte[0];
        }
        return decodeBase64(value);
    }

    private static String stripTrailingCrlf(byte[] line) {
        return StringUtils.stripEnd(new String(line, StandardCharsets.US_ASCII), "\r\n");
    }
}
