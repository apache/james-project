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

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.ModSeq;
import org.apache.james.mailbox.model.Attachment;
import org.apache.james.mailbox.model.MessageAttachment;
import org.apache.james.mailbox.model.MessageId;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.google.common.collect.ImmutableList;

public class SimpleMailboxMessageTest {
    private static final Charset MESSAGE_CHARSET = StandardCharsets.UTF_8;
    private static final String MESSAGE_CONTENT = "Simple message content without special characters";
    public static final SharedByteArrayInputStream CONTENT_STREAM = new SharedByteArrayInputStream(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET));
    private static final String MESSAGE_CONTENT_SPECIAL_CHAR = "Simple message content with special characters: \"'(§è!çà$*`";
    public static final TestId TEST_ID = TestId.of(1L);
    public static final int BODY_START_OCTET = 0;
    public static final MessageId MESSAGE_ID = new TestMessageId.Factory().generate();
    public static final int SIZE = 1000;
    private SimpleMailboxMessage message;
    private SimpleMailboxMessage messageSpecialChar;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Rule
    public final JUnitSoftAssertions soft = new JUnitSoftAssertions();

    @Before
    public void setUp() {
        this.message = buildMessage(MESSAGE_CONTENT);
        this.messageSpecialChar = buildMessage(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    public void testSize() {
        assertThat(message.getFullContentOctets()).isEqualTo(MESSAGE_CONTENT.length());
    }

    @Test
    public void testInputStreamSize() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byteArrayOutputStream.write(message.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET).length);
        }
    }

    @Test
    public void testInputStreamSizeSpecialCharacters() throws IOException {
        try (ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream()) {
            byteArrayOutputStream.write(messageSpecialChar.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR.getBytes(MESSAGE_CHARSET).length);
        }
    }

    @Test
    public void testFullContent() throws IOException {
        assertThat(new String(IOUtils.toByteArray(message.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT);
        assertThat(new String(IOUtils.toByteArray(messageSpecialChar.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    public void simpleMessageShouldReturnTheSameUserFlagsThatThoseProvided() {
        message.setFlags(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("mozzarela", "parmesan", "coppa", "limonchello").build());
        assertThat(message.createUserFlags()).containsOnly("mozzarela", "parmesan", "coppa", "limonchello");
    }

    @Test
    public void copyShouldReturnFieldByFieldEqualsObject() throws Exception {
        long textualLineCount = 42L;
        String text = "text";
        String plain = "plain";
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        propertyBuilder.setMediaType(text);
        propertyBuilder.setSubType(plain);
        SimpleMailboxMessage original = new SimpleMailboxMessage(new TestMessageId.Factory().generate(), new Date(),
            MESSAGE_CONTENT.length(),
            BODY_START_OCTET,
            CONTENT_STREAM,
            new Flags(),
            propertyBuilder,
            TEST_ID);

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

    }

    private static SimpleMailboxMessage buildMessage(String content) {
        return new SimpleMailboxMessage(new DefaultMessageId(), Calendar.getInstance().getTime(),
            content.length(), BODY_START_OCTET, new SharedByteArrayInputStream(
                    content.getBytes(MESSAGE_CHARSET)), new Flags(),
            new PropertyBuilder(), TEST_ID);
    }

    @Test
    public void sizeShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        SimpleMailboxMessage.builder()
            .size(-1);
    }

    @Test
    public void bodyStartOctetShouldThrowWhenNegative() {
        expectedException.expect(IllegalArgumentException.class);

        SimpleMailboxMessage.builder()
            .bodyStartOctet(-1);
    }

    @Test
    public void buildShouldWorkWithMinimalContent() {
        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }



    @Test
    public void buildShouldCreateAMessageWithAllFields() throws IOException {
        Date internalDate = new Date();
        Flags flags = new Flags();
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        ModSeq modseq = ModSeq.of(145);
        MessageUid uid = MessageUid.of(45);
        MessageAttachment messageAttachment = MessageAttachment.builder()
            .attachment(Attachment.builder()
                .bytes("".getBytes(StandardCharsets.UTF_8))
                .type("type")
                .build())
            .name("name")
            .isInline(false)
            .build();
        SimpleMailboxMessage message = SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .modseq(modseq)
            .uid(uid)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(flags)
            .propertyBuilder(propertyBuilder)
            .addAttachments(ImmutableList.of(messageAttachment))
            .build();

        soft.assertThat(message.getMessageId()).isEqualTo(MESSAGE_ID);
        soft.assertThat(message.getMailboxId()).isEqualTo(TEST_ID);
        soft.assertThat(message.getInternalDate()).isEqualTo(internalDate);
        soft.assertThat(message.getHeaderOctets()).isEqualTo(BODY_START_OCTET);
        soft.assertThat(message.getFullContentOctets()).isEqualTo(SIZE);
        soft.assertThat(IOUtils.toString(message.getFullContent(), StandardCharsets.UTF_8)).isEqualTo(MESSAGE_CONTENT);
        soft.assertThat(message.createFlags()).isEqualTo(flags);
        soft.assertThat(message.getProperties()).isEqualTo(propertyBuilder.toProperties());
        soft.assertThat(message.getUid()).isEqualTo(uid);
        soft.assertThat(message.getModSeq()).isEqualTo(modseq);
        soft.assertThat(message.getAttachments()).containsOnly(messageAttachment);
    }

    @Test
    public void buildShouldThrowOnMissingMessageId() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingMailboxId() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingInternalDate() {
        expectedException.expect(NullPointerException.class);

        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingBodyStartOctets() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingSize() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingContent() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .flags(new Flags())
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingFlags() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .propertyBuilder(new PropertyBuilder())
            .build();
    }

    @Test
    public void buildShouldThrowOnMissingProperties() {
        expectedException.expect(NullPointerException.class);

        Date internalDate = new Date();
        SimpleMailboxMessage.builder()
            .messageId(MESSAGE_ID)
            .mailboxId(TEST_ID)
            .internalDate(internalDate)
            .bodyStartOctet(BODY_START_OCTET)
            .size(SIZE)
            .content(CONTENT_STREAM)
            .flags(new Flags())
            .build();
    }

}
