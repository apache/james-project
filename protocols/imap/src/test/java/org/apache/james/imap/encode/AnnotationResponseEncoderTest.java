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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.apache.james.imap.api.process.ImapSession;
import org.apache.james.imap.encode.base.ByteImapResponseWriter;
import org.apache.james.imap.encode.base.ImapResponseComposerImpl;
import org.apache.james.imap.message.response.AnnotationResponse;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

public class AnnotationResponseEncoderTest {
    private static final MailboxAnnotationKey PRIVATE_KEY = new MailboxAnnotationKey("/private/comment");
    private static final MailboxAnnotationKey SHARED_KEY = new MailboxAnnotationKey("/shared/comment");

    private static final MailboxAnnotation PRIVATE_ANNOTATION = MailboxAnnotation.newInstance(PRIVATE_KEY, "My own comment");
    private static final MailboxAnnotation SHARED_ANNOTATION = MailboxAnnotation.newInstance(SHARED_KEY, "Shared comment");

    private ByteImapResponseWriter byteImapResponseWriter;
    private ImapResponseComposer composer;
    private AnnotationResponseEncoder encoder;
    private ImapSession imapSession;

    @Before
    public void setUp() throws Exception {
        byteImapResponseWriter = new ByteImapResponseWriter();

        imapSession = mock(ImapSession.class);

        composer = new ImapResponseComposerImpl(byteImapResponseWriter, 1024);
        encoder = new AnnotationResponseEncoder();
    }

    @Test
    public void encodingShouldWellFormEmptyRequest() throws Exception {
        AnnotationResponse response = new AnnotationResponse(null, ImmutableList.of());

        encoder.encode(response, composer, imapSession);

        assertThat(byteImapResponseWriter.getString()).isEqualTo("* METADATA \"\"\r\n");
    }

    @Test
    public void encodingShouldWellFormWhenEmptyReturnedAnnotation() throws Exception {
        AnnotationResponse response = new AnnotationResponse("INBOX", ImmutableList.of());

        encoder.encode(response, composer, imapSession);

        assertThat(byteImapResponseWriter.getString()).isEqualTo("* METADATA \"INBOX\"\r\n");
    }

    @Test
    public void encodingShouldWellFormWhenOnlyOneReturnedAnnotation() throws Exception {
        AnnotationResponse response = new AnnotationResponse("INBOX", ImmutableList.of(PRIVATE_ANNOTATION));

        encoder.encode(response, composer, imapSession);

        assertThat(byteImapResponseWriter.getString()).isEqualTo("* METADATA \"INBOX\" (/private/comment \"My own comment\")\r\n");
    }

    @Test
    public void encodingShouldWellFormWhenManyReturnedAnnotations() throws Exception {
        AnnotationResponse response = new AnnotationResponse("INBOX", ImmutableList.of(PRIVATE_ANNOTATION, SHARED_ANNOTATION));
        encoder.encode(response, composer, imapSession);

        assertThat(byteImapResponseWriter.getString()).isEqualTo("* METADATA \"INBOX\" (/private/comment \"My own comment\" /shared/comment \"Shared comment\")\r\n");
    }

    @Test
    public void encodingShouldWellFormWhenNilReturnedAnnotation() throws Exception {
        AnnotationResponse response = new AnnotationResponse("INBOX", ImmutableList.of(MailboxAnnotation.nil(PRIVATE_KEY)));

        encoder.encode(response, composer, imapSession);

        assertThat(byteImapResponseWriter.getString()).isEqualTo("* METADATA \"INBOX\" ()\r\n");
    }
}
