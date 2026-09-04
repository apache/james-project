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

package org.apache.james.imap.decode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class ImapRequestLineReaderTest {
    private final OutputStream outputStream = null;
    private InputStream inputStream;
    private ImapRequestStreamLineReader lineReader;

    @Test
    void nextNonSpaceCharShouldReturnTheFirstCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("anyString \n").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test
    void nextNonSpaceCharShouldIgnoreTheSpaceAndReturnTheFirstNonSpaceCharacter() throws Exception {
        inputStream = new ByteArrayInputStream(("    anyString \n").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.nextNonSpaceChar()).isEqualTo('a');
    }

    @Test
    void nextNonSpaceCharShouldThrowExceptionWhenNotFound() {
        inputStream = new ByteArrayInputStream(("    ").getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThatThrownBy(() -> lineReader.nextNonSpaceChar()).isInstanceOf(DecodingException.class);
    }

    @Test
    void mailboxShouldDecodeModifiedUtf7WhenUtf8AcceptNotEnabled() throws Exception {
        // Wire form "a&--b" is the Modified UTF-7 encoding of "a&-b".
        inputStream = new ByteArrayInputStream("\"a&--b\" ".getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.mailbox()).isEqualTo("a&-b");
    }

    @Test
    void mailboxShouldDecodeUnicodeModifiedUtf7WhenUtf8AcceptNotEnabled() throws Exception {
        // Wire form "gr&AOU-" is the Modified UTF-7 encoding of "grå".
        inputStream = new ByteArrayInputStream("\"gr&AOU-\" ".getBytes(StandardCharsets.US_ASCII));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.mailbox()).isEqualTo("grå");
    }

    @Test
    void mailboxShouldReturnRawStringWhenUtf8AcceptEnabledAndNameContainsAmpersand() throws Exception {
        inputStream = new ByteArrayInputStream("\"a&-b\" ".getBytes(StandardCharsets.UTF_8));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);
        lineReader.setUtf8Accept(true);

        assertThat(lineReader.mailbox()).isEqualTo("a&-b");
    }

    @Test
    void mailboxShouldReturnRawUnicodeWhenUtf8AcceptEnabled() throws Exception {
        inputStream = new ByteArrayInputStream("\"grå\" ".getBytes(StandardCharsets.UTF_8));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);
        lineReader.setUtf8Accept(true);

        assertThat(lineReader.mailbox()).isEqualTo("grå");
    }

    @Test
    void astringShouldDecodeUtf8QuotedStringByDefault() throws Exception {
        // Many IMAP clients put UTF-8 in quoted-string arguments (e.g.
        // SEARCH HEADER Subject "grå") without an explicit CHARSET. RFC 9051
        // allows this, and we accept it regardless of UTF8=ACCEPT.
        inputStream = new ByteArrayInputStream("\"grå\" ".getBytes(StandardCharsets.UTF_8));
        lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        assertThat(lineReader.astring()).isEqualTo("grå");
    }
}