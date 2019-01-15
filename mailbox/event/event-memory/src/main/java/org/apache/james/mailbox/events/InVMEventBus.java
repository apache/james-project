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

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;

import org.apache.james.mailbox.Event;
import org.apache.james.mailbox.MailboxListener;
import org.apache.james.mailbox.events.delivery.EventDelivery;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class InVMEventBus implements EventBus {
    private final Multimap<RegistrationKey, MailboxListener> registrations;
    private final ConcurrentHashMap<Group, MailboxListener> groups;
    private final EventDelivery eventDelivery;

    @Inject
    public InVMEventBus(EventDelivery eventDelivery) {
        this.eventDelivery = eventDelivery;
        this.registrations = Multimaps.synchronizedSetMultimap(HashMultimap.create());
        this.groups = new ConcurrentHashMap<>();
    }

    @Override
    public Registration register(MailboxListener listener, RegistrationKey key) {
        registrations.put(key, listener);
        return () -> registrations.remove(key, listener);
    }

    @Override
    public Registration register(MailboxListener listener, Group group) {
        MailboxListener previous = groups.putIfAbsent(group, listener);
        if (previous == null) {
            return () -> groups.remove(group, listener);
        }
        throw new GroupAlreadyRegistered(group);
    }

    @Override
    public Mono<Void> dispatch(Event event, Set<RegistrationKey> keys) {
        if (!event.isNoop()) {
            return Flux.merge(
                eventDelivery.deliverWithRetries(groups.values(), event).synchronousListenerFuture(),
                eventDelivery.deliver(registeredListenersByKeys(keys), event).synchronousListenerFuture())
                .then()
                .onErrorResume(throwable -> Mono.empty());
        }
        return Mono.empty();
    }

    public Set<Group> registeredGroups() {
        return groups.keySet();
    }

    private Set<MailboxListener> registeredListenersByKeys(Set<RegistrationKey> keys) {
        return keys.stream()
            .flatMap(registrationKey -> registrations.get(registrationKey).stream())
            .collect(Guavate.toImmutableSet());
    }
}
