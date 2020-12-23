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
import org.apache.james.mailbox.model.TestId;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;

import com.google.common.collect.ImmutableList;

public interface MailboxChangeRepositoryContract {
    AccountId ACCOUNT_ID = AccountId.fromUsername(BOB);
    ZonedDateTime DATE = ZonedDateTime.now();

    State.Factory stateFactory();

    MailboxChangeRepository mailboxChangeRepository();

    @Test
    default void saveChangeShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State state = stateFactory().generate();

        MailboxChange change = MailboxChange.builder().accountId(ACCOUNT_ID).state(state).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();

        assertThatCode(() -> repository.save(change).block())
            .doesNotThrowAnyException();
    }

    @Test
    default void getLatestStateShouldReturnInitialWhenEmpty() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(State.INITIAL);
    }

    @Test
    default void getLatestStateShouldReturnLastPersistedState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4))).build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getLatestStateShouldNotReturnDelegated() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(4)))
            .delegated()
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestState(ACCOUNT_ID).block())
            .isEqualTo(change2.getState());
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnInitialWhenEmpty() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(State.INITIAL);
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnLastPersistedState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4))).build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getLatestStateWithDelegationShouldReturnDelegated() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(4)))
            .delegated()
            .build();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getLatestStateWithDelegation(ACCOUNT_ID).block())
            .isEqualTo(change3.getState());
    }

    @Test
    default void getChangesShouldSuccess() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).updated(ImmutableList.of(TestId.of(1))).build();
        repository.save(oldState);
        repository.save(change);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change.getUpdated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNoNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        repository.save(oldState);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnCurrentStateWhenNoNewerState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        repository.save(oldState);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block().getNewState())
            .isEqualTo(oldState.getState());
    }

    @Test
    default void getChangesShouldLimitChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(3)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4))).build();
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);
        repository.save(change3);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestId.of(2), TestId.of(3), TestId.of(4));
    }

    @Test
    default void getChangesShouldReturnAllFromInitial() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(3)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4))).build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getCreated())
            .containsExactlyInAnyOrder(TestId.of(1), TestId.of(2), TestId.of(3));
    }

    @Test
    default void getChangesFromInitialShouldReturnNewState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(3)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        State state2 = stateFactory.generate();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(state2).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(3))).build();
        MailboxChange change3 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4))).build();
        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();


        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.of(Limit.of(3))).block().getNewState())
            .isEqualTo(state2);
    }

    @Test
    default void getChangesShouldLimitChangesWhenMaxChangesOmitted() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3), TestId.of(4), TestId.of(5), TestId.of(6))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(7))).build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldNotReturnMoreThanMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(4), TestId.of(5))).build();
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(3))).block().getAllChanges())
            .hasSameElementsAs(change1.getCreated());
    }

    @Test
    default void getChangesShouldReturnEmptyWhenNumberOfChangesExceedMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        repository.save(oldState);
        repository.save(change1);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(1))).block().getAllChanges())
            .isEmpty();
    }

    @Test
    default void getChangesShouldReturnNewState() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).updated(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block().getNewState())
            .isEqualTo(change2.getState());
    }

    @Test
    default void hasMoreChangesShouldBeTrueWhenMoreChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).updated(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(1))).block().hasMoreChanges())
            .isTrue();
    }

    @Test
    default void hasMoreChangesShouldBeFalseWhenNoMoreChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).created(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        MailboxChange change2 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).updated(ImmutableList.of(TestId.of(2), TestId.of(3))).build();
        repository.save(oldState);
        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(4))).block().hasMoreChanges())
            .isFalse();
    }

    @Test
    default void changesShouldBeStoredInTheirRespectiveType() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(referenceState)
            .date(DATE.minusHours(3))
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(1), TestId.of(9), TestId.of(10)))
            .build();
        MailboxChange change1 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(2))
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(2), TestId.of(3), TestId.of(4), TestId.of(5)))
            .build();
        MailboxChange change2 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(1))
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(6), TestId.of(7)))
            .updated(ImmutableList.of(TestId.of(2), TestId.of(3), TestId.of(9)))
            .destroyed(ImmutableList.of(TestId.of(4))).build();
        MailboxChange change3 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(8)))
            .updated(ImmutableList.of(TestId.of(6), TestId.of(7)))
            .destroyed(ImmutableList.of(TestId.of(5), TestId.of(10))).build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();
        repository.save(change3).block();

        MailboxChanges mailboxChanges = repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(20))).block();

        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxChanges.getCreated()).containsExactlyInAnyOrder(TestId.of(2), TestId.of(3), TestId.of(6), TestId.of(7), TestId.of(8));
            softly.assertThat(mailboxChanges.getUpdated()).containsExactlyInAnyOrder(TestId.of(9));
            softly.assertThat(mailboxChanges.getDestroyed()).containsExactlyInAnyOrder(TestId.of(10));
        });
    }

    @Test
    default void getChangesShouldIgnoreDuplicatedValues() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE.minusHours(1)).isCountChange(false).updated(ImmutableList.of(TestId.of(1), TestId.of(2))).build();
        MailboxChange change2 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(3)))
            .updated(ImmutableList.of(TestId.of(1), TestId.of(2)))
            .build();

        repository.save(oldState).block();
        repository.save(change1).block();
        repository.save(change2).block();

        MailboxChanges mailboxChanges = repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(3))).block();
        SoftAssertions.assertSoftly(softly -> {
            softly.assertThat(mailboxChanges.getUpdated()).containsExactly(TestId.of(1), TestId.of(2));
            softly.assertThat(mailboxChanges.getCreated()).containsExactly(TestId.of(3));
        });
    }

    @Test
    default void getChangesShouldReturnDelegatedChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange oldState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE.minusHours(2)).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change1 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(1))
            .isCountChange(false)
            .updated(ImmutableList.of(TestId.of(1)))
            .delegated()
            .build();

        repository.save(oldState);
        repository.save(change1);

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, referenceState, Optional.empty()).block().getUpdated())
            .containsExactly(TestId.of(1));
    }

    @Test
    default void isCountChangeOnlyShouldBeFalseWhenNoChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.empty()).block().isCountChangesOnly())
            .isFalse();
    }

    @Test
    default void isCountChangeOnlyShouldBeFalseWhenAllNonCountChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(1))
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(1)))
            .build();
        MailboxChange change2 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(2)))
            .build();

        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.empty()).block().isCountChangesOnly())
            .isFalse();
    }

    @Test
    default void isCountChangeOnlyShouldBeFalseWhenMixedChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(1))
            .isCountChange(false)
            .created(ImmutableList.of(TestId.of(1)))
            .build();
        MailboxChange change2 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(false)
            .updated(ImmutableList.of(TestId.of(2)))
            .build();

        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceState(ACCOUNT_ID, State.INITIAL, Optional.empty()).block().isCountChangesOnly())
            .isFalse();
    }

    @Test
    default void isCountChangeOnlyShouldBeTrueWhenAllCountChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();

        MailboxChange change1 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE.minusHours(1))
            .isCountChange(true)
            .updated(ImmutableList.of(TestId.of(1)))
            .build();
        MailboxChange change2 = MailboxChange.builder()
            .accountId(ACCOUNT_ID)
            .state(stateFactory.generate())
            .date(DATE)
            .isCountChange(true)
            .updated(ImmutableList.of(TestId.of(2)))
            .build();

        repository.save(change1);
        repository.save(change2);

        assertThat(repository.getSinceStateWithDelegation(ACCOUNT_ID, State.INITIAL, Optional.empty()).block().isCountChangesOnly())
            .isTrue();
    }

    @Test
    default void getChangesShouldFailWhenInvalidMaxChanges() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        MailboxChange currentState = MailboxChange.builder().accountId(ACCOUNT_ID).state(referenceState).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(1))).build();
        MailboxChange change = MailboxChange.builder().accountId(ACCOUNT_ID).state(stateFactory.generate()).date(DATE).isCountChange(false).created(ImmutableList.of(TestId.of(2))).build();
        repository.save(currentState);
        repository.save(change);

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, referenceState, Optional.of(Limit.of(-1))))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    default void getChangesShouldFailWhenSinceStateNotFound() {
        MailboxChangeRepository repository = mailboxChangeRepository();
        State.Factory stateFactory = stateFactory();
        State referenceState = stateFactory.generate();

        assertThatThrownBy(() -> repository.getSinceState(ACCOUNT_ID, referenceState, Optional.empty()).block())
            .isInstanceOf(ChangeNotFoundException.class);
    }
}
