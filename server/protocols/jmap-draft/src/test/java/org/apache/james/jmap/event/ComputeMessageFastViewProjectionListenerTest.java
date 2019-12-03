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

package org.apache.james.jmap.event;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import org.apache.james.core.Username;
import org.apache.james.jmap.api.model.Preview;
import org.apache.james.jmap.api.projections.MessageFastViewPrecomputedProperties;
import org.apache.james.jmap.api.projections.MessageFastViewProjection;
import org.apache.james.jmap.draft.model.PreviewDTO;
import org.apache.james.jmap.draft.model.message.view.MessageFullViewFactory;
import org.apache.james.jmap.draft.utils.JsoupHtmlTextExtractor;
import org.apache.james.jmap.memory.projections.MemoryMessageFastViewProjection;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.events.Group;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.store.FakeAuthenticator;
import org.apache.james.mailbox.store.FakeAuthorizator;
import org.apache.james.mailbox.store.SessionProvider;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.util.html.HtmlTextExtractor;
import org.apache.james.util.mime.MessageContentExtractor;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import reactor.core.publisher.Mono;

class ComputeMessageFastViewProjectionListenerTest {
    private static final Username BOB = Username.of("bob");
    private static final Preview PREVIEW = Preview.from("This should be the preview of the message...");
    private static final MailboxPath BOB_INBOX_PATH = MailboxPath.inbox(BOB);
    private static final MailboxPath BOB_OTHER_BOX_PATH = MailboxPath.forUser(BOB, "otherBox");
    private static final MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES = MessageFastViewPrecomputedProperties.builder()
        .preview(PREVIEW)
        .noAttachments()
        .build();
    private static final MessageFastViewPrecomputedProperties MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_EMPTY = MessageFastViewPrecomputedProperties.builder()
        .preview(Preview.from(PreviewDTO.from(Optional.empty()).getValue()))
        .noAttachments()
        .build();

    MessageFastViewProjection messageFastViewProjection;
    MessageFullViewFactory messageFullViewFactory;
    MailboxSession mailboxSession;
    StoreMailboxManager mailboxManager;

    private MessageManager inboxMessageManager;
    private MessageManager otherBoxMessageManager;

    @BeforeEach
    void setup() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();
        MessageIdManager messageIdManager = resources.getMessageIdManager();

        messageFastViewProjection = new MemoryMessageFastViewProjection();

        MessageContentExtractor messageContentExtractor = new MessageContentExtractor();
        HtmlTextExtractor htmlTextExtractor = new JsoupHtmlTextExtractor();

        messageFullViewFactory = new MessageFullViewFactory(resources.getBlobManager(), messageContentExtractor, htmlTextExtractor);

        FakeAuthenticator authenticator = new FakeAuthenticator();
        authenticator.addUser(BOB, "12345");

        SessionProvider sessionProvider = new SessionProvider(authenticator, FakeAuthorizator.defaultReject());

        ComputeMessageFastViewProjectionListener listener = new ComputeMessageFastViewProjectionListener(sessionProvider, messageIdManager,
            messageFastViewProjection, messageFullViewFactory);

        resources.getEventBus().register(listener);

        mailboxSession = MailboxSessionUtil.create(BOB);

        MailboxId inboxId = mailboxManager.createMailbox(BOB_INBOX_PATH, mailboxSession).get();
        inboxMessageManager = mailboxManager.getMailbox(inboxId, mailboxSession);

        MailboxId otherBoxId = mailboxManager.createMailbox(BOB_OTHER_BOX_PATH, mailboxSession).get();
        otherBoxMessageManager = mailboxManager.getMailbox(otherBoxId, mailboxSession);
    }

    @Test
    void deserializeMailboxAnnotationListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.jmap.event.ComputeMessageFastViewProjectionListener$ComputeMessageFastViewProjectionListenerGroup"))
            .isEqualTo(new ComputeMessageFastViewProjectionListener.ComputeMessageFastViewProjectionListenerGroup());
    }

    @Test
    void shouldStorePreviewWhenBodyMessageNotEmpty() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES);
    }

    @Test
    void shouldStoreEmptyPreviewWhenEmptyBodyMessage() throws Exception {
        ComposedMessageId composedId = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession);

        assertThat(Mono.from(messageFastViewProjection.retrieve(composedId.getMessageId())).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_EMPTY);
    }

    @Test
    void shouldStoreMultiplePreviewsWhenMultipleMessagesAdded() throws Exception {
        ComposedMessageId composedId1 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        ComposedMessageId composedId2 = inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(emptyMessage()),
            mailboxSession);

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId1.getMessageId())).block())
                .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES);
            softly.assertThat(Mono.from(messageFastViewProjection.retrieve(composedId2.getMessageId())).block())
                .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES_EMPTY);
        });
    }

    @Test
    void shouldKeepPreviewWhenMovingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.moveMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES);
    }

    @Test
    void shouldKeepPreviewWhenCopyingMessage() throws Exception {
        inboxMessageManager.appendMessage(
            MessageManager.AppendCommand.builder()
                .build(previewMessage()),
            mailboxSession);

        mailboxManager.copyMessages(MessageRange.all(), BOB_INBOX_PATH, BOB_OTHER_BOX_PATH, mailboxSession);

        MessageResult result = otherBoxMessageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession).next();
        assertThat(Mono.from(messageFastViewProjection.retrieve(result.getMessageId())).block())
            .isEqualTo(MESSAGE_FAST_VIEW_PRECOMPUTED_PROPERTIES);
    }

    private Message previewMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Preview message")
            .setBody(PREVIEW.getValue(), StandardCharsets.UTF_8)
            .build();
    }

    private Message emptyMessage() throws Exception {
        return Message.Builder.of()
            .setSubject("Empty message")
            .setBody("", StandardCharsets.UTF_8)
            .build();
    }
}
