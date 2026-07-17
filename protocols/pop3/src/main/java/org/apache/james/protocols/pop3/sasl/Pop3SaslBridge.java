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

package org.apache.james.protocols.pop3.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;
import java.util.regex.Pattern;

import org.apache.james.protocols.api.Response;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.pop3.POP3Response;

public class Pop3SaslBridge {
    private static final Pattern CANONICAL_BASE64 = Pattern.compile("(?:[A-Za-z0-9+/]{4})*(?:[A-Za-z0-9+/]{2}==|[A-Za-z0-9+/]{3}=)?");

    /** Converts a POP3 AUTH command into a protocol-neutral SASL initial request. */
    public SaslInitialRequest initialRequest(String mechanismName, Optional<String> initialClientResponse) {
        return new SaslInitialRequest(mechanismName, initialClientResponse.map(this::decodeInitialClientResponse));
    }

    /** Encodes a SASL challenge as a POP3 continuation response. */
    public Response challenge(SaslStep.Challenge challenge) {
        return continuation(challenge.payload());
    }

    /** Encodes final SASL server data as a POP3 continuation response. */
    public Response successData(SaslStep.Success success) {
        return continuation(success.serverData());
    }

    /** Decodes a POP3 client continuation and forwards it to the SASL exchange. */
    public SaslStep onClientResponse(SaslExchange exchange, byte[] line) {
        return exchange.onResponse(decodeBase64(stripTrailingCrlf(line)));
    }

    /** Detects the RFC 5034 client cancellation marker. */
    public boolean isAbort(byte[] line) {
        return "*".equals(stripTrailingCrlf(line));
    }

    /** Detects the empty acknowledgement required after final SASL server data. */
    public boolean isEmptyClientResponse(byte[] line) {
        return stripTrailingCrlf(line).isEmpty();
    }

    /** Aborts and releases an active SASL exchange. */
    public void abort(SaslExchange exchange) {
        exchange.abort();
    }

    /** Releases an active SASL exchange. */
    public void close(SaslExchange exchange) {
        exchange.close();
    }

    private Response continuation(Optional<byte[]> payload) {
        String encodedPayload = payload
            .map(Base64.getEncoder()::encodeToString)
            .orElse("");
        return new POP3Response("+", encodedPayload).immutable();
    }

    private byte[] decodeInitialClientResponse(String value) {
        if (value.equals("=")) {
            return new byte[0];
        }
        return decodeBase64(value);
    }

    private byte[] decodeBase64(String value) {
        if (!CANONICAL_BASE64.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid Base64 value");
        }
        return Base64.getDecoder().decode(value);
    }

    private String stripTrailingCrlf(byte[] line) {
        String value = new String(line, StandardCharsets.US_ASCII);
        if (value.endsWith("\r\n")) {
            return value.substring(0, value.length() - 2);
        }
        if (value.endsWith("\n")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }
}
