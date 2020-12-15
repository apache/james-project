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

import org.apache.james.jmap.api.change.MailboxChange.Limit;
import org.apache.james.jmap.api.change.MailboxChange.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.model.TestId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public interface MailboxChangeRepositoryContract {
    AccountId ACCOUNT_ID = AccountId.fromUsername(BOB);
    ZonedDateTime DATE = ZonedDateTime.now();
    State STATE_0 = State.of(UUID.randomUUID());

    MailboxChangeRepository mailboxChangeRepository();

    @Test
    default void saveChangeShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());

        assertThatCode(() -> repository.save(change))
            .doesNotThrowAnyException();
    }

    @Test
    default void saveChangeShouldFailWhenNoAccountId() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.of(null, STATE_0, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());

        assertThatThrownBy(() -> repository.save(change).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void saveChangeShouldFailWhenNoState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange change = MailboxChange.of(ACCOUNT_ID, null, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());

        assertThatThrownBy(() -> repository.save(change).block())
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    default void getChangesShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(1), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(), ImmutableList.of(TestId.of(1)), ImmutableList.of());
        repository.save(oldState);
        repository.save(change);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change.getUpdated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNoNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnCurrentStateWhenNoNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getNewState())
            .isEqualTo(oldState.getState());
    }

    @Test
    default void getChangesShouldLimitChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(3), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(2), ImmutableList.of(TestId.of(2)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change3 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(4)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestId.of(2), TestId.of(3), TestId.of(4));
    }

    @Test
    default void getChangesShouldReturnAllFromInitial() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(3), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(2), ImmutableList.of(TestId.of(2)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change3 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(4)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestId.of(1), TestId.of(2), TestId.of(3));
    }

    @Test
    default void getChangesFromInitialShouldReturnNewState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(3), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(2), ImmutableList.of(TestId.of(2)), ImmutableList.of(), ImmutableList.of());
        State state2 = State.of(UUID.randomUUID());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, state2, DATE.minusHours(1), ImmutableList.of(TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change3 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(4)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);


        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getNewState())
            .isEqualTo(state2);
    }

    @Test
    default void getChangesShouldLimitChangesWhenMaxChangesOmitted() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3), TestId.of(4), TestId.of(5), TestId.of(6)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(7)), ImmutableList.of(), ImmutableList.of());

        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldNotReturnMoreThanMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(4), TestId.of(5)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNumberOfChangesExceedMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(1))).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnNewState() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block().getNewState())
            .isEqualTo(change2.getState());
    }

    @Test
    default void hasMoreChangesShouldBeTrueWhenMoreChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(1))).block().hasMoreChanges())
            .isTrue();
    }

    @Test
    default void hasMoreChangesShouldBeFalseWhenNoMoreChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of());
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(4))).block().hasMoreChanges())
            .isFalse();
    }

    @Test
    default void changesShouldBeStoredInTheirRespectiveType() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(3), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(2), ImmutableList.of(TestId.of(2), TestId.of(3), TestId.of(4), TestId.of(5)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(TestId.of(6), TestId.of(7)), ImmutableList.of(TestId.of(2), TestId.of(3)), ImmutableList.of(TestId.of(4)));
        MailboxChange change3 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(8)), ImmutableList.of(TestId.of(6), TestId.of(7)), ImmutableList.of(TestId.of(5)));
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);

        MailboxChanges mailboxChanges = repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(20))).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxChanges.getCreated()).containsExactlyInAnyOrder(TestId.of(2), TestId.of(3), TestId.of(4), TestId.of(5), TestId.of(6), TestId.of(7), TestId.of(8));
            softly.assertThat(mailboxChanges.getUpdated()).containsExactlyInAnyOrder(TestId.of(2), TestId.of(3), TestId.of(6), TestId.of(7));
            softly.assertThat(mailboxChanges.getDestroyed()).containsExactlyInAnyOrder(TestId.of(4), TestId.of(5));
        });
    }

    @Test
    default void getChangesShouldIgnoreDuplicatedValues() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange oldState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE.minusHours(2), ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change1 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE.minusHours(1), ImmutableList.of(), ImmutableList.of(TestId.of(1), TestId.of(2)), ImmutableList.of());
        MailboxChange change2 = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(3)), ImmutableList.of(TestId.of(1), TestId.of(2)), ImmutableList.of());

        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        MailboxChanges mailboxChanges = repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(3))).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxChanges.getUpdated()).containsExactly(TestId.of(1), TestId.of(2));
            softly.assertThat(mailboxChanges.getCreated()).containsExactly(TestId.of(3));
        });
    }

    @Test
    default void getChangesShouldFailWhenInvalidMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        MailboxChange currentState = MailboxChange.of(ACCOUNT_ID, STATE_0, DATE, ImmutableList.of(TestId.of(1)), ImmutableList.of(), ImmutableList.of());
        MailboxChange change = MailboxChange.of(ACCOUNT_ID, State.of(UUID.randomUUID()), DATE, ImmutableList.of(TestId.of(2)), ImmutableList.of(), ImmutableList.of());
        repository.save(currentState);
        repository.save(change);

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.of(Limit.of(-1))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void getChangesShouldFailWhenSinceStateNotFound() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, STATE_0, Optional.empty()).block())
            .isInstanceOf(ChangeNotFoundException.class);
    }
}
