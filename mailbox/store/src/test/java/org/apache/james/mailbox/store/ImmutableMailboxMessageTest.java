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
package org.apache.james.mailbox.store;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.EnumSet;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.commons.io.IOUtils;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxManager.MessageCapabilities;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.DefaultMessageId;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

public class ImmutableMailboxMessageTest {

    private ImmutableMailboxMessage.Factory messageFactory;

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Before
    public void setup() {
        MailboxManager mailboxManager = mock(MailboxManager.class);
        when(mailboxManager.getSupportedMessageCapabilities()).thenReturn(EnumSet.noneOf(MessageCapabilities.class));

        messageFactory = new ImmutableMailboxMessage.Factory(mailboxManager);
    }

    @Test
    public void fullContentMayBeReadMultipleTimes() throws Exception {
        String fullContent = "Subject: Test1 \n\nBody1\n.\n";
        int bodyStartOctet = 16;
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(new DefaultMessageId(), 
                new Date(), 
                fullContent.length(), 
                bodyStartOctet, 
                new SharedByteArrayInputStream(fullContent.getBytes(StandardCharsets.UTF_8)), 
                new Flags(), 
                new PropertyBuilder(), TestId.of(1));

        ImmutableMailboxMessage message = messageFactory.from(TestId.of(1), simpleMailboxMessage);

        softly.assertThat(IOUtils.toString(message.getFullContent(), StandardCharsets.UTF_8)).isEqualTo(fullContent);
        softly.assertThat(IOUtils.toString(message.getFullContent(), StandardCharsets.UTF_8)).isEqualTo(fullContent);
    }

    @Test
    public void headersMayBeReadMultipleTimes() throws Exception {
        String fullContent = "Subject: Test1 \n\nBody1\n.\n";
        int bodyStartOctet = 16;
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(new DefaultMessageId(), 
                new Date(), 
                fullContent.length(), 
                bodyStartOctet, 
                new SharedByteArrayInputStream(fullContent.getBytes(StandardCharsets.UTF_8)), 
                new Flags(), 
                new PropertyBuilder(), TestId.of(1));

        ImmutableMailboxMessage message = messageFactory.from(TestId.of(1), simpleMailboxMessage);

        String expectedHeaders = "Subject: Test1 \n";
        softly.assertThat(IOUtils.toString(message.getHeaderContent(), StandardCharsets.UTF_8)).isEqualTo(expectedHeaders);
        softly.assertThat(IOUtils.toString(message.getHeaderContent(), StandardCharsets.UTF_8)).isEqualTo(expectedHeaders);
    }

    @Test
    public void bodyMayBeReadMultipleTimes() throws Exception {
        String fullContent = "Subject: Test1 \n\nBody1\n.\n";
        int bodyStartOctet = 16;
        SimpleMailboxMessage simpleMailboxMessage = new SimpleMailboxMessage(new DefaultMessageId(), 
                new Date(), 
                fullContent.length(), 
                bodyStartOctet, 
                new SharedByteArrayInputStream(fullContent.getBytes(StandardCharsets.UTF_8)), 
                new Flags(), 
                new PropertyBuilder(), TestId.of(1));

        ImmutableMailboxMessage message = messageFactory.from(TestId.of(1), simpleMailboxMessage);

        String expectedBody = "\nBody1\n.\n";
        softly.assertThat(IOUtils.toString(message.getBodyContent(), StandardCharsets.UTF_8)).isEqualTo(expectedBody);
        softly.assertThat(IOUtils.toString(message.getBodyContent(), StandardCharsets.UTF_8)).isEqualTo(expectedBody);
    }
}
