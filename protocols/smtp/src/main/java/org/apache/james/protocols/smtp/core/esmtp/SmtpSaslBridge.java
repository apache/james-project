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

package org.apache.james.protocols.smtp.core.esmtp;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.apache.james.protocols.api.sasl.SaslExchange;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslProtocol;
import org.apache.james.protocols.api.sasl.SaslStep;
import org.apache.james.protocols.smtp.SMTPResponse;
import org.apache.james.protocols.smtp.SMTPRetCode;

public class SmtpSaslBridge {
    /**
     * Converts an SMTP AUTH request into a protocol-neutral SASL initial request.
     */
    public SaslInitialRequest initialRequest(String mechanismName, Optional<String> initialClientResponse) {
        return new SaslInitialRequest(SaslProtocol.SMTP, mechanismName,
            Objects.requireNonNull(initialClientResponse).map(this::decodeInitialClientResponse));
    }

    /**
     * Encodes a SASL challenge payload as an SMTP AUTH 334 response.
     */
    public SMTPResponse challenge(SaslStep.Challenge challenge) {
        return new SMTPResponse(SMTPRetCode.AUTH_READY,
            challenge.payload()
                .map(Base64.getEncoder()::encodeToString)
                .orElse(""));
    }

    /**
     * Decodes an SMTP client continuation line and forwards it to the SASL exchange.
     */
    public SaslStep onClientResponse(SaslExchange exchange, byte[] line) {
        return exchange.onResponse(decodeBase64(stripTrailingCrlf(line)));
    }

    /**
     * Aborts and closes an active SASL exchange.
     */
    public void abort(SaslExchange exchange) {
        exchange.abort();
        exchange.close();
    }

    /**
     * Closes an active SASL exchange.
     */
    public void close(SaslExchange exchange) {
        exchange.close();
    }

    private byte[] decodeBase64(String value) {
        return Base64.getDecoder().decode(value);
    }

    private byte[] decodeInitialClientResponse(String value) {
        if (value.equals("=")) {
            return new byte[0];
        }
        return decodeBase64(value);
    }

    private String stripTrailingCrlf(byte[] line) {
        String value = new String(line, StandardCharsets.US_ASCII);
        return StringUtils.stripEnd(value, "\r\n");
    }
}
