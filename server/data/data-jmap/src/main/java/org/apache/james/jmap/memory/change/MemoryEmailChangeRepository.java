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

import jakarta.inject.Inject;
import jakarta.inject.Named;

import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.EmailChangeRepository;
import org.apache.james.jmap.api.change.EmailChanges;
import org.apache.james.jmap.api.change.EmailChanges.Builder.EmailChangeCollector;
import org.apache.james.jmap.api.change.Limit;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.exception.ChangeNotFoundException;
import org.apache.james.jmap.api.model.AccountId;

import com.google.common.base.Preconditions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class MemoryEmailChangeRepository implements EmailChangeRepository {
    public static final String LIMIT_NAME = "emailChangeDefaultLimit";

    private final Multimap<AccountId, EmailChange> emailChangeMap;
    private final Limit defaultLimit;

    @Inject
    public MemoryEmailChangeRepository(@Named(LIMIT_NAME) Limit defaultLimit) {
        this.defaultLimit = defaultLimit;
        this.emailChangeMap = Multimaps.synchronizedListMultimap(ArrayListMultimap.create());
    }

    @Override
    public Mono<Void> save(EmailChange change) {
        Preconditions.checkNotNull(change.getAccountId());
        Preconditions.checkNotNull(change.getState());

        return Mono.just(emailChangeMap.put(change.getAccountId(), change)).then();
    }

    @Override
    public Mono<State> getLatestState(AccountId accountId) {
        return allChanges(accountId)
            .filter(change -> !change.isShared())
            .map(EmailChange::getState)
            .last(State.INITIAL);
    }

    @Override
    public Mono<EmailChanges> getSinceState(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);
        maxChanges.ifPresent(limit -> Preconditions.checkArgument(limit.getValue() > 0, "maxChanges must be a positive integer"));

        return resolveAllChanges(accountId, state)
            .filter(change -> !change.isShared())
            .collect(new EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<EmailChanges> getSinceStateWithDelegation(AccountId accountId, State state, Optional<Limit> maxChanges) {
        Preconditions.checkNotNull(accountId);
        Preconditions.checkNotNull(state);

        return resolveAllChanges(accountId, state)
            .collect(new EmailChangeCollector(state, maxChanges.orElse(defaultLimit)));
    }

    @Override
    public Mono<State> getLatestStateWithDelegation(AccountId accountId) {
        return allChanges(accountId)
            .sort(Comparator.comparing(EmailChange::getDate))
            .map(EmailChange::getState)
            .last(State.INITIAL);
    }

    private Flux<EmailChange> resolveAllChanges(AccountId accountId, State state) {
        if (state.equals(State.INITIAL)) {
            return allChanges(accountId);
        }
        return allChangesSince(accountId, state);
    }

    private Flux<EmailChange> allChangesSince(AccountId accountId, State state) {
        return findByState(accountId, state)
            .flatMapIterable(currentState -> emailChangeMap.get(accountId).stream()
                .filter(change -> change.getDate().isAfter(currentState.getDate()))
                .sorted(Comparator.comparing(EmailChange::getDate))
                .collect(ImmutableList.toImmutableList()));
    }

    private Flux<EmailChange> allChanges(AccountId accountId) {
        return Flux.fromIterable(emailChangeMap.get(accountId))
            .sort(Comparator.comparing(EmailChange::getDate));
    }

    private Mono<EmailChange> findByState(AccountId accountId, State state) {
        return Flux.fromIterable(emailChangeMap.get(accountId))
            .filter(change -> change.getState().equals(state))
            .switchIfEmpty(Mono.error(() -> new ChangeNotFoundException(state, String.format("State '%s' could not be found", state.getValue()))))
            .single();
    }
}
