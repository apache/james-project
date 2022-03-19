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
package org.apache.james.mailbox.store.mail.model.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import jakarta.mail.Flags;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.AttachmentId;
import org.apache.james.mailbox.model.AttachmentMetadata;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.MessageAttachmentMetadata;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

class SimpleMailboxMessageTest {
    static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    static final String MESSAGE_CONTENT = "Simple message content without special characters";
    static final ByteContent CONTENT_STREAM = new ByteContent(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET));
    static final String MESSAGE_CONTENT_SPECIAL_CHAR = "Simple message content with special characters: \"'(§è!çà$*`";
    static final TestId TEST_ID = TestId.of(1L);
    static final int BODY_START_OCTET = 0;
    static final MessageId MESSAGE_ID = new TestMessageId.Factory().generate();
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);
    static final int SIZE = 1000;

    SimpleMailboxMessage message;
    SimpleMailboxMessage messageSpecialChar;

    @BeforeEach
    void setUp() {
        this.message = buildMessage(MESSAGE_CONTENT);
        this.messageSpecialChar = buildMessage(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    void testSize() {
        assertThat(message.getFullContentOctets()).isEqualTo(MESSAGE_CONTENT.length());
    }

    @Test
    void testInputStreamSize() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byteArrayOutputStream.write(message.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET).length);
        }
    }

    @Test
    void testInputStreamSizeSpecialCharacters() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byteArrayOutputStream.write(messageSpecialChar.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR.getBytes(MESSAGE_CHARSET).length);
        }
    }

    @Test
    void testFullContent() throws IOException {
        assertThat(new String(IOUtils.toByteArray(message.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT);
        assertThat(new String(IOUtils.toByteArray(messageSpecialChar.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    void simpleMessageShouldReturnTheSameUserFlagsThatThoseProvided() {
        message.setFlags(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("mozzarela", "parmesan", "coppa", "limonchello").build());
        assertThat(message.createUserFlags()).containsOnly("mozzarela", "parmesan", "coppa", "limonchello");
    }

    @Test
    void copyShouldReturnFieldByFieldEqualsObject() throws Exception {
        long textualLineCount = 42L;
        String text = "text";
        String plain = "plain";
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        propertyBuilder.setMediaType(text);
        propertyBuilder.setSubType(plain);
        MessageId messageId = new TestMessageId.Factory().generate();
        ThreadId threadId = ThreadId.fromBaseMessageId(messageId);
        Optional<Date> saveDate = Optional.of(new Date());
        SimpleMailboxMessage original = new SimpleMailboxMessage(messageId, threadId, new Date(),
            MESSAGE_CONTENT.length(),
            BODY_START_OCTET,
            CONTENT_STREAM,
            new Flags(),
            propertyBuilder.build(),
            TEST_ID,
            List.of(),
            saveDate);

        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(TestId.of(1337), original);

        assertThat((Object)copy).isEqualToIgnoringGivenFields(original, "message", "mailboxId").isNotSameAs(original);
        assertThat(copy.getMessage())
            .isEqualToIgnoringGivenFields(original.getMessage(), "content")
            .isNotSameAs(original.getMessage());
        assertThat(IOUtils.toString(copy.getMessage().getFullContent(), StandardCharsets.UTF_8))
            .isEqualTo(IOUtils.toString(original.getMessage().getFullContent(), StandardCharsets.UTF_8));
        assertThat(SimpleMailboxMessage.copy(TEST_ID, original).getTextualLineCount()).isEqualTo(textualLineCount);
        assertThat(SimpleMailboxMessage.copy(TEST_ID, original).getMediaType()).isEqualTo(text);
        assertThat(SimpleMailboxMessage.copy(TEST_ID, original).getSubType()).isEqualTo(plain);
        assertThat(SimpleMailboxMessage.copy(TEST_ID, original).getSaveDate()).isEqualTo(saveDate);
    }

    private static SimpleMailboxMessage buildMessage(String content) {
        return new SimpleMailboxMessage(new DefaultMessageId(), ThreadId.fromBaseMessageId(new DefaultMessageId()), Calendar.getInstance().getTime(),
            content.length(), BODY_START_OCTET, new ByteContent(
                    content.getBytes(MESSAGE_CHARSET)), new Flags(),
            new PropertyBuilder().build(), TEST_ID);
    }

    @Test
    void sizeShouldThrowWhenNegative() {
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .size(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bodyStartOctetShouldThrowWhenNegative() {
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .bodyStartOctet(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void buildShouldWorkWithMinimalContent() {
        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .threadId(THREAD_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .properties(new PropertyBuilder())
            .build();
    }



    @Test
    void buildShouldCreateAMessageWithAllFields() throws IOException {
        Date internalDate = new Date();
        Flags flags = new Flags();
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        ModSeq modseq = ModSeq.of(145);
        MessageUid uid = MessageUid.of(45);
        MessageAttachmentMetadata messageAttachment = MessageAttachmentMetadata.builder()
            .attachment(AttachmentMetadata.builder()
                .attachmentId(AttachmentId.from("1"))
                .messageId(MESSAGE_ID)
                .type("type")
                .size(485)
                .build())
            .name("name")
            .isInline(false)
            .build();
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .threadId(THREAD_ID)
            .mailboxId(TEST_ID)
            .modseq(modseq)
            .uid(uid)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(flags)
            .properties(propertyBuilder)
            .addAttachments(ImmutableList.of(messageAttachment))
            .build();
        String messageContent = IOUtils.toString(message.getFullContent(), StandardCharsets.UTF_8);

        SoftAssertions.assertSoftly(soft -> {
            soft.assertThat(message.getMessageId()).isEqualTo(MESSAGE_ID);
            soft.assertThat(message.getMailboxId()).isEqualTo(TEST_ID);
            soft.assertThat(message.getInternalDate()).isEqualTo(internalDate);
            soft.assertThat(message.getHeaderOctets()).isEqualTo(BODY_START_OCTET);
            soft.assertThat(message.getFullContentOctets()).isEqualTo(SIZE);
            soft.assertThat(messageContent).isEqualTo(MESSAGE_CONTENT);
            soft.assertThat(message.createFlags()).isEqualTo(flags);
            soft.assertThat(message.getProperties().toProperties()).isEqualTo(propertyBuilder.toProperties());
            soft.assertThat(message.getUid()).isEqualTo(uid);
            soft.assertThat(message.getModSeq()).isEqualTo(modseq);
            soft.assertThat(message.getAttachments()).containsOnly(messageAttachment);
        });
    }

    @Test
    void buildShouldThrowOnMissingMessageId() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingThreadId() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .properties(new PropertyBuilder())
            .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldNotThrowOnMissingSaveDate() {
        assertThatCode(() -> SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .threadId(THREAD_ID)
            .internalDate(new Date())
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .properties(new PropertyBuilder())
            .build())
            .doesNotThrowAnyException();
    }

    @Test
    void buildShouldThrowOnMissingMailboxId() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingInternalDate() {
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingBodyStartOctets() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingSize() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingContent() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .flags(new Flags())
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingFlags() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .properties(new PropertyBuilder())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void buildShouldThrowOnMissingProperties() {
        Date internalDate = new Date();
        assertThatThrownBy(() -> SimpleMailboxMessage.builder()
                .messageId(MESSAGE_ID)
                .threadId(THREAD_ID)
                .mailboxId(TEST_ID)
                .internalDate(internalDate)
                .bodyStartOctet(BODY_START_OCTET)
                .size(SIZE)
                .content(CONTENT_STREAM)
                .flags(new Flags())
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void simpleMessageShouldReturnThreadIdWhichWrapsMessageId() {
        assertThat(message.getThreadId().getBaseMessageId()).isInstanceOf(MessageId.class);
    }

    @Test
    void simpleMessageShouldReturnSaveDateWhenEmpty() {
        MailboxMessage mailboxMessage = SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .threadId(THREAD_ID)
            .internalDate(new Date())
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .properties(new PropertyBuilder())
            .build();

        assertThat(mailboxMessage.getSaveDate()).isEmpty();
    }

    @Test
    void simpleMessageShouldReturnSaveDate() {
        Optional<Date> saveDate = Optional.of(new Date());
        MailboxMessage mailboxMessage = SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .threadId(THREAD_ID)
            .internalDate(new Date())
            .saveDate(saveDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .properties(new PropertyBuilder())
            .build();

        assertThat(mailboxMessage.getSaveDate()).isEqualTo(saveDate);
    }

}
