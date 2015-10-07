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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Calendar;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.FlagsBuilder;
import org.junit.Before;
import org.junit.Test;

public class SimpleMessageTest {
    private static final Charset MESSAGE_CHARSET = Charset.forName("UTF-8");
    private static final String MESSAGE_CONTENT = "Simple message content without special characters";
    private static final String MESSAGE_CONTENT_SPECIAL_CHAR = "Simple message content with special characters: \"'(§è!çà$*`";
    private SimpleMessage<TestId> MESSAGE;
    private SimpleMessage<TestId> MESSAGE_SPECIAL_CHAR;

    @Before
    public void setUp() {
        MESSAGE = buildMessage(MESSAGE_CONTENT);
        MESSAGE_SPECIAL_CHAR = buildMessage(MESSAGE_CONTENT_SPECIAL_CHAR);
    }

    @Test
    public void testSize() {
        assertEquals(MESSAGE_CONTENT.length(), MESSAGE.getFullContentOctets());
    }

    @Test
    public void testInputStreamSize() throws IOException {
        InputStream is = MESSAGE.getFullContent();
        int byteCount = 0;
        while (is.read() != -1) {
            byteCount++;
        }
        assertEquals(MESSAGE_CONTENT.length(), byteCount);
    }

    @Test
    public void testInputStreamSizeSpecialCharacters() throws IOException {
        InputStream is = MESSAGE_SPECIAL_CHAR.getFullContent();
        int byteCount = 0;
        while (is.read() != -1) {
            byteCount++;
        }
        assertFalse(MESSAGE_CONTENT_SPECIAL_CHAR.length() == byteCount);
    }

    @Test
    public void testFullContent() throws IOException {
        assertEquals(MESSAGE_CONTENT,
                new String(IOUtils.toByteArray(MESSAGE.getFullContent()),MESSAGE_CHARSET));
        assertEquals(MESSAGE_CONTENT_SPECIAL_CHAR,
                new String(IOUtils.toByteArray(MESSAGE_SPECIAL_CHAR.getFullContent()),MESSAGE_CHARSET));
    }

    @Test
    public void simpleMessageShouldReturnTheSameUserFlagsThatThoseProvided() {
        MESSAGE.setFlags(new FlagsBuilder().add(Flags.Flag.DELETED, Flags.Flag.SEEN).add("mozzarela", "parmesan", "coppa", "limonchello").build());
        assertThat(MESSAGE.createUserFlags()).containsOnly("mozzarela", "parmesan", "coppa", "limonchello");
    }

        private static SimpleMessage<TestId> buildMessage(String content) {
            return new SimpleMessage<TestId>(Calendar.getInstance().getTime(),
                content.length(), 0, new SharedByteArrayInputStream(
                        content.getBytes(MESSAGE_CHARSET)), new Flags(),
                new PropertyBuilder(), TestId.of(1L));
    }

}
