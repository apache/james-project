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
import org.apache.james.jmap.model.MessageContentExtractor;
import org.apache.james.jmap.model.MessageFactory;
import org.apache.james.jmap.model.MessageFactory.MetaDataWithContent;
import org.apache.james.jmap.model.MessageId;
import org.apache.james.jmap.model.MessagePreviewGenerator;
import org.apache.james.jmap.utils.HtmlTextExtractor;
import org.apache.james.jmap.utils.MailboxBasedHtmlTextExtractor;
import org.apache.james.mailbox.FlagsBuilder;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.store.TestId;
import org.apache.james.mailbox.store.extractor.DefaultTextExtractor;
import org.apache.mailet.Mail;
import org.apache.mailet.MailAddress;
import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

public class MailFactoryTest {

    private MailFactory testee;
    private MetaDataWithContent message;
    private Message jmapMessage;

    @Before
    public void init() throws MailboxException {
        testee = new MailFactory();
        String headers = "From: me@example.com\n"
                + "To: 1@example.com\n"
                + "Cc: 2@example.com, 3@example.com\n"
                + "Bcc: 4@example.com\n"
                + "Subject: news\n";
        String content = headers
                + "Hello! How are you?";
        message = MetaDataWithContent.builder()
                .uid(2)
                .flags(new FlagsBuilder().add(Flags.Flag.SEEN).build())
                .size(content.length())
                .internalDate(new Date())
                .sharedContent(new SharedByteArrayInputStream(content.getBytes(Charsets.UTF_8)))
                .attachments(ImmutableList.of())
                .mailboxId(TestId.of(3))
                .messageId(MessageId.of("test|test|2"))
                .build();
        HtmlTextExtractor htmlTextExtractor = new MailboxBasedHtmlTextExtractor(new DefaultTextExtractor());
        MessagePreviewGenerator messagePreview = new MessagePreviewGenerator(htmlTextExtractor);
        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        MessageFactory messageFactory = new MessageFactory(messagePreview, messageContentExtractor);
        jmapMessage = messageFactory.fromMetaDataWithContent(message);
    }

    @Test(expected=NullPointerException.class)
    public void buildMailShouldThrowWhenNullMailboxMessage() throws Exception {
        testee.build(null, jmapMessage);
    }

    @Test(expected=NullPointerException.class)
    public void buildMailShouldThrowWhenNullJmapMessage() throws Exception {
        testee.build(message, null);
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
        
        Mail actual = testee.build(message, jmapMessage);
        
        assertThat(actual.getName()).isEqualTo(expectedName);
        assertThat(actual.getSender()).isEqualTo(expectedSender);
        assertThat(actual.getRecipients()).containsAll(expectedRecipients);
    }
}
