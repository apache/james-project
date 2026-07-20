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

package org.apache.james.imap.encode;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.BytesBackedLiteral;
import org.apache.james.imap.message.Literal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ImapResponseComposerImplTest {
    private AtomicInteger byteWrites;
    private AtomicInteger literalWrites;
    private ByteImapResponseWriter writer;
    private ImapResponseComposer composer;

    @BeforeEach
    void setUp() {
        byteWrites = new AtomicInteger();
        literalWrites = new AtomicInteger();
        writer = new ByteImapResponseWriter() {
            @Override
            public void write(byte[] buffer) throws IOException {
                byteWrites.incrementAndGet();
                super.write(buffer);
            }

            @Override
            public void write(Literal literal) throws IOException {
                literalWrites.incrementAndGet();
                super.write(literal);
            }
        };
        composer = new ImapResponseComposerImpl(writer);
    }

    @Test
    void encodeShouldHandleQuotedChar128() throws Exception {
        Character c = 128;
        composer.quote(c.toString());
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" \"?\"\r\n");
    }

    @Test
    void plainResponseShouldBeWrittenAsBytesWithoutLiteralWrite() throws Exception {
        composer.untaggedResponse("OK completed");
        composer.flush();

        assertThat(writer.getString()).isEqualTo("* OK completed\r\n");
        assertThat(byteWrites).hasValue(1);
        assertThat(literalWrites).hasValue(0);
    }

    @Test
    void fetchBodyResponseShouldBeEmittedAsASingleSequencedLiteral() throws Exception {
        composer.untagged().message("1").message("FETCH");
        composer.openParen();
        composer.message("BODY[]");
        composer.literal(BytesBackedLiteral.of("Body of Initial".getBytes(US_ASCII)));
        composer.closeParen();
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo("* 1 FETCH (BODY[] {15}\r\nBody of Initial)\r\n");
        // Whole response (prefix + literal + trailing ")" + CRLF) leaves through a single writer call, no
        // separate byte[] write that would split the literal from the ")".
        assertThat(literalWrites).hasValue(1);
        assertThat(byteWrites).hasValue(0);
    }

    @Test
    void multipleLiteralsShouldBeEmittedAsASingleSequencedLiteral() throws Exception {
        composer.openParen();
        composer.message("BODY[HEADER]");
        composer.literal(BytesBackedLiteral.of("H".getBytes(US_ASCII)));
        composer.message("BODY[TEXT]");
        composer.literal(BytesBackedLiteral.of("T".getBytes(US_ASCII)));
        composer.closeParen();
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" (BODY[HEADER] {1}\r\nH BODY[TEXT] {1}\r\nT)\r\n");
        assertThat(literalWrites).hasValue(1);
        assertThat(byteWrites).hasValue(0);
    }

    @Test
    void flushIfNeededShouldNotFlushWhenBelowTheThreshold() throws Exception {
        composer.message("BODY[]");
        composer.literal(BytesBackedLiteral.of("small".getBytes(US_ASCII)));
        composer.flushIfNeeded();

        assertThat(literalWrites).hasValue(0);
        assertThat(byteWrites).hasValue(0);
    }

    @Test
    void flushIfNeededShouldFlushWhenDeferredBytesExceedTheThreshold() throws Exception {
        composer.message("BODY[]");
        composer.literal(BytesBackedLiteral.of(new byte[9000]));
        composer.flushIfNeeded();

        assertThat(literalWrites).hasValue(1);
    }

    @Test
    void emptyLiteralShouldNotForceASequencedLiteral() throws Exception {
        composer.message("BODY[]");
        composer.literal(BytesBackedLiteral.of(new byte[0]));
        composer.end();
        composer.flush();

        assertThat(writer.getString()).isEqualTo(" BODY[] {0}\r\n\r\n");
        assertThat(byteWrites).hasValue(1);
        assertThat(literalWrites).hasValue(0);
    }
}
