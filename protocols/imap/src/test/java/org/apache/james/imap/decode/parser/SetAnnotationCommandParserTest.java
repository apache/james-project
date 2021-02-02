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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import org.apache.james.imap.api.message.response.StatusResponseFactory;
import org.apache.james.imap.decode.DecodingException;
import org.apache.james.imap.decode.ImapRequestStreamLineReader;
import org.apache.james.imap.message.request.SetAnnotationRequest;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.junit.jupiter.api.Test;

class SetAnnotationCommandParserTest {
    private static final String INBOX = "anyMailboxName";
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "This is my comment");
    private static final MailboxAnnotation SHARED_ANNOTATION = MailboxAnnotation.newInstance(SHARED_KEY, "This one is for you!");
    private static final MailboxAnnotation NIL_ANNOTATION = MailboxAnnotation.nil(PRIVATE_KEY);

    private SetAnnotationCommandParser parser = new SetAnnotationCommandParser(mock(StatusResponseFactory.class));

    @Test
    void decodeMessageShouldReturnRequestContainsOneAnnotation() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment \"This is my comment\") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);
        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getMailboxAnnotations()).containsOnly(PRIVATE_ANNOTATION);
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenContainsInvalidAnnotationKey() {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/pri*vate/comment \"This is my comment\") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeMessageShouldReturnRequestContainsOneNilAnnotation() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment NIL) \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);
        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getMailboxAnnotations()).containsOnly(NIL_ANNOTATION);
    }

    @Test
    void decodeMessageShouldReturnRequestContainsOneAnnotationWithMultiLinesValue() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment {32}\nMy new comment across two lines.) \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, new ByteArrayOutputStream());
        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getMailboxAnnotations()).containsOnly(MailboxAnnotation.newInstance(PRIVATE_KEY, "My new comment across two lines."));
    }

    @Test
    void decodeMessageShouldReturnRequestContainsMultiAnnotations() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment \"This is my comment\" /shared/comment \"This one is for you!\") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, new ByteArrayOutputStream());
        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getMailboxAnnotations()).containsExactly(PRIVATE_ANNOTATION, SHARED_ANNOTATION);
    }

    @Test
    void decodeMessageShouldReturnRequestContainsMultiAnnotationsWithNil() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment NIL /shared/comment \"This one is for you!\") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, new ByteArrayOutputStream());
        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxName()).isEqualTo(INBOX);
        assertThat(request.getMailboxAnnotations()).containsExactly(NIL_ANNOTATION, SHARED_ANNOTATION);
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenCommandDoesNotStartWithSlash() {
        InputStream inputStream = new ByteArrayInputStream("INBOX /private/comment \"This is my comment\") \n".getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenCommandDoesNotEndWithSlash() {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment \"This is my comment\" \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenCommandDoesNotHaveAnnotationValue() {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment) \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeMessageShouldReturnRequestWhenCommandHasEmptyAnnotationValue() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment \"   \") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxAnnotations().get(0).getValue()).isPresent();
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenCommandAnnotationValueNotInQuote() {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment This is my comment) \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }

    @Test
    void decodeMessageShouldReturnRequestWhenCommandAnnotationValueIsNILString() throws Exception {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " (/private/comment \"NIL\") \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        SetAnnotationRequest request = (SetAnnotationRequest) parser.decode(lineReader, TAG, null);

        assertThat(request.getMailboxAnnotations().get(0).getValue()).isPresent();
    }

    @Test
    void decodeMessageShouldThrowDecodingExceptionWhenCommandMissingAnnotations() {
        InputStream inputStream = new ByteArrayInputStream((INBOX + " () \n").getBytes());
        ImapRequestStreamLineReader lineReader = new ImapRequestStreamLineReader(inputStream, null);

        assertThatThrownBy(() -> parser.decode(lineReader, TAG, null))
            .isInstanceOf(DecodingException.class);
    }
}
