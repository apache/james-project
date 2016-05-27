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
package org.apache.james.jmap.send;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Collection;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.jmap.model.Message;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.jmap.utils.MailboxBasedHtmlTextExtractor;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class MailFactoryTest {

    private MailFactory testee;
    private MailboxMessage mailboxMessage;
    private Message jmapMessage;

    @Before
    public void init() {
        testee = new MailFactory();
        String headers = "From: me@example.com\n"
                + "To: 1@example.com\n"
                + "Cc: 2@example.com, 3@example.com\n"
                + "Bcc: 4@example.com\n"
                + "Subject: news\n";
        String content = headers
                + "Hello! How are you?";
        PropertyBuilder propertyBuilder = new PropertyBuilder();
        propertyBuilder.setMediaType("plain");
        propertyBuilder.setSubType("text");
        propertyBuilder.setTextualLineCount(18L);
        mailboxMessage = new SimpleMailboxMessage(
                new Date(),
                content.length(),
                headers.length(),
                new SharedByteArrayInputStream(content.getBytes()),
                new FlagsBuilder().add(Flags.Flag.SEEN).build(),
                propertyBuilder,
                TestId.of(2));
        HtmlTextExtractor htmlTextExtractor = new MailboxBasedHtmlTextExtractor(new DefaultTextExtractor());
        MessagePreviewGenerator messagePreview = new MessagePreviewGenerator(htmlTextExtractor);
        MessageFactory messageFactory = new MessageFactory(messagePreview) ;
        jmapMessage = messageFactory.fromMailboxMessage(mailboxMessage, ImmutableList.of(), x -> MessageId.of("test|test|" + x));
    }

    @Test(expected=NullPointerException.class)
    public void buildMailShouldThrowWhenNullMailboxMessage() throws Exception {
        testee.build(null, jmapMessage);
    }

    @Test(expected=NullPointerException.class)
    public void buildMailShouldThrowWhenNullJmapMessage() throws Exception {
        testee.build(mailboxMessage, null);
    }

    @Test
    public void buildMailShouldWork() throws Exception {
        String expectedName = jmapMessage.getId().serialize();
        MailAddress expectedSender = new MailAddress("me@example.com");
        Collection<MailAddress> expectedRecipients = ImmutableSet.of(
                new MailAddress("1@example.com"),
                new MailAddress("3@example.com"),
                new MailAddress("2@example.com"),
                new MailAddress("4@example.com"));
        
        Mail actual = testee.build(mailboxMessage, jmapMessage);
        
        assertThat(actual.getName()).isEqualTo(expectedName);
        assertThat(actual.getSender()).isEqualTo(expectedSender);
        assertThat(actual.getRecipients()).containsAll(expectedRecipients);
    }
}
