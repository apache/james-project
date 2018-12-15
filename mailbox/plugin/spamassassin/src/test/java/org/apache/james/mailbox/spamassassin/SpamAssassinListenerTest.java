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
package org.apache.james.mailbox.spamassassin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.acl.SimpleGroupMembershipResolver;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.SimpleMessageMetaData;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.event.MessageMoveEvent;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.Mailbox;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;

public class SpamAssassinListenerTest {

    public static final String USER = "user";
    private static final MailboxSession MAILBOX_SESSION = MailboxSession.create(USER);
    private static final int UID_VALIDITY = 43;
    private static final MessageUid UID = MessageUid.of(45);

    private SpamAssassin spamAssassin;
    private SpamAssassinListener listener;
    private SimpleMailbox inbox;
    private SimpleMailbox mailbox1;
    private MailboxId mailboxId1;
    private MailboxId mailboxId2;
    private MailboxId spamMailboxId;
    private MailboxId spamCapitalMailboxId;
    private MailboxId trashMailboxId;
    private MailboxSessionMapperFactory mapperFactory;
    private SimpleMailbox mailbox2;

    @Before
    public void setup() throws Exception {
        StoreMailboxManager mailboxManager = spy(new InMemoryIntegrationResources().createMailboxManager(new SimpleGroupMembershipResolver()));
        SystemMailboxesProviderImpl systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);
        when(mailboxManager.createSystemSession(USER))
            .thenReturn(MAILBOX_SESSION);

        spamAssassin = mock(SpamAssassin.class);
        mapperFactory = mailboxManager.getMapperFactory();
        MailboxMapper mailboxMapper = mapperFactory.createMailboxMapper(MAILBOX_SESSION);
        inbox = new SimpleMailbox(MailboxPath.forUser(USER, DefaultMailboxes.INBOX), UID_VALIDITY);
        mailbox1 = new SimpleMailbox(MailboxPath.forUser(USER, "mailbox1"), UID_VALIDITY);
        mailbox2 = new SimpleMailbox(MailboxPath.forUser(USER, "mailbox2"), UID_VALIDITY);
        mailboxMapper.save(inbox);
        mailboxId1 = mailboxMapper.save(mailbox1);
        mailboxId2 = mailboxMapper.save(mailbox2);
        spamMailboxId = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "Spam"), UID_VALIDITY));
        spamCapitalMailboxId = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "SPAM"), UID_VALIDITY));
        trashMailboxId = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "Trash"), UID_VALIDITY));

        listener = new SpamAssassinListener(spamAssassin, systemMailboxesProvider, mailboxManager, mapperFactory, MailboxListener.ExecutionMode.SYNCHRONOUS);
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMessageIsMovedToANonSpamMailbox() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox2)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnTrueWhenMailboxIsSpam() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox1)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMailboxIsSpamOtherCase() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamCapitalMailboxId)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox1)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void eventShouldCallSpamAssassinSpamLearningWhenTheEventMatches() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox1)))
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassin).learnSpam(any(), any());
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedBetweenNonSpamMailboxes() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedOutOfCapitalSpamMailbox() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamCapitalMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnTrueWhenMessageMovedOutOfSpamMailbox() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedToTrash() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(trashMailboxId)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void eventShouldCallSpamAssassinHamLearningWhenTheEventMatches() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId1)
                .build())
            .messages(ImmutableMap.of(UID,
                createMessage(mailbox1)))
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassin).learnHam(any(), any());
    }

    @Test
    public void eventShouldCallSpamAssassinHamLearningWhenTheMessageIsAddedInInbox() throws Exception {
        SimpleMailboxMessage message = createMessage(inbox);

        ImmutableSortedMap<MessageUid, MessageMetaData> sortedMap = ImmutableSortedMap.of(UID, new SimpleMessageMetaData(message));
        MailboxListener.Added addedEvent = new EventFactory().added(
                MAILBOX_SESSION, sortedMap, inbox);

        listener.event(addedEvent);

        verify(spamAssassin).learnHam(any(), any());
    }

    @Test
    public void eventShouldNotCallSpamAssassinHamLearningWhenTheMessageIsAddedInAMailboxOtherThanInbox() throws Exception {
        SimpleMailboxMessage message = createMessage(mailbox1);

        MailboxListener.Added addedEvent = new EventFactory().added(
            MAILBOX_SESSION,
            ImmutableSortedMap.of(UID, new SimpleMessageMetaData(message)),
            mailbox1);

        listener.event(addedEvent);

        verifyNoMoreInteractions(spamAssassin);
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox) throws MailboxException {
        int size = 45;
        int bodyStartOctet = 25;
        byte[] content = "Subject: test\r\n\r\nBody\r\n".getBytes(StandardCharsets.UTF_8);
        SimpleMailboxMessage message = new SimpleMailboxMessage(TestMessageId.of(58), new Date(),
            size, bodyStartOctet, new SharedByteArrayInputStream(content), new Flags(), new PropertyBuilder(),
            mailbox.getMailboxId());
        MessageMetaData messageMetaData = mapperFactory.createMessageMapper(null).add(mailbox, message);
        message.setUid(messageMetaData.getUid());
        return message;
    }
}
