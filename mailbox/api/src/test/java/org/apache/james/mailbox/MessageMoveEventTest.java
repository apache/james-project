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
package org.apache.james.mailbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.apache.james.core.User;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.assertj.core.api.JUnitSoftAssertions;
import org.junit.Rule;
import org.junit.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

public class MessageMoveEventTest {

    @Rule
    public JUnitSoftAssertions softly = new JUnitSoftAssertions();

    @Test
    public void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MessageMoveEvent.class).verify();
    }

    @Test
    public void builderShouldThrowWhenSessionIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldThrowWhenMessageMovesIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .session(MailboxSessionUtil.create("user@james.org"))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void builderShouldReturnNoopWhenMessagesIsEmpty() {
        assertThat(MessageMoveEvent.builder()
                .session(MailboxSessionUtil.create("user@james.org"))
                .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
                .build()
            .isNoop()).isTrue();
    }

    @Test
    public void builderShouldNotBeNoopWhenFieldsAreGiven() {
        MailboxSession session = MailboxSessionUtil.create("user@james.org");
        MessageMoves messageMoves = MessageMoves.builder()
            .targetMailboxIds(TestId.of(2))
            .previousMailboxIds(TestId.of(1))
            .build();

        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(session)
            .messageMoves(messageMoves)
            .messageId(TestMessageId.of(35))
            .build();

        assertThat(event.isNoop()).isFalse();
    }

    @Test
    public void builderShouldBuildWhenFieldsAreGiven() {
        String username = "user@james.org";
        MailboxSession session = MailboxSessionUtil.create(username);
        MessageMoves messageMoves = MessageMoves.builder()
            .targetMailboxIds(TestId.of(2))
            .previousMailboxIds(TestId.of(1))
            .build();

        TestMessageId messageId = TestMessageId.of(45);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(session)
            .messageMoves(messageMoves)
            .messageId(messageId)
            .build();

        softly.assertThat(event.getUser()).isEqualTo(User.fromUsername(username));
        softly.assertThat(event.getMessageMoves()).isEqualTo(messageMoves);
        softly.assertThat(event.getMessageIds()).containsExactly(messageId);
    }

    @Test
    public void isMoveToShouldReturnFalseWhenMailboxIdIsNotInAddedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create("user@james.org"))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .build();

        assertThat(event.isMoveTo(TestId.of(123))).isFalse();
    }

    @Test
    public void isMoveToShouldReturnTrueWhenMailboxIdIsInAddedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create("user@james.org"))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1))
                .targetMailboxIds(TestId.of(2), mailboxId)
                .build())
            .build();

        assertThat(event.isMoveTo(mailboxId)).isTrue();
    }

    @Test
    public void isMoveFromShouldReturnFalseWhenMailboxIdIsNotInRemovedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create("user@james.org"))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .build();

        assertThat(event.isMoveFrom(TestId.of(123))).isFalse();
    }

    @Test
    public void isMoveFromShouldReturnTrueWhenMailboxIdIsInRemovedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create("user@james.org"))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1), mailboxId)
                .targetMailboxIds(TestId.of(2))
                .build())
            .build();

        assertThat(event.isMoveFrom(mailboxId)).isTrue();
    }
}
