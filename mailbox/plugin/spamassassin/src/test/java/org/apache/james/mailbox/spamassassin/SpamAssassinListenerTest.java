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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.Date;

import javax.mail.Flags;
import javax.mail.util.SharedByteArrayInputStream;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.exception.MailboxException;
import org.apache.james.mailbox.inmemory.InMemoryMailboxSessionMapperFactory;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.model.MailboxPath;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestMessageId;
import org.apache.james.mailbox.store.event.MessageMoveEvent;
import org.apache.james.mailbox.store.mail.MailboxMapper;
import org.apache.james.mailbox.store.mail.model.impl.PropertyBuilder;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailbox;
import org.apache.james.mailbox.store.mail.model.impl.SimpleMailboxMessage;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.github.fge.lambdas.Throwing;
import com.google.common.collect.ImmutableMap;

public class SpamAssassinListenerTest {

    public static final String USER = "user";
    public static final MockMailboxSession MAILBOX_SESSION = new MockMailboxSession(USER);
    public static final int UID_VALIDITY = 43;
    private SpamAssassin spamAssassin;
    private SpamAssassinListener listener;
    private MailboxId mailboxId1;
    private MailboxId mailboxId2;
    private MailboxId spamMailboxId;
    private MailboxId spamCapitalMailboxId;
    private MailboxMapper mailboxMapper;

    @Before
    public void setup() throws MailboxException {
        spamAssassin = mock(SpamAssassin.class);
        InMemoryMailboxSessionMapperFactory mapperFactory = new InMemoryMailboxSessionMapperFactory();
        mailboxMapper = mapperFactory.getMailboxMapper(MAILBOX_SESSION);
        mailboxId1 = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "mailbox1"), UID_VALIDITY));
        mailboxId2 = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "mailbox2"), UID_VALIDITY));
        spamMailboxId = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "Spam"), UID_VALIDITY));
        spamCapitalMailboxId = mailboxMapper.save(new SimpleMailbox(MailboxPath.forUser(USER, "SPAM"), UID_VALIDITY));

        listener = new SpamAssassinListener(spamAssassin, mapperFactory);
    }

    @After
    public void tearDown() throws MailboxException {
        mailboxMapper.list()
            .forEach(Throwing.consumer(mailboxMapper::delete));
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMessageIsMovedToANonSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId2)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnTrueWhenMailboxIsSpam() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId1)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    public void isEventOnSpamMailboxShouldReturnFalseWhenMailboxIsSpamOtherCase() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamCapitalMailboxId)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId1)))
            .build();

        assertThat(listener.isMessageMovedToSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void eventShouldCallSpamAssassinSpamLearningWhenTheEventMatches() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(spamMailboxId)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId1)))
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassin).learnSpam(any(), any());
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedBetweenNonSpamMailboxes() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(mailboxId1)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnFalseWhenMessageMovedOutOfCapitalSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamCapitalMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isFalse();
    }

    @Test
    public void isMessageMovedOutOfSpamMailboxShouldReturnTrueWhenMessageMovedOutOfSpamMailbox() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId2)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId2)))
            .build();

        assertThat(listener.isMessageMovedOutOfSpamMailbox(messageMoveEvent)).isTrue();
    }

    @Test
    public void eventShouldCallSpamAssassinHamLearningWhenTheEventMatches() {
        MessageMoveEvent messageMoveEvent = MessageMoveEvent.builder()
            .session(MAILBOX_SESSION)
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(spamMailboxId)
                .targetMailboxIds(mailboxId1)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(45),
                createMessage(mailboxId1)))
            .build();

        listener.event(messageMoveEvent);

        verify(spamAssassin).learnHam(any(), any());
    }

    private SimpleMailboxMessage createMessage(MailboxId mailboxId) {
        int size = 45;
        int bodyStartOctet = 25;
        byte[] content = "Subject: test\r\n\r\nBody\r\n".getBytes(StandardCharsets.UTF_8);
        return new SimpleMailboxMessage(TestMessageId.of(58), new Date(),
            size, bodyStartOctet, new SharedByteArrayInputStream(content), new Flags(), new PropertyBuilder(),
            mailboxId);
    }
}
