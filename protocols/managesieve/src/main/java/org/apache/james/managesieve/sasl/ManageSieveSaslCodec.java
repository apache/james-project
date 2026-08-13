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

package org.apache.james.managesieve.sasl;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.apache.james.managesieve.api.SyntaxException;
import org.apache.james.managesieve.transcode.NotEnoughDataException;
import org.apache.james.protocols.api.sasl.SaslCodec;
import org.apache.james.protocols.api.sasl.SaslInitialRequest;
import org.apache.james.protocols.api.sasl.SaslMechanismNames;
import org.apache.james.protocols.api.sasl.SaslStep;

/**
 * Translates between ManageSieve AUTHENTICATE strings and protocol-neutral SASL exchanges.
 *
 * RFC 5804 section 2.1 frames SASL mechanism names, initial responses and continuations as
 * ManageSieve quoted or literal strings. This differs from the line framing used by IMAP,
 * SMTP and POP3, so this codec owns the ManageSieve string grammar and response syntax while
 * delegating protocol-neutral Base64 and cancellation handling to {@link SaslCodec}.
 */
public class ManageSieveSaslCodec {
    public record InitialRequest(String mechanismName, SaslInitialRequest saslInitialRequest) {
    }

    private record ParsedString(String value, String remaining) {
    }

    private static final int QUOTED_STRING_MAX_LENGTH = 1024;

    public static InitialRequest parseInitialRequest(String arguments) throws SyntaxException {
        ParsedString mechanism = parseString(arguments, "quoted SASL mechanism must be supplied");
        String remaining = mechanism.remaining().stripLeading();
        Optional<byte[]> initialResponse = Optional.empty();
        if (!remaining.isEmpty()) {
            ParsedString response = parseString(remaining, "authentication data must be supplied");
            if (!response.remaining().isBlank()) {
                throw new SyntaxException("too many authentication arguments");
            }
            initialResponse = Optional.of(decodeClientData(mechanism.value(), response.value()));
        }
        return new InitialRequest(mechanism.value(), new SaslInitialRequest(mechanism.value(), initialResponse));
    }

    public static byte[] parseClientResponse(String mechanismName, String suppliedData) throws SyntaxException {
        if (suppliedData.isEmpty()) {
            throw new SyntaxException("authentication data must be supplied");
        }
        String serializedData = isString(suppliedData)
            ? parseContinuationString(suppliedData)
            : suppliedData;
        return decodeClientData(mechanismName, serializedData);
    }

    public static boolean isAbort(String suppliedData) {
        String clientData = suppliedData;
        try {
            if (isString(suppliedData)) {
                clientData = parseContinuationString(suppliedData);
            }
            return SaslCodec.isAbort(clientData.getBytes(StandardCharsets.US_ASCII));
        } catch (SyntaxException e) {
            return false;
        }
    }

    public static String challenge(SaslStep.Challenge challenge) {
        // RFC 5804 section 2.1 encodes SASL challenges as ManageSieve strings, including an empty challenge as "".
        return serializeBase64(challenge.payload());
    }

    public static String success(SaslStep.Success success) {
        return success.serverData()
            .map(serverData -> "OK (SASL " + serializeBase64(Optional.of(serverData)) + ")")
            .orElse("OK");
    }

    private static String parseContinuationString(String suppliedData) throws SyntaxException {
        ParsedString parsedString = parseString(suppliedData, "authentication data must be supplied");
        if (!parsedString.remaining().isBlank()) {
            throw new SyntaxException("too many authentication arguments");
        }
        return parsedString.value();
    }

    private static byte[] decodeClientData(String mechanismName, String suppliedData) throws SyntaxException {
        try {
            return SaslCodec.decodeClientResponse(suppliedData.getBytes(StandardCharsets.US_ASCII));
        } catch (IllegalArgumentException e) {
            if (mechanismName.equalsIgnoreCase(SaslMechanismNames.PLAIN)) {
                return legacyPlainData(suppliedData);
            }
            throw new SyntaxException("authentication data must be Base64 encoded");
        }
    }

    private static byte[] legacyPlainData(String suppliedData) throws SyntaxException {
        String separator = suppliedData.indexOf('\0') >= 0 ? "\0" : "\\s+";
        List<String> tokens = Arrays.stream(suppliedData.split(separator, -1))
            .filter(token -> !token.isEmpty())
            .limit(2)
            .toList();
        if (tokens.size() < 2) {
            throw new SyntaxException("authentication data is malformed");
        }
        return (tokens.get(0) + '\0' + tokens.get(1)).getBytes(StandardCharsets.UTF_8);
    }

    private static String serializeBase64(Optional<byte[]> payload) {
        String encoded = SaslCodec.encode(payload);
        if (encoded.length() <= QUOTED_STRING_MAX_LENGTH) {
            return '"' + encoded + '"';
        }
        return "{" + encoded.length() + "}\r\n" + encoded;
    }

    private static ParsedString parseString(String input, String missingMessage) throws SyntaxException {
        if (input == null || input.isEmpty()) {
            throw new SyntaxException(missingMessage);
        }
        if (input.charAt(0) == '"' || input.charAt(0) == '\'') {
            return parseQuotedString(input);
        }
        if (input.charAt(0) == '{') {
            return parseLiteral(input);
        }
        throw new SyntaxException(missingMessage);
    }

    private static ParsedString parseQuotedString(String input) throws SyntaxException {
        char quote = input.charAt(0);
        StringBuilder value = new StringBuilder();
        boolean escaped = false;
        for (int index = 1; index < input.length(); index++) {
            char current = input.charAt(index);
            if (escaped) {
                value.append(current);
                escaped = false;
            } else if (current == '\\') {
                escaped = true;
            } else if (current == quote) {
                return new ParsedString(value.toString(), input.substring(index + 1));
            } else {
                value.append(current);
            }
        }
        throw new SyntaxException("unterminated quoted authentication argument");
    }

    private static ParsedString parseLiteral(String input) throws SyntaxException {
        int markerEnd = input.indexOf("}\r\n");
        if (markerEnd < 0) {
            throw new NotEnoughDataException();
        }
        String sizeValue = input.substring(1, markerEnd);
        // RFC 5804 section 4 defines client-to-server literals as {number+}; {number} is server-to-client syntax.
        if (!sizeValue.endsWith("+")) {
            throw new SyntaxException("invalid client authentication literal syntax");
        }
        sizeValue = sizeValue.substring(0, sizeValue.length() - 1);
        if (sizeValue.isEmpty() || sizeValue.chars().anyMatch(character -> character < '0' || character > '9')) {
            throw new SyntaxException("invalid authentication literal size");
        }
        int size;
        try {
            size = Integer.parseInt(sizeValue);
        } catch (NumberFormatException e) {
            throw new SyntaxException("invalid authentication literal size");
        }
        int contentStart = markerEnd + 3;
        // RFC 5804 section 4 defines the literal size as an octet count, not a Java character count.
        int contentEnd = utf8ContentEnd(input, contentStart, size);
        return new ParsedString(input.substring(contentStart, contentEnd), input.substring(contentEnd));
    }

    private static int utf8ContentEnd(String input, int contentStart, int expectedOctets) throws SyntaxException {
        int contentEnd = contentStart;
        int consumedOctets = 0;
        while (contentEnd < input.length() && consumedOctets < expectedOctets) {
            int codePoint = input.codePointAt(contentEnd);
            int codePointOctets = utf8Length(codePoint);
            if (consumedOctets + codePointOctets > expectedOctets) {
                throw new SyntaxException("authentication literal size splits a UTF-8 character");
            }
            consumedOctets += codePointOctets;
            contentEnd += Character.charCount(codePoint);
        }
        if (consumedOctets < expectedOctets) {
            throw new NotEnoughDataException();
        }
        return contentEnd;
    }

    private static int utf8Length(int codePoint) {
        return new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
    }

    private static boolean isString(String suppliedData) {
        return !suppliedData.isEmpty()
            && (suppliedData.charAt(0) == '"' || suppliedData.charAt(0) == '\'' || suppliedData.charAt(0) == '{');
    }
}
