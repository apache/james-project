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

package org.apache.james.jmap;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.utils.JsoupHtmlTextExtractor;
import org.apache.james.mailbox.MailboxManager;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.util.ClassLoaderUtils;
import org.apache.james.util.mime.MessageContentExtractor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MessageFastViewProjectionItemFactoryTest {
    public static final Username BOB = Username.of("bob");
    MessageFastViewPrecomputedProperties.Factory testee;
    MailboxManager mailboxManager;
    MailboxSession session;
    MessageManager mailbox;

    @BeforeEach
    void setUp() throws Exception {
        testee = new MessageFastViewPrecomputedProperties.Factory(
            new Preview.Factory(new MessageContentExtractor(), new JsoupHtmlTextExtractor()));
        mailboxManager = InMemoryIntegrationResources.defaultResources().getMailboxManager();
        session = mailboxManager.createSystemSession(BOB);
        MailboxId mailboxId = mailboxManager.createMailbox(MailboxPath.inbox(BOB), session).get();
        mailbox = mailboxManager.getMailbox(mailboxId, session);
    }

    @Test
    void fromShouldReturnEmptyWhenNoBodyPart() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult("header: value\r\n"));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.EMPTY)
                .hasAttachment(false)
                .build());
    }

    @Test
    void fromShouldReturnEmptyWhenEmptyBodyPart() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult("header: value\r\n\r\n"));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.EMPTY)
                .hasAttachment(false)
                .build());
    }

    @Test
    void fromShouldReturnEmptyWhenBlankBodyPart() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult("header: value\r\n\r\n  \r\n  \r\n"));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.EMPTY)
                .hasAttachment(false)
                .build());
    }

    @Test
    void fromShouldReturnSanitizedBodyTextValue() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult("header: value\r\n\r\n  \r\nmessage  \r\n"));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from("message"))
                .hasAttachment(false)
                .build());
    }

    @Test
    void fromShouldExtractHtml() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult(ClassLoaderUtils.getSystemResourceAsString("fullMessage.eml")));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from("blabla bloblo"))
                .hasAttachment()
                .build());
    }

    @Test
    void fromShouldParseAttachmentWhenOnlyAttachment() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult(ClassLoaderUtils.getSystemResourceAsString("oneAttachment.eml")));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.EMPTY)
                .hasAttachment()
                .build());
    }

    @Test
    void fromShouldIngnoreAttachmentsWhenInlined() throws Exception {
        MessageFastViewPrecomputedProperties actual = testee.from(toMessageResult(ClassLoaderUtils.getSystemResourceAsString("inlineAttachment.eml")));

        assertThat(actual)
            .isEqualTo(MessageFastViewPrecomputedProperties.builder()
                .preview(Preview.from("I'm the body!"))
                .hasAttachment(false)
                .build());
    }

    MessageResult toMessageResult(String messageAsString) throws Exception {
        ComposedMessageId composedMessageId = mailbox.appendMessage(MessageManager.AppendCommand.builder()
            .build(messageAsString), session).getId();

        return mailbox.getMessages(MessageRange.one(composedMessageId.getUid()), FetchGroup.FULL_CONTENT, session)
            .next();
    }
}
