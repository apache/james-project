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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Fail.fail;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.james.imap.api.ImapCommand;
import org.apache.james.imap.api.ImapMessage;
import org.apache.james.imap.api.display.HumanReadableText;
import org.apache.james.imap.api.message.request.SearchKey;
import org.apache.james.imap.api.message.response.StatusResponse;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestLineReader;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.encode.FakeImapSession;
import org.junit.Before;
import org.junit.Test;

public class SearchCommandParserQuotedCharsetTest {
    private static final String ASCII_SEARCH_TERM = "A Search Term";

    private static final String NON_ASCII_SEARCH_TERM = "как Дела?";

    private static final String LENGTHY_NON_ASCII_SEARCH_TERM = NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM
            + NON_ASCII_SEARCH_TERM;

    private static final byte[] BYTES_LENGTHY_NON_ASCII_SEARCH_TERM = NioUtils
            .toBytes(LENGTHY_NON_ASCII_SEARCH_TERM, StandardCharsets.UTF_8);

    private static final byte[] BYTES_NON_ASCII_SEARCH_TERM = NioUtils.toBytes(
            NON_ASCII_SEARCH_TERM, StandardCharsets.UTF_8);

    private static final byte[] BYTES_QUOTED_UTF8_LENGTHY_NON_ASCII_SEARCH_TERM = add(
            add(NioUtils.toBytes(" \"", StandardCharsets.US_ASCII),
                    BYTES_LENGTHY_NON_ASCII_SEARCH_TERM), NioUtils.toBytes(
                    "\"", StandardCharsets.US_ASCII));

    private static final byte[] BYTES_QUOTED_UTF8_NON_ASCII_SEARCH_TERM = add(
            add(NioUtils.toBytes(" \"", StandardCharsets.US_ASCII), BYTES_NON_ASCII_SEARCH_TERM),
            NioUtils.toBytes("\"", StandardCharsets.US_ASCII));

    private static final byte[] BYTES_UTF8_NON_ASCII_SEARCH_TERM = add(NioUtils
            .toBytes(" {16}\r\n", StandardCharsets.US_ASCII), BYTES_NON_ASCII_SEARCH_TERM);

    private static final byte[] CHARSET = NioUtils.toBytes("CHARSET UTF-8 ",
        StandardCharsets.US_ASCII);

    private static byte[] add(byte[] one, byte[] two) {
        byte[] results = new byte[one.length + two.length];
        System.arraycopy(one, 0, results, 0, one.length);
        System.arraycopy(two, 0, results, one.length, two.length);
        return results;
    }

    SearchCommandParser parser;
    StatusResponseFactory mockStatusResponseFactory;
    ImapCommand command;
    ImapMessage message;

    private ImapSession session;
    
    @Before
    public void setUp() throws Exception {
        mockStatusResponseFactory = mock(StatusResponseFactory.class);
        parser = new SearchCommandParser(mockStatusResponseFactory);
        command = ImapCommand.anyStateCommand("Command");
        message = mock(ImapMessage.class);
        session = new FakeImapSession();
    }

    @Test
    public void testShouldDecoderLengthyQuotedCharset() throws Exception {
        SearchKey key = SearchKey.buildBcc(LENGTHY_NON_ASCII_SEARCH_TERM);
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(add(add(CHARSET, "BCC"
                        .getBytes(StandardCharsets.US_ASCII)),
                        BYTES_QUOTED_UTF8_LENGTHY_NON_ASCII_SEARCH_TERM)),
                new ByteArrayOutputStream());
        final SearchKey searchKey = parser.searchKey(null, reader, null, true);
        assertThat(searchKey).isEqualTo(key);
    }

    @Test
    public void testShouldDecoderQuotedCharset() throws Exception {
        SearchKey key = SearchKey.buildBcc(NON_ASCII_SEARCH_TERM);
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(add(add(CHARSET, "BCC"
                        .getBytes(StandardCharsets.US_ASCII)),
                        BYTES_QUOTED_UTF8_NON_ASCII_SEARCH_TERM)),
                new ByteArrayOutputStream());
        final SearchKey searchKey = parser.searchKey(null, reader, null, true);
        assertThat(searchKey).isEqualTo(key);
    }

    @Test
    public void testBadCharset() throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream("CHARSET BOGUS ".getBytes(StandardCharsets.US_ASCII)),
                new ByteArrayOutputStream());
        parser.decode(command, reader, TAG, false, session);

        verify(mockStatusResponseFactory, times(1)).taggedNo(
            eq(TAG),
            same(command),
            eq(HumanReadableText.BAD_CHARSET),
            eq(StatusResponse.ResponseCode.badCharset()));
        verifyNoMoreInteractions(mockStatusResponseFactory);
    }

    @Test
    public void testShouldThrowProtocolExceptionWhenBytesAreNotEncodedByCharset() {
        try {
            ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                    new ByteArrayInputStream(add("CHARSET US-ASCII BCC "
                            .getBytes(StandardCharsets.US_ASCII), BYTES_NON_ASCII_SEARCH_TERM)),
                    new ByteArrayOutputStream());
            parser.decode(command, reader, TAG, false, session);
            fail("A protocol exception should be thrown when charset is incompatible with input");
        } catch (DecodingException e) {
            // expected
        }
    }

    @Test
    public void testBCCShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildBcc(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("BCC".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testBODYShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildBody(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("BODY".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testCCShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildCc(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("CC".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testFROMShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildFrom(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("FROM".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testHEADERShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey
                .buildHeader("whatever", NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("HEADER whatever".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testSUBJECTShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildSubject(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("SUBJECT".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testTEXTShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildText(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("TEXT".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testTOShouldConvertCharset() throws Exception {
        SearchKey key = SearchKey.buildTo(NON_ASCII_SEARCH_TERM);
        checkUTF8Valid("TO".getBytes(StandardCharsets.US_ASCII), key);
    }

    @Test
    public void testASCIICharset() throws Exception {
        SearchKey key = SearchKey.buildBcc(ASCII_SEARCH_TERM);
        checkValid("CHARSET US-ASCII BCC \"" + ASCII_SEARCH_TERM + "\"", key,
                true, StandardCharsets.US_ASCII);
    }

    @Test
    public void testSimpleUTF8Charset() throws Exception {
        SearchKey key = SearchKey.buildBcc(ASCII_SEARCH_TERM);
        checkValid("CHARSET UTF-8 BCC \"" + ASCII_SEARCH_TERM + "\"", key,
                true, StandardCharsets.US_ASCII);
    }

    private void checkUTF8Valid(byte[] term, SearchKey key)
            throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(add(add(CHARSET, term),
                        BYTES_UTF8_NON_ASCII_SEARCH_TERM)),
                new ByteArrayOutputStream());
        final SearchKey searchKey = parser.searchKey(null, reader, null, true);
        assertThat(searchKey).isEqualTo(key);
    }

    private void checkValid(String input, SearchKey key, boolean isFirst, Charset charset) throws Exception {
        ImapRequestLineReader reader = new ImapRequestStreamLineReader(
                new ByteArrayInputStream(input.getBytes(charset)),
                new ByteArrayOutputStream());

        final SearchKey searchKey = parser.searchKey(null, reader, null, isFirst);
        assertThat(searchKey).isEqualTo(key);
    }

}
