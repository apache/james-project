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

package org.apache.james.mailbox.events;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.mailbox.events.delivery.EventDelivery;
import org.apache.james.mailbox.events.delivery.EventDelivery.PermanentFailureHandler.StoreToDeadLetters;
import org.apache.james.mailbox.events.delivery.EventDelivery.Retryer.BackoffRetryer;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InVMEventBus implements EventBus {

    private final Multimap<RegistrationKey, MailboxListener.ReactiveMailboxListener> registrations;
    private final ConcurrentHashMap<Group, MailboxListener.ReactiveMailboxListener> groups;
    private final EventDelivery eventDelivery;
    private final RetryBackoffConfiguration retryBackoff;
    private final EventDeadLetters eventDeadLetters;

    @Inject
    public InVMEventBus(EventDelivery eventDelivery, RetryBackoffConfiguration retryBackoff, EventDeadLetters eventDeadLetters) {
        this.eventDelivery = eventDelivery;
        this.retryBackoff = retryBackoff;
        this.eventDeadLetters = eventDeadLetters;
        this.registrations = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        this.groups = new ConcurrentHashMap<>();
    }

    @Override
    public Mono<Registration> register(MailboxListener.ReactiveMailboxListener listener, RegistrationKey key) {
        registrations.put(key, listener);
        return Mono.just(() -> registrations.remove(key, listener));
    }

    @Override
    public Registration register(MailboxListener.ReactiveMailboxListener listener, Group group) {
        MailboxListener previous = groups.putIfAbsent(group, listener);
        if (previous == null) {
            return () -> groups.remove(group, listener);
        }
        throw new GroupAlreadyRegistered(group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        if (!event.isNoop()) {
            return Flux.merge(groupDeliveries(event), keyDeliveries(event, keys))
                .then()
                .onErrorResume(throwable -> Mono.empty());
        }
        return Mono.empty();
    }

    @Override
    public Mono<Void> reDeliver(Group group, Event event) {
        if (!event.isNoop()) {
            return groupDelivery(event, retrieveListenerFromGroup(group), group);
        }
        return Mono.empty();
    }

    private MailboxListener.ReactiveMailboxListener retrieveListenerFromGroup(Group group) {
        return Optional.ofNullable(groups.get(group))
            .orElseThrow(() -> new GroupRegistrationNotFound(group));
    }

    private Mono<Void> keyDeliveries(Event event, Set<RegistrationKey> keys) {
        return Flux.fromIterable(registeredListenersByKeys(keys))
            .flatMap(listener -> eventDelivery.deliver(listener, event, EventDelivery.DeliveryOption.none()))
            .then();
    }

    private Mono<Void> groupDeliveries(Event event) {
        return Flux.fromIterable(groups.entrySet())
            .flatMap(entry -> groupDelivery(event, entry.getValue(), entry.getKey()))
            .then();
    }

    private Mono<Void> groupDelivery(Event event, MailboxListener.ReactiveMailboxListener mailboxListener, Group group) {
        return eventDelivery.deliver(
            mailboxListener,
            event,
            EventDelivery.DeliveryOption.of(
                BackoffRetryer.of(retryBackoff, mailboxListener),
                StoreToDeadLetters.of(group, eventDeadLetters)));
    }

    public Set<Group> registeredGroups() {
        return groups.keySet();
    }

    private Set<MailboxListener.ReactiveMailboxListener> registeredListenersByKeys(Set<RegistrationKey> keys) {
        return keys.stream()
            .flatMap(registrationKey -> registrations.get(registrationKey).stream())
            .collect(Guavate.toImmutableSet());
    }
}
