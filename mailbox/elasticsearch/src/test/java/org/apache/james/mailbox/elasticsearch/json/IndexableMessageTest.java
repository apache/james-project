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

package org.apache.james.mailbox.elasticsearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.elasticsearch.IndexAttachments;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.Property;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.api.NoopMetricFactory;
import org.assertj.core.api.iterable.Extractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class IndexableMessageTest {
    static final MessageUid MESSAGE_UID = MessageUid.of(154);

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    TikaTextExtractor textExtractor;

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new NoopMetricFactory(), new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @Test
    void textShouldBeEmptyWhenNoMatchingHeaders() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText()).isEmpty();
    }

    @Test
    void textShouldContainsFromWhenFrom() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("From: First user <user@james.org>\nFrom: Second user <user2@james.org>".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText())
            .contains("Second user user2@james.org")
            .contains("First user user@james.org");
    }

    @Test
    void textShouldContainsToWhenTo() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("To: First to <user@james.org>\nTo: Second to <user2@james.org>".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText())
            .contains("First to user@james.org")
            .contains("Second to user2@james.org");
    }

    @Test
    void textShouldContainsCcWhenCc() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("Cc: First cc <user@james.org>\nCc: Second cc <user2@james.org>".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText())
            .contains("Second cc user2@james.org")
            .contains("First cc user@james.org");
    }

    @Test
    void textShouldContainsBccWhenBcc() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("Bcc: First bcc <user@james.org>\nBcc: Second bcc <user2@james.org>".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText())
            .contains("Second bcc user2@james.org")
            .contains("First bcc user@james.org");
    }

    @Test
    void textShouldContainsSubjectsWhenSubjects() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("Subject: subject1\nSubject: subject2".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText()).isEqualTo("subject1 subject2");
    }

    @Test
    void textShouldContainsBodyWhenBody() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(new ByteArrayInputStream("\nMy body".getBytes()));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText()).isEqualTo("My body");
    }

    @Test
    void textShouldContainsAllFieldsWhenAllSet() throws Exception {
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        assertThat(indexableMessage.getText()).isEqualTo("Ad Min admin@opush.test " +
                "a@test a@test B b@test " + 
                "c@test c@test " +
                "dD d@test " + 
                "my subject " + 
                "Mail content\n" +
                "\n" +
                "-- \n" + 
                "Ad Min\n");
    }

    @Test
    void hasAttachmentsShouldReturnTrueWhenPropertyIsPresentAndTrue() throws IOException {
        //Given
        MailboxMessage  mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getProperties()).thenReturn(ImmutableList.of(IndexableMessage.HAS_ATTACHMENT_PROPERTY));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getHasAttachment()).isTrue();
    }

    @Test
    void hasAttachmentsShouldReturnFalseWhenPropertyIsPresentButFalse() throws IOException {
        //Given
        MailboxMessage  mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getProperties())
            .thenReturn(ImmutableList.of(new Property(PropertyBuilder.JAMES_INTERNALS, PropertyBuilder.HAS_ATTACHMENT, "false")));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        // Then
        assertThat(indexableMessage.getHasAttachment()).isFalse();
    }

    @Test
    void hasAttachmentsShouldReturnFalseWhenPropertyIsAbsent() throws IOException {
        //Given
        MailboxMessage  mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getProperties())
            .thenReturn(ImmutableList.of());

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        // Then
        assertThat(indexableMessage.getHasAttachment()).isFalse();
    }

    @Test
    void attachmentsShouldNotBeenIndexedWhenAsked() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .build();

        // Then
        assertThat(indexableMessage.getAttachments()).isEmpty();
    }

    @Test
    void attachmentsShouldBeenIndexedWhenAsked() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/emailWith3Attachments.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getAttachments()).isNotEmpty();
    }

    @Test
    void otherAttachmentsShouldBeenIndexedWhenOneOfThemCannotBeParsed() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/emailWith3Attachments.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        TextExtractor textExtractor = mock(TextExtractor.class);
        when(textExtractor.extractContent(any(), any()))
            .thenReturn(new ParsedContent(Optional.of("first attachment content"), ImmutableMap.of()))
            .thenThrow(new RuntimeException("second cannot be parsed"))
            .thenReturn(new ParsedContent(Optional.of("third attachment content"), ImmutableMap.of()));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(textExtractor)
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getAttachments())
            .extracting(new TextualBodyExtractor())
            .contains("first attachment content", TextualBodyExtractor.NO_TEXTUAL_BODY, "third attachment content");
    }

    static class TextualBodyExtractor implements Extractor<MimePart, String> {

        static final String NO_TEXTUAL_BODY = "The textual body is not present";

        @Override
        public String extract(MimePart input) {
            return input.getTextualBody().orElse(NO_TEXTUAL_BODY);
        }
    }

    @Test
    void messageShouldBeIndexedEvenIfTikaParserThrowsAnError() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/bodyMakeTikaToFail.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(textExtractor)
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getText()).contains("subject should be parsed");
    }

    @Test
    void shouldHandleCorrectlyMessageIdHavingSerializeMethodThatReturnNull() throws Exception {
       MessageId invalidMessageIdThatReturnNull = mock(MessageId.class);
       when(invalidMessageIdThatReturnNull.serialize())
           .thenReturn(null);
       
        // When
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(invalidMessageIdThatReturnNull);
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/bodyMakeTikaToFail.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(textExtractor)
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getMessageId()).isNull();
    }

    @Test
    void shouldHandleCorrectlyNullMessageId() throws Exception {
       
        // When
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(null);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/bodyMakeTikaToFail.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);

        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(textExtractor)
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .build();

        // Then
        assertThat(indexableMessage.getMessageId()).isNull();
    }
}
