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

import static com.google.common.base.Predicates.not;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;
import reactor.core.publisher.Flux;

class MailboxListenerRegistry {
    private final ConcurrentHashMap<RegistrationKey, ImmutableSet<MailboxListener>> listenersByKey;

    MailboxListenerRegistry() {
        this.listenersByKey = new ConcurrentHashMap<>();
    }

    void addListener(RegistrationKey registrationKey, MailboxListener listener, Runnable runIfEmpty) {
        listenersByKey.compute(registrationKey, (key, listeners) ->
            Optional.ofNullable(listeners)
                .map(set -> ImmutableSet.<MailboxListener>builder().addAll(set).add(listener).build())
                .orElseGet(() -> {
                    runIfEmpty.run();
                    return ImmutableSet.of(listener);
                })
        );
    }

    void removeListener(RegistrationKey registrationKey, MailboxListener listener, Runnable runIfEmpty) {
        listenersByKey.compute(registrationKey, (key, listeners) -> {
            boolean listenersContainRequested = Optional.ofNullable(listeners).orElse(ImmutableSet.of()).contains(listener);
            if (listenersContainRequested) {
                return removeListenerFromSet(listener, runIfEmpty, listeners);
            }
            return listeners;
        });
    }

    private ImmutableSet<MailboxListener> removeListenerFromSet(MailboxListener listener, Runnable runIfEmpty, ImmutableSet<MailboxListener> listeners) {
        ImmutableSet<MailboxListener> remainingListeners = listeners.stream().filter(not(listener::equals)).collect(Guavate.toImmutableSet());
        if (remainingListeners.isEmpty()) {
            runIfEmpty.run();
            return null;
        }
        return remainingListeners;
    }

    Flux<MailboxListener> getLocalMailboxListeners(RegistrationKey registrationKey) {
        return Flux.fromIterable(listenersByKey.getOrDefault(registrationKey, ImmutableSet.of()));
    }
}