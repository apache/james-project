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
package org.apache.james.mailbox.store.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.util.Map;

import org.apache.james.mailbox.MessageUid;
import org.apache.james.mailbox.mock.MockMailboxSession;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.store.mail.model.MailboxMessage;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import com.google.common.collect.ImmutableMap;

public class MessageMoveEventTest {

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void builderShouldThrowWhenSessionIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenMessageMovesIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .session(new MockMailboxSession("user@james.org"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenMessagesIsEmpty() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .session(new MockMailboxSession("user@james.org"))
                .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
                .build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    public void builderShouldBuildWhenFieldsAreGiven() {
        MockMailboxSession session = new MockMailboxSession("user@james.org");
        MessageMoves messageMoves = MessageMoves.builder()
            .targetMailboxIds(TestId.of(2))
            .previousMailboxIds(TestId.of(1))
            .build();
        Map<MessageUid, MailboxMessage> messages = ImmutableMap.of(MessageUid.of(1), mock(MailboxMessage.class));

        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(session)
            .messageMoves(messageMoves)
            .messages(messages)
            .build();

        softly.assertThat(event.getSession()).isEqualTo(session);
        softly.assertThat(event.getMessageMoves()).isEqualTo(messageMoves);
        softly.assertThat(event.getMessages()).isEqualTo(messages);
    }

    @Test
    public void isMoveToShouldReturnFalseWhenMailboxIdIsNotInAddedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(new MockMailboxSession("user@james.org"))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .messages(ImmutableMap.of(MessageUid.of(1), mock(MailboxMessage.class)))
            .build();

        assertThat(event.isMoveTo(TestId.of(123))).isFalse();
    }

    @Test
    public void isMoveToShouldReturnTrueWhenMailboxIdIsInAddedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(new MockMailboxSession("user@james.org"))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1))
                .targetMailboxIds(TestId.of(2), mailboxId)
                .build())
            .messages(ImmutableMap.of(MessageUid.of(1), mock(MailboxMessage.class)))
            .build();

        assertThat(event.isMoveTo(mailboxId)).isTrue();
    }

    @Test
    public void isMoveFromShouldReturnFalseWhenMailboxIdIsNotInRemovedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(new MockMailboxSession("user@james.org"))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .messages(ImmutableMap.of(MessageUid.of(1), mock(MailboxMessage.class)))
            .build();

        assertThat(event.isMoveFrom(TestId.of(123))).isFalse();
    }

    @Test
    public void isMoveFromShouldReturnTrueWhenMailboxIdIsInRemovedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(new MockMailboxSession("user@james.org"))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1), mailboxId)
                .targetMailboxIds(TestId.of(2))
                .build())
            .messages(ImmutableMap.of(MessageUid.of(1), mock(MailboxMessage.class)))
            .build();

        assertThat(event.isMoveFrom(mailboxId)).isTrue();
    }
}
