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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import jakarta.mail.Flags;
import jakarta.mail.Flags.Flag;

import org.apache.james.core.Username;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageIdManager;
import org.apache.james.mailbox.MessageManager;
import org.apache.james.mailbox.MessageManager.AppendCommand;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.exception.MailboxRoleNotFoundException;
import org.apache.james.mailbox.inmemory.InMemoryMessageId;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ComposedMessageId;
import org.apache.james.mailbox.model.FetchGroup;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageRange;
import org.apache.james.mailbox.model.MessageResult;
import org.apache.james.mailbox.model.MessageResultIterator;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mime4j.dom.Message;
import org.apache.james.queue.api.MailQueue.MailQueueItem;
import org.apache.mailet.Attribute;
import org.apache.mailet.AttributeValue;
import org.apache.mailet.Mail;
import org.apache.mailet.base.test.FakeMail;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostDequeueDecoratorTest {
    private static final String OUTBOX = DefaultMailboxes.OUTBOX;
    private static final String SENT = DefaultMailboxes.SENT;
    private static final String RAW_USERNAME = "username@domain.tld";
    private static final Username USERNAME = Username.of(RAW_USERNAME);
    private static final MessageUid UID = MessageUid.of(1);
    private static final MailboxPath OUTBOX_MAILBOX_PATH = MailboxPath.forUser(USERNAME, OUTBOX);
    private static final MailboxPath SENT_MAILBOX_PATH = MailboxPath.forUser(USERNAME, SENT);
    private static final Attribute USERNAME_ATTRIBUTE = new Attribute(MailMetadata.MAIL_METADATA_USERNAME_ATTRIBUTE, AttributeValue.of(RAW_USERNAME));

    private StoreMailboxManager mailboxManager;
    private MailQueueItem mockedMailQueueItem;
    private Mail mail;
    private PostDequeueDecorator testee;
    private Message message;

    @Before
    public void init() throws Exception {
        InMemoryIntegrationResources resources = InMemoryIntegrationResources.defaultResources();
        mailboxManager = resources.getMailboxManager();

        mockedMailQueueItem = mock(MailQueueItem.class);
        mail = FakeMail.defaultFakeMail();
        when(mockedMailQueueItem.getMail()).thenReturn(mail);
        testee = new PostDequeueDecorator(mockedMailQueueItem, mailboxManager, new InMemoryMessageId.Factory(),
                resources.getMessageIdManager(), new SystemMailboxesProviderImpl(mailboxManager));

        message = Message.Builder.of()
            .setSubject("test")
            .setBody("testmail", StandardCharsets.UTF_8)
            .build();
    }

    @Test
    public void doneShouldCallDecoratedDone() throws Exception {
        try {
            testee.done(MailQueueItem.CompletionStatus.SUCCESS);
        } catch (Exception e) {
            //Ignore
        }

        verify(mockedMailQueueItem).done(MailQueueItem.CompletionStatus.SUCCESS);
    }

    @Test
    public void doneShouldNotThrowWhenMessageIsNotInOutbox() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(SENT_MAILBOX_PATH, mailboxSession);
        ComposedMessageId sentMessageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(sentMessageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);
    }

    @Test(expected = MailboxRoleNotFoundException.class)
    public void doneShouldThrowWhenSentDoesNotExist() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);
    }

    @Test
    public void doneShouldCopyMailFromOutboxToSentWhenSuccess() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager sentMailbox = mailboxManager.getMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = sentMailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldDeleteMailFromOutboxWhenSuccess() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(0);
    }

    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenSuccessIsFalse() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.RETRY);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenNoMetadataProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(AppendCommand.from(message), mailboxSession);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenUsernameNotProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenMessageIdNotProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(AppendCommand.from(message), mailboxSession);
        mail.setAttribute(USERNAME_ATTRIBUTE);

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldNotMoveMailFromOutboxToSentWhenInvalidMessageIdProvided() throws Exception {
        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession);
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        messageManager.appendMessage(AppendCommand.from(message), mailboxSession);
        mail.setAttribute(USERNAME_ATTRIBUTE);
        mail.setAttribute(messageIdAttribute("invalid"));

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        MessageManager mailbox = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MessageResultIterator resultIterator = mailbox.getMessages(MessageRange.one(UID), FetchGroup.FULL_CONTENT, mailboxSession);
        assertThat(resultIterator).toIterable()
            .hasSize(1);
    }

    @Test
    public void doneShouldCopyMailFromOutboxToSentOnlyOneTimeWhenSuccess() throws Exception {
        MessageIdManager messageIdManager = mock(MessageIdManager.class);
        testee = new PostDequeueDecorator(mockedMailQueueItem, mailboxManager, new InMemoryMessageId.Factory(),
                messageIdManager, new SystemMailboxesProviderImpl(mailboxManager));

        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        MailboxId sentMailboxId = mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession).get();
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        ImmutableList<MessageResult> allMessages = ImmutableList.copyOf(messageManager.getMessages(MessageRange.all(), FetchGroup.MINIMAL, mailboxSession));

        when(messageIdManager.getMessagesReactive(any(), eq(FetchGroup.MINIMAL), any(MailboxSession.class)))
            .thenReturn(Flux.fromIterable(allMessages));
        when(messageIdManager.setInMailboxesReactive(eq(messageId.getMessageId()), eq(ImmutableList.of(sentMailboxId)), any(MailboxSession.class)))
            .thenReturn(Mono.empty());
        when(messageIdManager.setFlagsReactive(eq(new Flags(Flag.SEEN)), eq(MessageManager.FlagsUpdateMode.ADD), eq(messageId.getMessageId()), eq(ImmutableList.of(sentMailboxId)), any(MailboxSession.class)))
            .thenReturn(Mono.empty());

        testee.done(MailQueueItem.CompletionStatus.SUCCESS);
        testee.done(MailQueueItem.CompletionStatus.SUCCESS);

        verify(messageIdManager, times(1)).getMessagesReactive(any(), eq(FetchGroup.MINIMAL), any(MailboxSession.class));
        verify(messageIdManager, times(1)).setInMailboxesReactive(eq(messageId.getMessageId()), eq(ImmutableList.of(sentMailboxId)), any(MailboxSession.class));
        verify(messageIdManager, times(1)).setFlagsReactive(eq(new Flags(Flag.SEEN)), eq(MessageManager.FlagsUpdateMode.ADD), eq(messageId.getMessageId()), eq(ImmutableList.of(sentMailboxId)), any(MailboxSession.class));

        verifyNoMoreInteractions(messageIdManager);
    }

    @Test
    public void doneShouldThrowWhenMailboxException() throws Exception {
        MessageIdManager messageIdManager = mock(MessageIdManager.class);
        testee = new PostDequeueDecorator(mockedMailQueueItem, mailboxManager, new InMemoryMessageId.Factory(),
                messageIdManager, new SystemMailboxesProviderImpl(mailboxManager));

        MailboxSession mailboxSession = mailboxManager.createSystemSession(USERNAME);
        mailboxManager.createMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        mailboxManager.createMailbox(SENT_MAILBOX_PATH, mailboxSession).get();
        MessageManager messageManager = mailboxManager.getMailbox(OUTBOX_MAILBOX_PATH, mailboxSession);
        ComposedMessageId messageId = messageManager.appendMessage(AppendCommand.from(message), mailboxSession).getId();
        mail.setAttribute(messageIdAttribute(messageId.getMessageId().serialize()));
        mail.setAttribute(USERNAME_ATTRIBUTE);

        when(messageIdManager.getMessagesReactive(any(), eq(FetchGroup.MINIMAL), any(MailboxSession.class)))
            .thenReturn(Flux.error(new MailboxException()));

        assertThatThrownBy(() -> testee.done(MailQueueItem.CompletionStatus.SUCCESS))
            .hasCauseInstanceOf(MailboxException.class);
    }

    private Attribute messageIdAttribute(String  value) {
        return new Attribute(MailMetadata.MAIL_METADATA_MESSAGE_ID_ATTRIBUTE, AttributeValue.of(value));
    }
}
