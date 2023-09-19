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

package org.apache.james.spamassassin;


import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_APPENDED;
import static org.apache.james.mailbox.events.MailboxEvents.Added.IS_DELIVERY;
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

import org.apache.james.core.Username;
import org.apache.james.events.EventListener;
import org.apache.james.events.Group;
import org.apache.james.mailbox.DefaultMailboxes;
import org.apache.james.mailbox.MailboxSession;
import org.apache.james.mailbox.MailboxSessionUtil;
import org.apache.james.mailbox.events.MailboxEvents.Added;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.manager.InMemoryIntegrationResources;
import org.apache.james.mailbox.model.ByteContent;
import org.apache.james.mailbox.model.Mailbox;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMetaData;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.model.ThreadId;
import org.apache.james.mailbox.model.UidValidity;
import org.apache.james.mailbox.store.MailboxSessionMapperFactory;
import org.apache.james.mailbox.store.StoreMailboxManager;
import org.apache.james.mailbox.store.SystemMailboxesProviderImpl;
import org.apache.james.mailbox.store.event.EventFactory;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SpamAssassinListenerTest {
    static final Username USER = Username.of("user");
    static final MailboxSession MAILBOX_SESSION = MailboxSessionUtil.create(USER);
    static final UidValidity UID_VALIDITY = UidValidity.of(43);
    static final TestMessageId MESSAGE_ID = TestMessageId.of(45);
    static final ThreadId THREAD_ID = ThreadId.fromBaseMessageId(MESSAGE_ID);

    SpamAssassinLearner spamAssassinLearner;
    SpamAssassinListener listener;
    Mailbox inbox;
    Mailbox mailbox1;
    MailboxId mailboxId1;
    MailboxId mailboxId2;
    MailboxId spamMailboxId;
    MailboxId spamCapitalMailboxId;
    MailboxId trashMailboxId;
    MailboxSessionMapperFactory mapperFactory;
    Mailbox mailbox2;

    @BeforeEach
    void setup() throws Exception {
        StoreMailboxManager mailboxManager = spy(InMemoryIntegrationResources.defaultResources().getMailboxManager());
        SystemMailboxesProviderImpl systemMailboxesProvider = new SystemMailboxesProviderImpl(mailboxManager);
        when(mailboxManager.createSystemSession(USER))
            .thenReturn(MAILBOX_SESSION);

        spamAssassinLearner = mock(SpamAssassinLearner.class);
        mapperFactory = mailboxManager.getMapperFactory();
        MailboxMapper mailboxMapper = mapperFactory.createMailboxMapper(MAILBOX_SESSION);
        inbox = mailboxMapper.create(MailboxPath.forUser(USER, DefaultMailboxes.INBOX), UID_VALIDITY).block();
        mailbox1 = mailboxMapper.create(MailboxPath.forUser(USER, "mailbox1"), UID_VALIDITY).block();
        mailbox2 = mailboxMapper.create(MailboxPath.forUser(USER, "mailbox2"), UID_VALIDITY).block();
        mailboxId1 = mailbox1.getMailboxId();
        mailboxId2 = mailbox2.getMailboxId();
        spamMailboxId = mailboxMapper.create(MailboxPath.forUser(USER, "Spam"), UID_VALIDITY).block().getMailboxId();
        spamCapitalMailboxId = mailboxMapper.create(MailboxPath.forUser(USER, "SPAM"), UID_VALIDITY).block().getMailboxId();
        trashMailboxId = mailboxMapper.create(MailboxPath.forUser(USER, "Trash"), UID_VALIDITY).block().getMailboxId();

        listener = new SpamAssassinListener(spamAssassinLearner, systemMailboxesProvider, mailboxManager, mapperFactory, EventListener.ExecutionMode.SYNCHRONOUS);
    }

    @Test
    void deserializeSpamAssassinListenerGroup() throws Exception {
        assertThat(Group.deserialize("org.apache.james.spamassassin.SpamAssassinListener$SpamAssassinListenerGroup"))
            .isEqualTo(new SpamAssassinListener.SpamAssassinListenerGroup());
    }

    @Test
    void isEventOnSpamMailboxShouldReturnFalseWhenMessageIsMovedToANonSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    void isEventOnSpamMailboxShouldReturnTrueWhenMailboxIsSpam() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    void isEventOnSpamMailboxShouldReturnFalseWhenMailboxIsSpamOtherCase() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamCapitalMailboxId)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    void eventShouldCallSpamAssassinSpamLearningWhenTheEventMatches() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassinLearner).learnSpam(any(), any());
    }

    @Test
    void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedBetweenNonSpamMailboxes() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedOutOfCapitalSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamCapitalMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    void isMessageMovedOutOfSpamMailboxShouldReturnTrueWhenMessageMovedOutOfSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedToTrash() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(trashMailboxId)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    void eventShouldCallSpamAssassinHamLearningWhenTheEventMatches() throws Exception {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId1)
                .build())
            .messageId(MESSAGE_ID)
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassinLearner).learnHam(any(), any());
    }

    @Test
    void eventShouldCallSpamAssassinHamLearningWhenTheMessageIsAddedInInbox() throws Exception {
        SimpleMailboxMessage message = createMessage(inbox);

        Added addedEvent = EventFactory.added()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(inbox)
            .addMetaData(message.metaData())
            .isDelivery(!IS_DELIVERY)
            .isAppended(!IS_APPENDED)
            .build();

        listener.event(addedEvent);

        verify(spamAssassinLearner).learnHam(any(), any());
    }

    @Test
    void eventShouldNotCallSpamAssassinHamLearningWhenTheMessageIsAddedInAMailboxOtherThanInbox() throws Exception {
        SimpleMailboxMessage message = createMessage(mailbox1);

        Added addedEvent = EventFactory.added()
            .randomEventId()
            .mailboxSession(MAILBOX_SESSION)
            .mailbox(mailbox1)
            .addMetaData(message.metaData())
            .isDelivery(!IS_DELIVERY)
            .isAppended(!IS_APPENDED)
            .build();

        listener.event(addedEvent);

        verifyNoMoreInteractions(spamAssassinLearner);
    }

    private SimpleMailboxMessage createMessage(Mailbox mailbox) throws MailboxException {
        int size = 45;
        int bodyStartOctet = 25;
        byte[] content = "Subject: test\r\n\r\nBody\r\n".getBytes(StandardCharsets.UTF_8);
        SimpleMailboxMessage message = new SimpleMailboxMessage(MESSAGE_ID, THREAD_ID, new Date(),
            size, bodyStartOctet, new ByteContent(content), new Flags(), new PropertyBuilder().build(),
            mailbox.getMailboxId());
        MessageMetaData messageMetaData = mapperFactory.createMessageMapper(null).add(mailbox, message);
        message.setUid(messageMetaData.getUid());
        return message;
    }
}