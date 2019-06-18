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

package org.apache.james.jmap.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;

import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.core.MailAddress;
import org.apache.james.core.MaybeSender;
import org.apache.james.jmap.model.EnvelopeUtils;
import org.apache.james.jmap.model.Keyword;
import org.apache.james.jmap.model.Keywords;
import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.mailbox.BlobManager;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryId;
import org.apache.james.mailbox.model.BlobId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.server.core.Envelope;
import org.apache.james.util.mime.MessageContentExtractor;
import org.apache.mailet.Mail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

class MessageSenderTest {

    private Envelope envelope;
    private MessageFactory.MetaDataWithContent message;
    private Message jmapMessage;

    @BeforeEach
    void setup() throws MailboxException {
        String headers = "From: me@example.com\n"
            + "To: 1@example.com\n"
            + "Cc: 2@example.com, 3@example.com\n"
            + "Bcc: 4@example.com\n"
            + "Subject: news\n";
        String content = headers
            + "Hello! How are you?";

        message = MessageFactory.MetaDataWithContent.builder()
            .uid(MessageUid.of(2))
            .keywords(Keywords.strictFactory().from(Keyword.SEEN))
            .size(content.length())
            .internalDate(Instant.now())
            .sharedContent(new SharedByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)))
            .attachments(ImmutableList.of())
            .mailboxId(InMemoryId.of(3))
            .messageId(TestMessageId.of(2))
            .build();

        MessagePreviewGenerator messagePreview = mock(MessagePreviewGenerator.class);
        HtmlTextExtractor htmlTextExtractor = mock(HtmlTextExtractor.class);
        when(messagePreview.compute(any())).thenReturn("text preview");

        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        BlobManager blobManager = mock(BlobManager.class);
        when(blobManager.toBlobId(any(MessageId.class))).thenReturn(BlobId.fromString("fake"));
        MessageFactory messageFactory = new MessageFactory(blobManager, messagePreview, messageContentExtractor, htmlTextExtractor);
        jmapMessage = messageFactory.fromMetaDataWithContent(message);
        envelope = EnvelopeUtils.fromMessage(jmapMessage);
    }

    @Test
    void buildMailShouldThrowWhenNullMailboxMessage() throws Exception {
        MessageFactory.MetaDataWithContent message = null;
        assertThatThrownBy(() -> MessageSender.buildMail(message, envelope)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildMailShouldThrowWhenNullJmapMessage() throws Exception {
        Envelope envelope = null;
        assertThatThrownBy(() -> MessageSender.buildMail(message, envelope)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildMailShouldGenerateMailWithExpectedProperties() throws Exception {
        String expectedName = jmapMessage.getId().serialize();
        MailAddress expectedSender = new MailAddress("me@example.com");
        Collection<MailAddress> expectedRecipients = ImmutableSet.of(
            new MailAddress("1@example.com"),
            new MailAddress("3@example.com"),
            new MailAddress("2@example.com"),
            new MailAddress("4@example.com"));

        Mail actual = MessageSender.buildMail(message, envelope);

        assertThat(actual.getName()).isEqualTo(expectedName);
        assertThat(actual.getMaybeSender()).isEqualTo(MaybeSender.of(expectedSender));
        assertThat(actual.getRecipients()).containsAll(expectedRecipients);
    }

}