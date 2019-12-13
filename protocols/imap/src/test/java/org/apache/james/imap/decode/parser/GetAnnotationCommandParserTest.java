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
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.imap.api.ImapConstants;
import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.encode.FakeImapSession;
import org.apache.james.imap.message.request.GetAnnotationRequest;
import org.apache.james.imap.message.request.GetAnnotationRequest.Depth;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.junit.Before;
import org.junit.Test;

public class GetAnnotationCommandParserTest {
    private static final String INBOX = "anyInboxName";
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");
    private static final ImapSession session = new FakeImapSession();
    private static final OutputStream outputStream = null;

    private GetAnnotationCommandParser parser;

    @Before
    public void setUp() throws Exception {
        parser = new GetAnnotationCommandParser(mock(StatusResponseFactory.class));
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowsExceptionWhenCommandHasNotMailbox() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream(" \n".getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test
    public void decodeMessageShouldReturnRequestWhenCommandHasMailboxOnly() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + "    \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest) parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getKeys()).isEmpty();
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).isEmpty();
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasOneKeyButInWrongFormat() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " /private/comment extrastring \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test
    public void decodeMessageShouldReturnRequestWhenCommandHasOnlyOneKey() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " /private/comment \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest) parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getKeys()).containsOnly(PRIVATE_KEY);
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).isEmpty();
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasOneInvalidKey() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + "/shared/comment private/comment \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test
    public void decodeMessageShouldReturnRequestWhenCommandHasMultiKeys() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest) parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getKeys()).contains(PRIVATE_KEY, SHARED_KEY);
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).isEmpty();
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMultiKeysButInWrongFormat() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment) (/another/key/group)\n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMultiKeysAndSingleKey() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment) /another/key \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMultiKeysButNotOpenQuote() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " /shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMultiKeysButNotCloseQuote() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMaxsizeOptButInWrongPlace() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment) (MAXSIZE 1024) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMaxsizeWithWrongValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE invalid) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMaxsizeWithoutValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMaxsizeDoesNotInParenthesis() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " MAXSIZE 1024 (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasMaxsizeDoesNotInParenthesisAndNoValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " MAXSIZE (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test
    public void decodeMessageShouldReturnRequestWhenCommandHasMaxsizeOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest) parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getKeys()).contains(PRIVATE_KEY, SHARED_KEY);
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).contains(1024);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasWrongMaxsizeOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZErr 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasWrongMaxsizeValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE 0) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasWrongDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH -1) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasWrongDepthOptionName() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTHerr 1) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasDepthOptionButNoValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasDepthOptionButInvalidValue() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH invalid) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasDepthOptionButNotInParenthesis() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " DEPTH (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldReturnRequestWhenCommandHasDepthOptionAndValueButNotInParenthesis() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " DEPTH 1 (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test
    public void decodeMessageShouldReturnRequestWithZeroDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH 0) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, null);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).contains(1024);
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test
    public void decodeMessageShouldReturnRequestWithOneDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH 1) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.ONE);
        assertThat(request.getMaxsize()).contains(1024);
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test
    public void decodeMessageShouldReturnRequestWhenCommandHasOptionsInAnyOrder() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE 1024) (DEPTH 1) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.ONE);
        assertThat(request.getMaxsize()).contains(1024);
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test
    public void decodeMessageShouldReturnRequestWithInfinityDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH infinity) (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.INFINITY);
        assertThat(request.getMaxsize()).contains(1024);
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test
    public void decodeMessageShouldReturnRequestWithOnlyInfinityDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH infinity) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.INFINITY);
        assertThat(request.getMaxsize()).isEmpty();
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test
    public void decodeMessageShouldReturnRequestWithDefaultDepthOptionWhenCommandHasDoesNotHaveDepthOption() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (MAXSIZE 1024) (/shared/comment /private/comment) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        GetAnnotationRequest request = (GetAnnotationRequest)parser.decode(lineReader, TAG, session);

        assertThat(request.getTag()).isEqualTo(TAG);
        assertThat(request.getCommand()).isEqualTo(ImapConstants.GETANNOTATION_COMMAND);
        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getDepth()).isEqualTo(Depth.ZERO);
        assertThat(request.getMaxsize()).contains(1024);
        assertThat(request.getKeys()).contains(SHARED_KEY, PRIVATE_KEY);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasOneDepthButWithoutKey() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH 1) (MAXSIZE 1024) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasInfinityDepthButWithoutKey() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (DEPTH infinity) (MAXSIZE 1024) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }

    @Test(expected = DecodingException.class)
    public void decodeMessageShouldThrowExceptionWhenCommandHasDepthOptionInWrongPlace() throws DecodingException {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/shared/comment /private/comment) (DEPTH infinity) \n").getBytes(StandardCharsets.US_ASCII));
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, outputStream);

        parser.decode(lineReader, TAG, session);
    }
}
