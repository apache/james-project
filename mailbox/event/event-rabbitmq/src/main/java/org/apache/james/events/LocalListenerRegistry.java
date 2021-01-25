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

package org.apache.james.events;

import static com.google.common.base.Predicates.not;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.apache.james.events.EventListener;
import org.apache.james.events.RegistrationKey;

import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;

class LocalListenerRegistry {

    interface RemovalStatus {
        boolean lastListenerRemoved();
    }

    public static class LocalRegistration {
        private final boolean firstListener;
        private final Supplier<RemovalStatus> unregister;

        public LocalRegistration(boolean firstListener, Supplier<RemovalStatus> unregister) {
            this.firstListener = firstListener;
            this.unregister = unregister;
        }

        public boolean isFirstListener() {
            return firstListener;
        }

        public RemovalStatus unregister() {
            return unregister.get();
        }
    }

    private final ConcurrentHashMap<RegistrationKey, ImmutableSet<EventListener.ReactiveEventListener>> listenersByKey;

    LocalListenerRegistry() {
        this.listenersByKey = new ConcurrentHashMap<>();
    }

    LocalRegistration addListener(RegistrationKey registrationKey, EventListener.ReactiveEventListener listener) {
        AtomicBoolean firstListener = new AtomicBoolean(false);
        listenersByKey.compute(registrationKey, (key, listeners) ->
            Optional.ofNullable(listeners)
                .map(set -> ImmutableSet.<EventListener.ReactiveEventListener>builder().addAll(set).add(listener).build())
                .orElseGet(() -> {
                    firstListener.set(true);
                    return ImmutableSet.of(listener);
                })
        );
        return new LocalRegistration(firstListener.get(), () -> removeListener(registrationKey, listener));
    }

    LocalRegistration addListener(RegistrationKey registrationKey, EventListener listener) {
        return addListener(registrationKey, EventListener.wrapReactive(listener));
    }

    private RemovalStatus removeListener(RegistrationKey registrationKey, EventListener.ReactiveEventListener listener) {
        AtomicBoolean lastListenerRemoved = new AtomicBoolean(false);
        listenersByKey.compute(registrationKey, (key, listeners) -> {
            boolean listenersContainRequested = Optional.ofNullable(listeners).orElse(ImmutableSet.of()).contains(listener);
            if (listenersContainRequested) {
                ImmutableSet<EventListener.ReactiveEventListener> remainingListeners = removeListenerFromSet(listener, listeners);
                if (remainingListeners.isEmpty()) {
                    lastListenerRemoved.set(true);
                    return null;
                }
                return remainingListeners;
            }
            return listeners;
        });
        return lastListenerRemoved::get;
    }

    private ImmutableSet<EventListener.ReactiveEventListener> removeListenerFromSet(EventListener listener, ImmutableSet<EventListener.ReactiveEventListener> listeners) {
        ImmutableSet<EventListener.ReactiveEventListener> remainingListeners = listeners.stream().filter(not(listener::equals)).collect(Guavate.toImmutableSet());
        if (remainingListeners.isEmpty()) {
            return ImmutableSet.of();
        }
        return remainingListeners;
    }

    Flux<EventListener.ReactiveEventListener> getLocalMailboxListeners(RegistrationKey registrationKey) {
        return Flux.fromIterable(listenersByKey.getOrDefault(registrationKey, ImmutableSet.of()));
    }
}