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
import java.util.Calendar;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.assertj.core.internal.FieldByFieldComparator;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;

public class SimpleMailboxMessageTest {
    private static final Charset MESSAGE_CHARSET = Charsets.UTF_8;
    private static final String MESSAGE_CONTENT = "Simple message content without special characters";
    private static final String MESSAGE_CONTENT_SPECIAL_CHAR = "Simple message content with special characters: \"'(§è!çà$*`";
    public static final TestId TEST_ID = TestId.of(1L);
    public static final int BODY_START_OCTET = 0;
    private SimpleMailboxMessage MESSAGE;
    private SimpleMailboxMessage MESSAGE_SPECIAL_CHAR;

    @Before
    public void setUp() {
        MESSAGE = buildMessage(MESSAGE_CONTENT);
        MESSAGE_SPECIAL_CHAR = buildMessage(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    public void testSize() {
        assertThat(MESSAGE.getFullContentOctets()).isEqualTo(MESSAGE_CONTENT.length());
    }

    @Test
    public void testInputStreamSize() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(MESSAGE.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET).length);
        } finally {
            byteArrayOutputStream.close();
        }
    }

    @Test
    public void testInputStreamSizeSpecialCharacters() throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        try {
            byteArrayOutputStream.write(MESSAGE_SPECIAL_CHAR.getFullContent());
            assertThat(byteArrayOutputStream.size()).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR.getBytes(MESSAGE_CHARSET).length);
        } finally {
            byteArrayOutputStream.close();
        }
    }

    @Test
    public void testFullContent() throws IOException {
        assertThat(new String(IOUtils.toByteArray(MESSAGE.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT);
        assertThat(new String(IOUtils.toByteArray(MESSAGE_SPECIAL_CHAR.getFullContent()), MESSAGE_CHARSET)).isEqualTo(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    public void simpleMessageShouldReturnTheSameUserFlagsThatThoseProvided() {
        MESSAGE.setFlags(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("mozzarela", "parmesan", "coppa", "limonchello").build());
        assertThat(MESSAGE.createUserFlags()).containsOnly("mozzarela", "parmesan", "coppa", "limonchello");
    }

    @Test
    public void copyShouldReturnFieldByFieldEqualsObject() throws MailboxException {
        long textualLineCount = 42L;
        String text = "text";
        String plain = "plain";
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setTextualLineCount(textualLineCount);
        propertyBuilder.setMediaType(text);
        propertyBuilder.setSubType(plain);
        SimpleMailboxMessage original = new SimpleMailboxMessage(new DefaultMessageId(), new Date(),
            MESSAGE_CONTENT.length(),
            BODY_START_OCTET,
            new SharedByteArrayInputStream(MESSAGE_CONTENT.getBytes(MESSAGE_CHARSET)),
            new Flags(),
            propertyBuilder,
            TEST_ID);

        SimpleMailboxMessage copy = SimpleMailboxMessage.copy(TestId.of(1337), original);

        assertThat((Object)copy).isEqualToIgnoringGivenFields(original, "message", "mailboxId").isNotSameAs(original);
        assertThat(copy.getMessage()).usingComparator(new FieldByFieldComparator()).isEqualTo(original.getMessage());
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

}
