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

package org.apache.james.imap.decode.parser;

import static org.apache.james.imap.ImapFixture.TAG;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class AclCommandParsersTest {
    private static final StatusResponseFactory STATUS_RESPONSE_FACTORY = mock(StatusResponseFactory.class);

    interface Decoder {
        ImapMessage decode(ImapRequestLineReader request) throws DecodingException;
    }

    static Stream<Arguments> emptyMailboxNames() {
        return Stream.of(
            Arguments.of((Decoder) request -> new GetACLCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "\"\"\r\n"),
            Arguments.of((Decoder) request -> new SetACLCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "\"\" bob lr\r\n"),
            Arguments.of((Decoder) request -> new DeleteACLCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "\"\" bob\r\n"),
            Arguments.of((Decoder) request -> new ListRightsCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "\"\" bob\r\n"),
            Arguments.of((Decoder) request -> new MyRightsCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "\"\"\r\n"));
    }

    @ParameterizedTest
    @MethodSource("emptyMailboxNames")
    void emptyMailboxNameShouldBeADecodingException(Decoder decoder, String arguments) {
        assertThatThrownBy(() -> decoder.decode(lineReader(arguments)))
            .isInstanceOf(DecodingException.class);
    }

    static Stream<Arguments> malformedIdentifiers() {
        return Stream.of("\"\"", "\"-\"", "$", "-$")
            .flatMap(identifier -> Stream.of(
                Arguments.of((Decoder) request -> new SetACLCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "INBOX " + identifier + " lr\r\n"),
                Arguments.of((Decoder) request -> new DeleteACLCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "INBOX " + identifier + "\r\n"),
                Arguments.of((Decoder) request -> new ListRightsCommandParser(STATUS_RESPONSE_FACTORY).decode(request, TAG, null), "INBOX " + identifier + "\r\n")));
    }

    @ParameterizedTest
    @MethodSource("malformedIdentifiers")
    void malformedIdentifierShouldBeADecodingException(Decoder decoder, String arguments) {
        assertThatThrownBy(() -> decoder.decode(lineReader(arguments)))
            .isInstanceOf(DecodingException.class);
    }

    private ImapRequestStreamLineReader lineReader(String arguments) {
        return new ImapRequestStreamLineReader(
            new ByteArrayInputStream(arguments.getBytes(StandardCharsets.US_ASCII)), new ByteArrayOutputStream());
    }
}
