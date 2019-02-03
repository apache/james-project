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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;

import reactor.core.publisher.Flux;

class MailboxListenerRegistry {
    private final Multimap<RegistrationKey, MailboxListener> listeners;

    MailboxListenerRegistry() {
        this.listeners = Multimaps.synchronizedMultimap(HashMultimap.create());
    }

    synchronized void addListener(RegistrationKey registrationKey, MailboxListener listener, Runnable runIfEmpty) {
        if (listeners.get(registrationKey).isEmpty()) {
            runIfEmpty.run();
        }
        listeners.put(registrationKey, listener);
    }

    synchronized void removeListener(RegistrationKey registrationKey, MailboxListener listener, Runnable runIfEmpty) {
        boolean wasRemoved = listeners.remove(registrationKey, listener);
        if (wasRemoved && listeners.get(registrationKey).isEmpty()) {
            runIfEmpty.run();
        }
    }

    Flux<MailboxListener> getLocalMailboxListeners(RegistrationKey registrationKey) {
        return Flux.fromIterable(listeners.get(registrationKey));
    }
}