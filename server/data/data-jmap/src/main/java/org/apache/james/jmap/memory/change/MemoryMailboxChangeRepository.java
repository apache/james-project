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

package org.apache.james.jmap.memory.change;

import java.util.Comparator;
import java.util.Optional;
import java.util.function.Predicate;

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.MailboxChange;
import org.apache.james.jmap.api.change.MailboxChangeRepository;
import org.apache.james.jmap.api.change.MailboxChanges;
import org.apache.james.jmap.api.change.MailboxChanges.MailboxChangesBuilder.MailboxChangeCollector;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryMailboxChangeRepository implements MailboxChangeRepository {
    public static final String LIMIT_NAME = "mailboxChangeDefaultLimit";

    private final Multimap<AccountId, MailboxChange> mailboxChangeMap;
    private final Limit defaultLimit;

    @Inject
    public MemoryMailboxChangeRepository(@Named(LIMIT_NAME) Limit defaultLimit) {
        this.defaultLimit = defaultLimit;
        this.mailboxChangeMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    }

    @Override
    public Mono<Void> save(MailboxChange change) {
        return Mono.just(mailboxChangeMap.put(change.getAccountId(), change)).then();
    }

    @Override
    public Mono<MailboxChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);

        if (state.equals(State.INITIAL)) {
            return Flux.fromIterable(mailboxChangeMap.get(accountId))
                .filter(Predicate.not(MailboxChange::isShared))
                .sort(Comparator.comparing(MailboxChange::getDate))
                .collect(new MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return findByState(accountId, state)
            .flatMapMany(currentState -> Flux.fromIterable(mailboxChangeMap.get(accountId))
                .filter(change -> change.getDate().isAfter(currentState.getDate()))
                .filter(Predicate.not(MailboxChange::isShared))
                .sort(Comparator.comparing(MailboxChange::getDate)))
            .collect(new MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<MailboxChanges> getSinceStateWithDelegation(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));
        if (state.equals(State.INITIAL)) {
            return Flux.fromIterable(mailboxChangeMap.get(accountId))
                .sort(Comparator.comparing(MailboxChange::getDate))
                .collect(new MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
        }

        return findByState(accountId, state)
            .flatMapMany(currentState -> Flux.fromIterable(mailboxChangeMap.get(accountId))
                .filter(change -> change.getDate().isAfter(currentState.getDate()))
                .sort(Comparator.comparing(MailboxChange::getDate)))
            .collect(new MailboxChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    private Mono<MailboxChange> findByState(AccountId accountId, State state) {
        return Flux.fromIterable(mailboxChangeMap.get(accountId))
            .filter(change -> change.getState().equals(state))
            .switchIfEmpty(Mono.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .single();
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        return Flux.fromIterable(mailboxChangeMap.get(accountId))
            .filter(change -> !change.isShared())
            .sort(Comparator.comparing(MailboxChange::getDate))
            .map(MailboxChange::getState)
            .last(State.INITIAL);
    }

    @Override
    public Mono<State> getLatestStateWithDelegation(AccountId accountId) {
        return Flux.fromIterable(mailboxChangeMap.get(accountId))
            .sort(Comparator.comparing(MailboxChange::getDate))
            .map(MailboxChange::getState)
            .last(State.INITIAL);
    }
}
