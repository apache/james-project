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

package org.apache.james.imap.processor.fetch;

import static java.nio.charset.StandardCharsets.US_ASCII;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.james.imap.encode.FetchResponseEncoder;
import org.apache.james.imap.encode.ImapResponseComposer;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.FetchResponse;
import org.apache.james.imap.message.response.FetchResponse.BodyElement;
import org.apache.james.mailbox.MessageSequenceNumber;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Header;
import org.junit.jupiter.api.Test;

/**
 * Byte-exact regression guard: the {@code {N}} literal advertised for every {@code BODY[...]} section MUST equal
 * the exact number of octets streamed between {@code {N}\r\n} and the closing {@code )}. An undercount here
 * mis-frames the response for a byte-exact client (the leftover octets surface as a spurious tagged line).
 *
 * <p>Unlike the MPT {@code .test} scripts (which are line-oriented and never frame the literal by its declared
 * size), this test parses the raw wire bytes and asserts declared-vs-streamed equality.
 */
class FetchResponseLiteralSizeTest {
    private static final MessageSequenceNumber MSN = MessageSequenceNumber.of(1);
    // Matches the FIRST literal marker "{N}\r\n" in the response.
    private static final Pattern LITERAL = Pattern.compile("\\{(\\d+)}\r\n");

    private byte[] encode(BodyElement element) throws Exception {
        ByteImapResponseWriter writer = new ByteImapResponseWriter();
        ImapResponseComposer composer = new ImapResponseComposerImpl(writer);
        FetchResponse response = new FetchResponse(MSN, null, null, null, null, null, null,
            null, null, null, List.of(element), null, null);
        new FetchResponseEncoder(false).encode(response, composer);
        composer.flush();
        return writer.getBytes();
    }

    /**
     * Parses "... {N}\r\n<content>)\r\n" and returns [declaredN, measuredContentLength].
     * The response always ends with the section-closing ")" then CRLF, so the streamed content is everything
     * between the "{N}\r\n" marker and the trailing ")\r\n".
     */
    private long[] declaredVsStreamed(byte[] response) {
        String asString = new String(response, US_ASCII);
        Matcher matcher = LITERAL.matcher(asString);
        assertThat(matcher.find()).as("response must contain a literal marker: %s", asString).isTrue();
        long declared = Long.parseLong(matcher.group(1));
        int contentStart = matcher.end();
        // Response tail is ")\r\n" (3 octets): ")" closing the FETCH paren, then CRLF.
        int contentEnd = response.length - 3;
        assertThat(new String(response, contentEnd, 3, US_ASCII)).isEqualTo(")\r\n");
        return new long[] {declared, contentEnd - contentStart};
    }

    private void assertLiteralIsExact(BodyElement element) throws Exception {
        long[] declaredVsStreamed = declaredVsStreamed(encode(element));
        assertThat(declaredVsStreamed[0])
            .as("declared {%d} must equal streamed %d octets", declaredVsStreamed[0], declaredVsStreamed[1])
            .isEqualTo(declaredVsStreamed[1]);
    }

    @Test
    void headerFieldsMessageIdShouldAdvertiseExactLiteralSize() throws Exception {
        // The exact shape reported by the failing client: BODY[HEADER.FIELDS (MESSAGE-ID)] with a single header.
        // Streamed content is "Message-ID: <initial@source>\r\n" (30) + section-terminating "\r\n" (2) = 32 octets.
        HeaderBodyElement element = new HeaderBodyElement("BODY[HEADER.FIELDS (MESSAGE-ID)]",
            List.of(new Header("Message-ID", "<initial@source>")));

        long[] declaredVsStreamed = declaredVsStreamed(encode(element));

        assertThat(declaredVsStreamed[0]).isEqualTo(32);
        assertThat(declaredVsStreamed[1]).isEqualTo(32);
    }

    @Test
    void headerFieldsWithSeveralHeadersShouldAdvertiseExactLiteralSize() throws Exception {
        assertLiteralIsExact(new HeaderBodyElement("BODY[HEADER.FIELDS (FROM TO SUBJECT)]",
            List.of(
                new Header("From", "Timothy Tayler <timothy@example.org>"),
                new Header("To", "Samual Smith <samual@example.org>"),
                new Header("Subject", "A Simple Email"))));
    }

    @Test
    void fullBodyShouldAdvertiseExactLiteralSize() throws Exception {
        byte[] message = ("Message-ID: <initial@source>\r\n"
            + "Subject: A Simple Email\r\n"
            + "\r\n"
            + "This is a very simple email.\r\n").getBytes(US_ASCII);
        assertLiteralIsExact(new ContentBodyElement("BODY[]", new ByteContent(message)));
    }

    @Test
    void headerBlockShouldAdvertiseExactLiteralSize() throws Exception {
        byte[] headerBlock = ("Message-ID: <initial@source>\r\n"
            + "Subject: A Simple Email\r\n"
            + "\r\n").getBytes(US_ASCII);
        assertLiteralIsExact(new ContentBodyElement("BODY[HEADER]", new ByteContent(headerBlock)));
    }
}
