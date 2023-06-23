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

package org.apache.james.jmap.api.change;

import static org.apache.james.mailbox.fixture.MailboxFixture.BOB;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.MessageId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public interface EmailChangeRepositoryContract {
    Limit DEFAULT_NUMBER_OF_CHANGES = Limit.of(5);
    AccountId ACCOUNT_ID = AccountId.fromUsername(BOB);
    ZonedDateTime DATE = ZonedDateTime.now();

    EmailChangeRepository emailChangeRepository();
    MessageId generateNewMessageId();
    State generateNewState();

    @Test
    default void saveChangeShouldSuccess() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange change = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId())
            .build();

        assertThatCode(() -> repository.save(change).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void getLatestStateShouldReturnInitialWhenEmpty() {
        EmailChangeRepository repository = emailChangeRepository();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(State.INITIAL);
    }

    @Test
    default void getLatestStateShouldReturnLastPersistedState() {
        EmailChangeRepository repository = emailChangeRepository();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId3)
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getLatestStateShouldReturnLastNonDelegatedPersistedState() {
        EmailChangeRepository repository = emailChangeRepository();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(true)
            .created(messageId3)
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(change2.getState());
    }

    @Test
    default void getChangesShouldSuccess() {
        EmailChangeRepository repository = emailChangeRepository();

        MessageId messageId = generateNewMessageId();
        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId)
            .build();
        EmailChange change = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId)
            .build();
        repository.save(oldState).block();
        repository.save(change).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change.getUpdated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();
        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnCurrentStateWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.empty()).block().getNewState())
            .isEqualTo(oldState.getState());
    }

    @Test
    default void getChangesShouldLimitChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();
        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId3)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId4)
            .build();
        EmailChange change4 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.plusHours(1))
            .isShared(false)
            .created(messageId5)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();
        repository.save(change4).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(messageId2, messageId3, messageId4);
    }

    @Test
    default void getChangesShouldReturnAllFromInitial() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();
        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId3)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId4)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(messageId1, messageId2, messageId3);
    }

    @Test
    default void getChangesFromInitialShouldReturnNewState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();
        State state2 = generateNewState();
        State state3 = generateNewState();
        State state4 = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state2)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state3)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId3)
            .build();
        EmailChange change4 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state4)
            .date(DATE)
            .isShared(false)
            .created(messageId4)
            .build();

        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();
        repository.save(change4).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getNewState())
            .isEqualTo(state3);
    }

    @Test
    default void getChangesShouldLimitChangesWhenMaxChangesOmitted() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();
        MessageId messageId6 = generateNewMessageId();
        MessageId messageId7 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3, messageId4, messageId5, messageId6)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId7)
            .build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldNotReturnMoreThanMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId4, messageId5)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShoulThrowWhenNumberOfChangesExceedMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(1))).block().getAllChanges())
            .isInstanceOf(CanNotCalculateChangesException.class)
            .hasMessage("Current change collector limit 1 is exceeded by a single change, hence we cannot calculate changes.");
    }

    @Test
    default void getChangesShouldReturnNewState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId2, messageId3)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.empty()).block().getNewState())
            .isEqualTo(change2.getState());
    }

    @Test
    default void hasMoreChangesShouldBeTrueWhenMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId2, messageId1)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(2))).block().hasMoreChanges())
            .isTrue();
    }

    @Test
    default void hasMoreChangesShouldBeFalseWhenNoMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId2, messageId3)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId2, messageId3)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(4))).block().hasMoreChanges())
            .isFalse();
    }

    @Test
    default void changesShouldBeStoredInTheirRespectiveType() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();
        MessageId messageId6 = generateNewMessageId();
        MessageId messageId7 = generateNewMessageId();
        MessageId messageId8 = generateNewMessageId();
        MessageId messageId9 = generateNewMessageId();
        MessageId messageId10 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1, messageId9, messageId10)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2, messageId3, messageId4, messageId5)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId6, messageId7)
            .updated(messageId2, messageId3, messageId10)
            .destroyed(messageId4, messageId9)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId8)
            .updated(messageId6, messageId7, messageId1)
            .destroyed(messageId5, messageId10)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(20))).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getCreated()).containsExactlyInAnyOrder(messageId2, messageId3, messageId6, messageId7, messageId8);
            softly.assertThat(emailChanges.getUpdated()).containsExactlyInAnyOrder(messageId2, messageId3, messageId6, messageId7, messageId1);
            softly.assertThat(emailChanges.getDestroyed()).containsExactlyInAnyOrder(messageId9, messageId10);
        });
    }

    @Test
    default void changesShouldNotReturnDelegatedChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();
        MessageId messageId6 = generateNewMessageId();
        MessageId messageId7 = generateNewMessageId();
        MessageId messageId8 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2, messageId3, messageId4, messageId5)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(true)
            .created(messageId6, messageId7)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId8)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(20))).block();

        assertThat(emailChanges.getCreated())
            .containsExactlyInAnyOrder(messageId2, messageId3, messageId4, messageId5, messageId8);
    }

    @Test
    default void getChangesShouldIgnoreDuplicatedValues() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .updated(messageId1, messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId1, messageId2)
            .created(messageId3)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getUpdated()).containsExactly(messageId1, messageId2);
            softly.assertThat(emailChanges.getCreated()).containsExactly(messageId3);
        });
    }

    @Test
    default void getChangesShouldFailWhenSinceStateNotFound() {
        EmailChangeRepository repository = emailChangeRepository();

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, generateNewState(), Optional.empty()).block())
            .isInstanceOf(ChangeNotFoundException.class);
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnInitialWhenEmpty() {
        EmailChangeRepository repository = emailChangeRepository();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(State.INITIAL);
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnLastPersistedState() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnLastDelegatedPersistedState() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(true)
            .created(generateNewMessageId())
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getSinceStateWithDelegationShouldSuccess() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change.getUpdated());
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnEmptyWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnCurrentStateWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.empty()).block().getNewState())
            .isEqualTo(oldState.getState());
    }

    @Test
    default void getSinceStateWithDelegationShouldLimitChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId3)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId4)
            .build();
        EmailChange change4 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.plusHours(1))
            .isShared(false)
            .created(messageId5)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();
        repository.save(change4).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(messageId2, messageId3, messageId4);
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnAllFromInitial() {
        EmailChangeRepository repository = emailChangeRepository();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId3)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId4)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(messageId1, messageId2, messageId3);
    }

    @Test
    default void getSinceStateWithDelegationShouldLimitChangesWhenMaxChangesOmitted() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId(), generateNewMessageId(), generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId())
            .build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getSinceStateWithDelegationShouldNotReturnMoreThanMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getSinceStateWithDelegationShouldThrowWhenNumberOfChangesExceedMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();

        assertThatThrownBy(() -> repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(1))).block().getAllChanges())
            .isInstanceOf(CanNotCalculateChangesException.class)
            .hasMessage("Current change collector limit 1 is exceeded by a single change, hence we cannot calculate changes.");
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnNewState() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(generateNewMessageId(), generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.empty()).block().getNewState())
            .isEqualTo(change2.getState());
    }

    @Test
    default void getSinceStateWithDelegationHasMoreChangesShouldBeTrueWhenMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(generateNewMessageId(), generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(2))).block().hasMoreChanges())
            .isTrue();
    }

    @Test
    default void getSinceStateWithDelegationHasMoreChangesShouldBeFalseWhenNoMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(generateNewMessageId())
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(generateNewMessageId(), generateNewMessageId())
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(generateNewMessageId(), generateNewMessageId())
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(4))).block().hasMoreChanges())
            .isFalse();
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnChangesInTheirRespectiveType() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();
        MessageId messageId6 = generateNewMessageId();
        MessageId messageId7 = generateNewMessageId();
        MessageId messageId8 = generateNewMessageId();
        MessageId messageId9 = generateNewMessageId();
        MessageId messageId10 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1, messageId9, messageId10)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2, messageId3, messageId4, messageId5)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .created(messageId6, messageId7)
            .updated(messageId2, messageId3, messageId10)
            .destroyed(messageId4, messageId9)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId8)
            .updated(messageId6, messageId7, messageId1)
            .destroyed(messageId5, messageId10)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, state, Optional.of(Limit.of(20))).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getCreated()).containsExactlyInAnyOrder(messageId2, messageId3, messageId6, messageId7, messageId8);
            softly.assertThat(emailChanges.getUpdated()).containsExactlyInAnyOrder(messageId2, messageId3, messageId6, messageId7, messageId1);
            softly.assertThat(emailChanges.getDestroyed()).containsExactlyInAnyOrder(messageId9, messageId10);
        });
    }

    @Test
    default void getSinceStateWithDelegationShouldReturnDelegatedChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();
        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();
        MessageId messageId4 = generateNewMessageId();
        MessageId messageId5 = generateNewMessageId();
        MessageId messageId6 = generateNewMessageId();
        MessageId messageId7 = generateNewMessageId();
        MessageId messageId8 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(3))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId2, messageId3, messageId4, messageId5)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(true)
            .created(messageId6, messageId7)
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .created(messageId8)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        EmailChanges emailChanges = repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(20))).block();

        assertThat(emailChanges.getCreated())
            .containsExactlyInAnyOrder(messageId2, messageId3, messageId4, messageId5, messageId6, messageId7, messageId8);
    }

    @Test
    default void getSinceStateWithDelegationShouldIgnoreDuplicatedValues() {
        EmailChangeRepository repository = emailChangeRepository();

        State state = generateNewState();

        MessageId messageId1 = generateNewMessageId();
        MessageId messageId2 = generateNewMessageId();
        MessageId messageId3 = generateNewMessageId();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state)
            .date(DATE.minusHours(2))
            .isShared(false)
            .created(messageId1)
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE.minusHours(1))
            .isShared(false)
            .updated(messageId1, messageId2)
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(generateNewState())
            .date(DATE)
            .isShared(false)
            .updated(messageId1, messageId2)
            .created(messageId3)
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        EmailChanges emailChanges = repository.getSinceStateWithDelegation(ACCOUNT_ID, state, Optional.of(Limit.of(3))).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getUpdated()).containsExactly(messageId1, messageId2);
            softly.assertThat(emailChanges.getCreated()).containsExactly(messageId3);
        });
    }

    @Test
    default void getSinceStateWithDelegationShouldFailWhenSinceStateNotFound() {
        EmailChangeRepository repository = emailChangeRepository();

        assertThatThrownBy(() -> repository.getSinceStateWithDelegation(ACCOUNT_ID, generateNewState(), Optional.empty()).block())
            .isInstanceOf(ChangeNotFoundException.class);
    }
}
