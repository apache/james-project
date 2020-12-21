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
import java.util.UUID;

import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.TestMessageId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

public interface EmailChangeRepositoryContract {
    AccountId ACCOUNT_ID = AccountId.fromUsername(BOB);
    ZonedDateTime DATE = ZonedDateTime.now();
    State STATE_0 = State.of(UUID.randomUUID());

    EmailChangeRepository emailChangeRepository();

    @Test
    default void saveChangeShouldSuccess() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange change = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(1))
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

        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(2))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(3))
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4))
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getChangesShouldSuccess() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .updated(TestMessageId.of(1))
            .build();
        repository.save(oldState).block();
        repository.save(change).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change.getUpdated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnCurrentStateWhenNoNewerState() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        repository.save(oldState).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getNewState())
            .isEqualTo(oldState.getState());
    }

    @Test
    default void getChangesShouldLimitChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(3))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(2))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(3))
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4))
            .build();
        EmailChange change4 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.plusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(5))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();
        repository.save(change4).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestMessageId.of(2), TestMessageId.of(3), TestMessageId.of(4));
    }

    @Test
    default void getChangesShouldReturnAllFromInitial() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(3))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(2))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(3))
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestMessageId.of(1), TestMessageId.of(2), TestMessageId.of(3));
    }

    @Test
    default void getChangesFromInitialShouldReturnNewState() {
        EmailChangeRepository repository = emailChangeRepository();
        State state2 = State.of(UUID.randomUUID());

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(3))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(2))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(state2)
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(3))
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4))
            .build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getNewState())
            .isEqualTo(state2);
    }

    @Test
    default void getChangesShouldLimitChangesWhenMaxChangesOmitted() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3), TestMessageId.of(4), TestMessageId.of(5), TestMessageId.of(6))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(7))
            .build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldNotReturnMoreThanMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4), TestMessageId.of(5))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNumberOfChangesExceedMaxChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(4), TestMessageId.of(5))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(1))).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnNewState() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .updated(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getNewState())
            .isEqualTo(change2.getState());
    }

    @Test
    default void hasMoreChangesShouldBeTrueWhenMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .updated(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(1))).block().hasMoreChanges())
            .isTrue();
    }

    @Test
    default void hasMoreChangesShouldBeFalseWhenNoMoreChanges() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .updated(TestMessageId.of(2), TestMessageId.of(3))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(4))).block().hasMoreChanges())
            .isFalse();
    }

    @Test
    default void changesShouldBeStoredInTheirRespectiveType() {
        EmailChangeRepository repository = emailChangeRepository();


        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(3))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(2), TestMessageId.of(3), TestMessageId.of(4), TestMessageId.of(5))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .created(TestMessageId.of(6), TestMessageId.of(7))
            .updated(TestMessageId.of(2), TestMessageId.of(3))
            .destroyed(TestMessageId.of(4))
            .build();
        EmailChange change3 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .created(TestMessageId.of(8))
            .updated(TestMessageId.of(6), TestMessageId.of(7), TestMessageId.of(1))
            .destroyed(TestMessageId.of(5))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(20))).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getCreated()).containsExactlyInAnyOrder(TestMessageId.of(2), TestMessageId.of(3), TestMessageId.of(4), TestMessageId.of(5), TestMessageId.of(6), TestMessageId.of(7), TestMessageId.of(8));
            softly.assertThat(emailChanges.getUpdated()).containsExactlyInAnyOrder(TestMessageId.of(1));
            softly.assertThat(emailChanges.getDestroyed()).containsExactlyInAnyOrder(TestMessageId.of(4), TestMessageId.of(5));
        });
    }

    @Test
    default void getChangesShouldIgnoreDuplicatedValues() {
        EmailChangeRepository repository = emailChangeRepository();

        EmailChange oldState = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(STATE_0)
            .date(DATE.minusHours(2))
            .isDelegated(false)
            .created(TestMessageId.of(1))
            .build();
        EmailChange change1 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE.minusHours(1))
            .isDelegated(false)
            .updated(TestMessageId.of(1), TestMessageId.of(2))
            .build();
        EmailChange change2 = EmailChange.builder()
            .accountId(ACCOUNT_ID)
            .state(State.of(UUID.randomUUID()))
            .date(DATE)
            .isDelegated(false)
            .updated(TestMessageId.of(1), TestMessageId.of(2))
            .created(TestMessageId.of(3))
            .build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        EmailChanges emailChanges = repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(emailChanges.getUpdated()).containsExactly(TestMessageId.of(1), TestMessageId.of(2));
            softly.assertThat(emailChanges.getCreated()).containsExactly(TestMessageId.of(3));
        });
    }

    @Test
    default void getChangesShouldFailWhenSinceStateNotFound() {
        EmailChangeRepository repository = emailChangeRepository();

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block())
            .isInstanceOf(ChangeNotFoundException.class);
    }
}
