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

import org.apache.james.core.Username;
import org.apache.james.mailbox.events.MessageMoveEvent;
import org.apache.james.mailbox.model.MessageMoves;
import org.apache.james.mailbox.model.TestId;
import org.apache.james.mailbox.model.TestMessageId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import nl.jqno.equalsverifier.EqualsVerifier;

class MessageMoveEventTest {
    private static final Username USER = Username.of("user@james.org");

    @Test
    void shouldRespectBeanContract() {
        EqualsVerifier.forClass(MessageMoveEvent.class).verify();
    }

    @Test
    void builderShouldThrowWhenSessionIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldThrowWhenMessageMovesIsNull() {
        assertThatThrownBy(() -> MessageMoveEvent.builder()
                .session(MailboxSessionUtil.create(USER))
                .build())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderShouldReturnNoopWhenMessagesIsEmpty() {
        assertThat(MessageMoveEvent.builder()
                .session(MailboxSessionUtil.create(USER))
                .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
                .build()
            .isNoop()).isTrue();
    }

    @Test
    void builderShouldNotBeNoopWhenFieldsAreGiven() {
        MailboxSession session = MailboxSessionUtil.create(USER);
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
    void builderShouldBuildWhenFieldsAreGiven() {
        MailboxSession session = MailboxSessionUtil.create(USER);
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

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(event.getUsername()).isEqualTo(USER);
            softly.assertThat(event.getMessageMoves()).isEqualTo(messageMoves);
            softly.assertThat(event.getMessageIds()).containsExactly(messageId);
        });
    }

    @Test
    void isMoveToShouldReturnFalseWhenMailboxIdIsNotInAddedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create(USER))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .build();

        assertThat(event.isMoveTo(TestId.of(123))).isFalse();
    }

    @Test
    void isMoveToShouldReturnTrueWhenMailboxIdIsInAddedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create(USER))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1))
                .targetMailboxIds(TestId.of(2), mailboxId)
                .build())
            .build();

        assertThat(event.isMoveTo(mailboxId)).isTrue();
    }

    @Test
    void isMoveFromShouldReturnFalseWhenMailboxIdIsNotInRemovedMailboxIds() {
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create(USER))
            .messageMoves(MessageMoves.builder()
                    .previousMailboxIds(TestId.of(1))
                    .targetMailboxIds(TestId.of(2))
                    .build())
            .build();

        assertThat(event.isMoveFrom(TestId.of(123))).isFalse();
    }

    @Test
    void isMoveFromShouldReturnTrueWhenMailboxIdIsInRemovedMailboxIds() {
        TestId mailboxId = TestId.of(123);
        MessageMoveEvent event = MessageMoveEvent.builder()
            .session(MailboxSessionUtil.create(USER))
            .messageMoves(MessageMoves.builder()
                .previousMailboxIds(TestId.of(1), mailboxId)
                .targetMailboxIds(TestId.of(2))
                .build())
            .build();

        assertThat(event.isMoveFrom(mailboxId)).isTrue();
    }
}
