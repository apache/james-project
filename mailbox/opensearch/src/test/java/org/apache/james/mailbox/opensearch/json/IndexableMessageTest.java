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

package org.apache.james.mailbox.opensearch.json;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.Optional;

import javax.mail.Flags;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.extractor.ParsedContent;
import org.apache.james.mailbox.extractor.TextExtractor;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.opensearch.IndexAttachments;
import org.apache.james.mailbox.opensearch.IndexHeaders;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.tika.TikaConfiguration;
import org.apache.james.mailbox.tika.TikaExtension;
import org.apache.james.mailbox.tika.TikaHttpClientImpl;
import org.apache.james.mailbox.tika.TikaTextExtractor;
import org.apache.james.metrics.tests.RecordingMetricFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Mono;

class IndexableMessageTest {
    static final MessageUid MESSAGE_UID = MessageUid.of(154);

    @RegisterExtension
    static TikaExtension tika = new TikaExtension();

    TikaTextExtractor textExtractor;

    @BeforeEach
    void setUp() throws Exception {
        textExtractor = new TikaTextExtractor(new RecordingMetricFactory(), new TikaHttpClientImpl(TikaConfiguration.builder()
                .host(tika.getIp())
                .port(tika.getPort())
                .timeoutInMillis(tika.getTimeoutInMillis())
                .build()));
    }

    @Test
    void hasAttachmentsShouldReturnTrueWhenNonInlined() throws IOException {
        //Given
        MailboxMessage  mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        InMemoryMessageId messageId = InMemoryMessageId.of(42);
        when(mailboxMessage.getMessageId())
            .thenReturn(messageId);
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/mailWithHeaders.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getAttachments())
            .thenReturn(ImmutableList.of(MessageAttachmentMetadata.builder()
                .attachment(AttachmentMetadata.builder()
                    .messageId(messageId)
                    .attachmentId(AttachmentId.from("1"))
                    .type("text/plain")
                    .size(36)
                    .build())
                .isInline(false)
                .build()));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getHasAttachment()).isTrue();
    }

    @Test
    void hasAttachmentsShouldReturnFalseWhenEmptyAttachments() throws IOException {
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
        when(mailboxMessage.getAttachments())
            .thenReturn(ImmutableList.of());

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(new DefaultTextExtractor())
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.NO)
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

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
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getAttachments()).isEmpty();
    }

    @Test
    void headersShouldNotBeenIndexedWhenAsked() throws Exception {
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
                .indexAttachments(IndexAttachments.YES)
                .indexHeaders(IndexHeaders.NO)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getHeaders()).isEmpty();
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
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getAttachments()).isNotEmpty();
    }

    @SuppressWarnings("checkstyle:LocalVariableName")
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
        when(textExtractor.applicable(any())).thenReturn(true);
        when(textExtractor.extractContentReactive(any(), any()))
            .thenReturn(Mono.just(ParsedContent.of("first attachment content")))
            .thenReturn(Mono.error(new RuntimeException("second cannot be parsed")))
            .thenReturn(Mono.just(ParsedContent.of("third attachment content")));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
                .message(mailboxMessage)
                .extractor(textExtractor)
                .zoneId(ZoneId.of("Europe/Paris"))
                .indexAttachments(IndexAttachments.YES)
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        String NO_TEXTUAL_BODY = "The textual body is not present";
        assertThat(indexableMessage.getAttachments())
            .extracting(input -> input.getTextualBody().orElse(NO_TEXTUAL_BODY))
            .contains("first attachment content", NO_TEXTUAL_BODY, "third attachment content");
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
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getMessageId()).isNull();
    }

    @Test
    void shouldHandleCorrectlyThreadIdHavingSerializeMethodThatReturnNull() throws Exception {
        ThreadId invalidThreadIdThatReturnNull = mock(ThreadId.class);
        when(invalidThreadIdThatReturnNull.serialize())
            .thenReturn(null);

        // When
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getThreadId())
            .thenReturn(invalidThreadIdThatReturnNull);
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
                .indexHeaders(IndexHeaders.YES)
            .build()
            .block();

        // Then
        assertThat(indexableMessage.getThreadId()).isNull();
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
                .indexHeaders(IndexHeaders.YES)
                .build()
                .block();

        // Then
        assertThat(indexableMessage.getMessageId()).isNull();
    }

    @Test
    void shouldHandleCorrectlyNullThreadId() throws Exception {

        // When
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getMessageId())
            .thenReturn(null);
        when(mailboxMessage.getThreadId())
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
                .indexHeaders(IndexHeaders.YES)
            .build()
            .block();

        // Then
        assertThat(indexableMessage.getThreadId()).isNull();
    }

    @Test
    void shouldSerializeThreadIdCorrectly() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getThreadId())
            .thenReturn(ThreadId.fromBaseMessageId(InMemoryMessageId.of(42)));
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
            .indexAttachments(IndexAttachments.NO)
            .indexHeaders(IndexHeaders.YES)
            .build()
            .block();

        // Then
        assertThat(indexableMessage.getThreadId()).isEqualTo("42");
    }

    @Test
    void shouldSerializeSaveDate() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        ZonedDateTime now = ZonedDateTime.parse("2015-10-30T14:12:00Z");
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getThreadId())
            .thenReturn(ThreadId.fromBaseMessageId(InMemoryMessageId.of(42)));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/emailWith3Attachments.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getSaveDate())
            .thenReturn(Optional.of(Date.from(now.toInstant())));

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
            .message(mailboxMessage)
            .extractor(new DefaultTextExtractor())
            .zoneId(ZoneId.of("Europe/Paris"))
            .indexAttachments(IndexAttachments.NO)
            .indexHeaders(IndexHeaders.YES)
            .build()
            .block();

        // Then
        assertThat(indexableMessage.getSaveDate().get()).startsWith("2015-10-30");
    }

    @Test
    void shouldAcceptEmptySaveDate() throws Exception {
        //Given
        MailboxMessage mailboxMessage = mock(MailboxMessage.class);
        TestId mailboxId = TestId.of(1);
        when(mailboxMessage.getMailboxId())
            .thenReturn(mailboxId);
        when(mailboxMessage.getModSeq())
            .thenReturn(ModSeq.first());
        when(mailboxMessage.getMessageId())
            .thenReturn(InMemoryMessageId.of(42));
        when(mailboxMessage.getThreadId())
            .thenReturn(ThreadId.fromBaseMessageId(InMemoryMessageId.of(42)));
        when(mailboxMessage.getFullContent())
            .thenReturn(ClassLoader.getSystemResourceAsStream("eml/emailWith3Attachments.eml"));
        when(mailboxMessage.createFlags())
            .thenReturn(new Flags());
        when(mailboxMessage.getUid())
            .thenReturn(MESSAGE_UID);
        when(mailboxMessage.getSaveDate())
            .thenReturn(Optional.empty());

        // When
        IndexableMessage indexableMessage = IndexableMessage.builder()
            .message(mailboxMessage)
            .extractor(new DefaultTextExtractor())
            .zoneId(ZoneId.of("Europe/Paris"))
            .indexAttachments(IndexAttachments.NO)
            .indexHeaders(IndexHeaders.YES)
            .build()
            .block();

        // Then
        assertThat(indexableMessage.getSaveDate()).isEmpty();
    }
}
