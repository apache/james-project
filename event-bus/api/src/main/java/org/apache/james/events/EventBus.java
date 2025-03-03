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

import java.util.Collection;
import java.util.Set;

import org.reactivestreams.Publisher;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface EventBus {
    int EXECUTION_RATE = 10;

    interface StructuredLoggingFields {
        String EVENT_ID = "eventId";
        String EVENT_CLASS = "eventClass";
        String LISTENER_CLASS = "listenerClass";
        String USER = "user";
        String GROUP = "group";
        String REGISTRATION_KEYS = "registrationKeys";
        String REGISTRATION_KEY = "registrationKey";
    }

    interface Metrics {
        static String timerName(EventListener listener) {
            return "mailbox-listener-" + listener.getClass().getSimpleName();
        }
    }

    default Publisher<Registration> register(EventListener listener, RegistrationKey key) {
        return register(EventListener.wrapReactive(listener), key);
    }

    Publisher<Registration> register(EventListener.ReactiveEventListener listener, RegistrationKey key);

    default Publisher<Registration> register(EventListener.ReactiveEventListener listener, Collection<RegistrationKey> keys) {
        return Flux.fromIterable(keys)
            .concatMap(key -> register(listener, key))
            .reduce((reg1, reg2) -> () -> Flux.merge(reg1.unregister(), reg2.unregister()))
            .map(unRegistrationWithMergedFlux -> () -> Mono.from(Flux.from(unRegistrationWithMergedFlux.unregister())
                .then()));
    }

    Registration register(EventListener.ReactiveEventListener listener, Group group) throws GroupAlreadyRegistered;

    default Registration register(EventListener listener, Group group) throws GroupAlreadyRegistered {
        return register(EventListener.wrapReactive(listener), group);
    }

    Mono<Void> dispatch(Event event, Set<RegistrationKey> key);

    default Mono<Void> dispatch(Event event, RegistrationKey key) {
        return dispatch(event, ImmutableSet.of(key));
    }

    record EventWithRegistrationKey(Event event, Set<RegistrationKey> keys) {

    }

    default Mono<Void> dispatch(Collection<EventWithRegistrationKey> events) {
        return Flux.fromIterable(events)
            .concatMap(event -> dispatch(event.event(), event.keys()))
            .then();
    }

    Mono<Void> reDeliver(Group group, Event event);

    EventBusName eventBusName();

    default Registration register(EventListener.GroupEventListener groupListener) {
        return register(EventListener.wrapReactive(groupListener));
    }

    default Registration register(EventListener.ReactiveGroupEventListener groupListener) {
        return register(groupListener, groupListener.getDefaultGroup());
    }

    default Collection<Group> listRegisteredGroups() {
        return ImmutableList.of();
    }

    default void start() {
    }

    default void restart() {
    }

    default void stop() {
    }
}
