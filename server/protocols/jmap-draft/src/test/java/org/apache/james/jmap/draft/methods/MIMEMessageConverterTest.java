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

package org.apache.james.jmap.draft.methods;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Date;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.apache.james.core.Username;
import org.apache.james.jmap.draft.methods.ValueWithId.MessageWithId;
import org.apache.james.jmap.model.Attachment;
import org.apache.james.jmap.model.Blob;
import org.apache.james.jmap.model.BlobId;
import org.apache.james.jmap.draft.model.CreationMessage;
import org.apache.james.jmap.draft.model.CreationMessage.DraftEmailer;
import org.apache.james.jmap.draft.model.CreationMessageId;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.model.StringBackedAttachmentId;
import org.apache.james.mime4j.dom.Entity;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.mime4j.dom.Multipart;
import org.apache.james.mime4j.dom.SingleBody;
import org.apache.james.mime4j.dom.TextBody;
import org.apache.james.mime4j.dom.address.Mailbox;
import org.apache.james.mime4j.dom.field.ContentTypeField;
import org.apache.james.mime4j.message.BasicBodyFactory;
import org.apache.james.mime4j.stream.Field;
import org.assertj.core.data.Index;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

class MIMEMessageConverterTest {
    MailboxSession session;

    @BeforeEach
    void setUp() {
        session = MailboxSessionUtil.create(Username.of("bob"));
    }

    @Test
    void convertToMimeShouldAddInReplyToHeaderWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String matchingMessageId = "unique-message-id";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .inReplyToMessageId(matchingMessageId)
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("In-Reply-To")).extracting(Field::getBody)
                .containsOnly(matchingMessageId);
    }

    @Test
    void convertToMimeShouldGenerateMessageId() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage message = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).extracting(Field::getBody)
                .isNotNull();
    }

    @Test
    void convertToMimeShouldGenerateMessageIdWhenSenderWithoutDomain() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage message = CreationMessage.builder()
                .from(DraftEmailer.builder().email("sender").build())
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).extracting(Field::getBody)
                .isNotNull();
    }

    @Test
    void convertToMimeShouldGenerateMessageIdContainingSenderDomain() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage message = CreationMessage.builder()
                .from(DraftEmailer.builder().email("email@domain.com").build())
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), message), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("Message-ID")).hasSize(1);
        assertThat(result.getHeader().getFields("Message-ID").get(0).getBody())
            .contains("@domain.com");
    }

    @Test
    void convertToMimeShouldAddHeaderWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
                .containsOnly("first value");
    }

    @Test
    void convertToMimeShouldAddHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value", "SECOND", "second value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
                .containsOnly("first value");
        assertThat(result.getHeader().getFields("SECOND")).extracting(Field::getBody)
            .containsOnly("second value");
    }

    @Test
    void convertToMimeShouldFilterGeneratedHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String joesEmail = "joe@example.com";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .headers(ImmutableMap.of("From", "hacker@example.com", "VALID", "valid header value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress)
            .allMatch(f -> f.equals(joesEmail));
        assertThat(result.getHeader().getFields("VALID")).extracting(Field::getBody)
            .containsOnly("valid header value");
        assertThat(result.getHeader().getFields("From")).extracting(Field::getBody)
            .containsOnly("joe <joe@example.com>");
    }

    @Test
    void convertToMimeShouldFilterGeneratedHeadersRegardlessOfCaseWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String joesEmail = "joe@example.com";
        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .headers(ImmutableMap.of("frOM", "hacker@example.com", "VALID", "valid header value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress)
            .allMatch(f -> f.equals(joesEmail));
        assertThat(result.getHeader().getFields("VALID")).extracting(Field::getBody)
            .containsOnly("valid header value");
        assertThat(result.getHeader().getFields("From")).extracting(Field::getBody)
            .containsOnly("joe <joe@example.com>");
    }

    @Test
    void convertToMimeShouldAddMultivaluedHeadersWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("sender").build())
                .headers(ImmutableMap.of("FIRST", "first value\nsecond value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("FIRST")).extracting(Field::getBody)
            .containsOnly("first value", "second value");
    }

    @Test
    void convertToMimeShouldFilterEmptyHeaderNames() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("joe").build())
                .headers(ImmutableMap.of("", "empty header name value"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("")).isEmpty();
    }

    @Test
    void convertToMimeShouldFilterWhiteSpacesOnlyHeaderNames() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage messageHavingInReplyTo = CreationMessage.builder()
                .from(DraftEmailer.builder().name("joe").build())
                .headers(ImmutableMap.of("   ", "only spaces header name values"))
                .mailboxIds(ImmutableList.of("dead-beef-1337"))
                .subject("subject")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), messageHavingInReplyTo), ImmutableList.of());

        // Then
        assertThat(result.getHeader().getFields("   ")).isEmpty();
        assertThat(result.getHeader().getFields("")).isEmpty();
    }

    @Test
    void convertToMimeShouldThrowWhenMessageIsNull() {
        MIMEMessageConverter sut = new MIMEMessageConverter();

        assertThatThrownBy(() -> sut.convertToMime(
                new ValueWithId.CreationMessageEntry(CreationMessageId.of("any"), null),
                ImmutableList.of()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void convertToMimeShouldSetBothFromAndSenderHeaders() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        String joesEmail = "joe@example.com";
        CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("deadbeef-dead-beef-dead-beef"))
                .subject("subject")
                .from(DraftEmailer.builder().email(joesEmail).name("joe").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getFrom()).extracting(Mailbox::getAddress).allMatch(f -> f.equals(joesEmail));
        assertThat(result.getSender().getAddress()).isEqualTo(joesEmail);
    }

    @Test
    void convertToMimeShouldSetCorrectLocalDate() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        Instant now = Instant.now();
        ZonedDateTime messageDate = ZonedDateTime.ofInstant(now, ZoneId.systemDefault());

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .date(messageDate)
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getDate()).isEqualToIgnoringMillis(Date.from(now));
    }

    @Test
    void convertToMimeShouldSetQuotedPrintableContentTransferEncodingWhenText() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getHeader()
                .getField("Content-Transfer-Encoding")
                .getBody())
            .isEqualTo("quoted-printable");
    }

    @Test
    void convertToMimeShouldSetTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("Hello all!", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldSetEmptyBodyWhenNoBodyProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldSetHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("Hello <b>all</b>!", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
    }

    @Test
    void convertToMimeShouldGenerateMultipartWhenHtmlBodyAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isInstanceOf(Multipart.class);
        assertThat(result.isMultipart()).isTrue();
        assertThat(result.getMimeType()).isEqualTo("multipart/alternative");
        Multipart typedResult = (Multipart)result.getBody();
        assertThat(typedResult.getBodyParts()).hasSize(2);
    }

    @Test
    void convertShouldGenerateExpectedMultipartWhenHtmlAndTextBodyProvided() throws Exception {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all</b>!")
                .build();

        String expectedHeaders = "MIME-Version: 1.0\r\n" +
                "Content-Type: multipart/alternative;\r\n" +
                " boundary=\"-=Part.";
        String expectedPart1 = "Content-Type: text/plain; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello all!\r\n";
        String expectedPart2 = "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Transfer-Encoding: quoted-printable\r\n" +
                "\r\n" +
                "Hello <b>all</b>!\r\n";

        // When
        byte[] convert = sut.convert(new MessageWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        String actual = new String(convert, StandardCharsets.UTF_8);
        assertThat(actual).startsWith(expectedHeaders);
        assertThat(actual).contains(expectedPart1);
        assertThat(actual).contains(expectedPart2);
    }

    @Test
    void convertToMimeShouldSetMimeTypeWhenTextBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Test
    void convertToMimeShouldSetMimeTypeWhenHtmlBody() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    void convertToMimeShouldSetEmptyHtmlBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/html");
    }

    @Test
    void convertToMimeShouldSetEmptyTextBodyWhenProvided() {
        // Given
        MIMEMessageConverter sut = new MIMEMessageConverter();
        TextBody expected = new BasicBodyFactory().textBody("", StandardCharsets.UTF_8);

        CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("")
                .build();

        // When
        Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of());

        // Then
        assertThat(result.getBody()).isEqualToComparingOnlyGivenFields(expected, "content", "charset");
        assertThat(result.getMimeType()).isEqualTo("text/plain");
    }

    @Nested
    class WithAttachments {

        @Test
        void convertToMimeShouldAddAttachment() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            TextBody expectedBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid(expectedCID)
                    .type(expectedMimeType)
                    .isInline(true)
                    .size(text.getBytes().length)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(expectedMimeType)
                    .build());

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo(expectedMimeType);
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                    assertThat(part.getContentTransferEncoding()).isEqualTo("base64");
                });
        }

        @Test
        void convertToMimeShouldPreservePartCharset() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "text/calendar; charset=\"iso-8859-1\"";
            String text = "123456";
            TextBody expectedBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid(expectedCID)
                    .isInline(true)
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(expectedMimeType)
                    .build());

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo("text/calendar");
                    assertThat(part.getCharset()).isEqualTo("iso-8859-1");
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                    assertThat(part.getContentTransferEncoding()).isEqualTo("base64");
                });
        }

        @Test
        void convertToMimeShouldAddAttachmentAndMultipartAlternativeWhenOneAttachementAndTextAndHtmlBody() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxId("dead-bada55")
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .textBody("Hello all!")
                .htmlBody("Hello <b>all<b>!")
                .build();
            TextBody expectedTextBody = new BasicBodyFactory().textBody("Hello all!".getBytes(), StandardCharsets.UTF_8);
            TextBody expectedHtmlBody = new BasicBodyFactory().textBody("Hello <b>all<b>!".getBytes(), StandardCharsets.UTF_8);

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            TextBody expectedAttachmentBody = new BasicBodyFactory().textBody(text.getBytes(), StandardCharsets.UTF_8);

            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid(expectedCID)
                    .isInline(true)
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(expectedMimeType)
                    .build());

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .satisfies(Throwing.consumer(part -> {
                    assertThat(part.getBody()).isInstanceOf(Multipart.class);
                    assertThat(part.isMultipart()).isTrue();
                    assertThat(part.getMimeType()).isEqualTo("multipart/alternative");
                    assertThat(((Multipart)part.getBody()).getBodyParts()).hasSize(2);
                    SingleBody textPart = (SingleBody) ((Multipart)part.getBody()).getBodyParts().get(0).getBody();
                    SingleBody htmlPart = (SingleBody) ((Multipart)part.getBody()).getBodyParts().get(1).getBody();
                    assertThat(textPart.getInputStream()).hasBinaryContent("Hello all!".getBytes());
                    assertThat(htmlPart.getInputStream()).hasBinaryContent("Hello <b>all<b>!".getBytes());
                }), Index.atIndex(0))
                .satisfies(part -> {
                    assertThat(part.getBody()).isEqualToComparingOnlyGivenFields(expectedAttachmentBody, "content");
                    assertThat(part.getDispositionType()).isEqualTo("inline");
                    assertThat(part.getMimeType()).isEqualTo(expectedMimeType);
                    assertThat(part.getHeader().getField("Content-ID").getBody()).isEqualTo(expectedCID);
                }, Index.atIndex(1));
        }

        @Test
        void convertShouldEncodeWhenNonASCIICharacters() {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                    .mailboxId("dead-bada55")
                    .subject("subject")
                    .from(DraftEmailer.builder().name("sender").build())
                    .htmlBody("Some non-ASCII characters: áÄÎßÿ")
                    .build();

            // When
            ImmutableList<Attachment.WithBlob> attachments = ImmutableList.of();
            byte[] convert = sut.convert(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), attachments);

            String expectedEncodedContent = "Some non-ASCII characters: =C3=A1=C3=84=C3=8E=C3=9F=C3=BF";

            // Then
            String actual = new String(convert, StandardCharsets.US_ASCII);
            assertThat(actual).contains(expectedEncodedContent);
        }

        @Test
        void convertToMimeShouldAddAttachmentAndContainsIndicationAboutTheWayToEncodeFilenamesAttachmentInTheInputStreamWhenSending() throws Exception {
            // Given
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String expectedCID = "cid";
            String expectedMimeType = "image/png";
            String text = "123456";
            String name = "ديناصور.png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .name(name)
                    .blobId(BlobId.of(blodId.getId()))
                    .cid(expectedCID)
                    .isInline(true)
                    .size(text.getBytes().length)
                    .type(expectedMimeType)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(expectedMimeType)
                    .build());

            // When
            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .extracting(entity -> (Multipart) entity.getBody())
                .flatExtracting(Multipart::getBodyParts)
                .anySatisfy(part -> assertThat(getNameParameterValue(part)).isEqualTo(name));
        }


        @Test
        void convertToMimeShouldHaveMixedMultipart() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            String type = "image/png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("ديناصور.png")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));

            assertThat(result.getBody()).isInstanceOf(Multipart.class);
            Multipart typedResult = (Multipart)result.getBody();
            assertThat(typedResult.getSubType()).isEqualTo("mixed");
        }

        @Test
        void convertToMimeShouldNotHaveInnerMultipartWhenNoInline() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            String type = "image/png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("ديناصور.png")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .noneMatch(Entity::isMultipart);
        }

        @Test
        void convertToMimeShouldHaveChildrenAttachmentParts() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            String type = "image/png";

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("ديناصور.png")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .extracting(Entity::getDispositionType)
                .anySatisfy(contentDisposition -> assertThat(contentDisposition).isEqualTo("attachment"));
        }

        @Test
        void convertToMimeShouldNotThrowWhenNameInContentTypeFieldAndAttachmentMetadata() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "123456";
            String type = "image/png; name=abc.png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("fgh.png")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            assertThatCode(() -> sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment)))
                .doesNotThrowAnyException();
        }

        @Test
        void attachmentNameShouldBeOverriddenWhenSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            String text = "123456";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            String type = "image/png; name=abc.png; charset=\"iso-8859-1\"";

            Attachment attachment = Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("fgh.png")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build();

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png; charset=iso-8859-1; name=\"=?US-ASCII?Q?fgh.png?=\"");
        }

        @Test
        void nameShouldBeAddedToContentTypeWhenSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            String text = "123456";
            String type = "image/png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            Attachment attachment = Attachment.builder()
                .blobId(BlobId.of(blodId.getId()))
                .cid("cid")
                .name("fgh.png")
                .isInline(false)
                .size(text.getBytes().length)
                .type(type)
                .build();

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo("image/png; name=\"=?US-ASCII?Q?fgh.png?=\"");
        }

        @Test
        void attachmentNameShouldBePreservedWhenNameNotSpecified() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            String text = "123456";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            String type = "image/png; name=abc.png";

            Attachment attachment = Attachment.builder()
                .blobId(BlobId.of(blodId.getId()))
                .cid("cid")
                .isInline(false)
                .size(text.getBytes().length)
                .type(type)
                .build();

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo(type);
        }

        @Test
        void attachmentNameShouldBeUnspecifiedWhenNone() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            String text = "123456";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            String type = "image/png";
            Attachment attachment = Attachment.builder()
                .blobId(BlobId.of(blodId.getId()))
                .cid("cid")
                .isInline(false)
                .size(text.getBytes().length)
                .type(type)
                .build();

            assertThat(sut.contentTypeField(attachment).getBody())
                .isEqualTo(type);
        }

        @Test
        void convertToMimeShouldHaveChildMultipartWhenOnlyInline() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String name = "ديناصور.png";
            String text = "123456";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");
            String type = "image/png";

            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name(name)
                    .isInline(true)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(1)
                .allMatch(Entity::isMultipart)
                .extracting(entity -> (Multipart) entity.getBody())
                .extracting(Multipart::getSubType)
                .allSatisfy(subType -> assertThat(subType).isEqualTo("related"));
        }

        @Test
        void convertToMimeShouldHaveChildMultipartWhenBothInlinesAndAttachments() throws Exception {
            MIMEMessageConverter sut = new MIMEMessageConverter();

            CreationMessage testMessage = CreationMessage.builder()
                .mailboxIds(ImmutableList.of("dead-bada55"))
                .subject("subject")
                .from(DraftEmailer.builder().name("sender").build())
                .htmlBody("Hello <b>all<b>!")
                .build();

            String text = "inline data";
            String type = "image/png";
            StringBackedAttachmentId blodId = StringBackedAttachmentId.from("blodId");

            Attachment.WithBlob inline = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid")
                    .name("ديناصور.png")
                    .isInline(true)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            String text2 = "attachment data";
            final StringBackedAttachmentId blodId2 = StringBackedAttachmentId.from("blodId2");
            Attachment.WithBlob attachment = new Attachment.WithBlob(
                Attachment.builder()
                    .blobId(BlobId.of(blodId.getId()))
                    .cid("cid2")
                    .name("att.pdf")
                    .isInline(false)
                    .size(text.getBytes().length)
                    .type(type)
                    .build(),
                Blob.builder()
                    .payload(() -> new ByteArrayInputStream(text.getBytes()))
                    .id(BlobId.of(blodId.getId()))
                    .size(text.getBytes().length)
                    .contentType(type)
                    .build());

            Message result = sut.convertToMime(new ValueWithId.CreationMessageEntry(
                    CreationMessageId.of("user|mailbox|1"), testMessage), ImmutableList.of(inline, attachment));
            Multipart typedResult = (Multipart)result.getBody();

            assertThat(typedResult.getBodyParts())
                .hasSize(2)
                .satisfies(part -> {
                    Multipart multipartRelated = (Multipart) part.getBody();
                    assertThat(multipartRelated.getSubType()).isEqualTo("related");
                    assertThat(multipartRelated.getBodyParts())
                        .extracting(Entity::getDispositionType)
                        .contains("inline");
                }, Index.atIndex(0))
                .satisfies(part -> {
                    assertThat(part.getDispositionType()).isEqualTo("attachment");
                }, Index.atIndex(1));
        }

        private String getNameParameterValue(Entity attachmentPart) {
            return ((ContentTypeField) attachmentPart.getHeader().getField("Content-Type")).getParameter("name");
        }
    }
}
