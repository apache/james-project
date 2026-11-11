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

package org.apache.james.vacation.memory;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;

import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class MemoryNotificationRegistry implements NotificationRegistry {
    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final Map<AccountId, Map<RecipientId, Optional<ZonedDateTime>>> registrations;

    @Inject
    public MemoryNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider) {
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.registrations = new HashMap<>();
    }

    @Override
    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        ZonedDateTime currentTime = zonedDateTimeProvider.get();
        synchronized (registrations) {
            Map<RecipientId, Optional<ZonedDateTime>> accountRegistrations = registrations.computeIfAbsent(accountId, any -> new HashMap<>());
            // Nothing else removes them: without this, every sender ever answered would stay until a flush
            accountRegistrations.values().removeIf(registrationEnd -> isExpired(currentTime, registrationEnd));
            accountRegistrations.put(recipientId, expiryDate);
        }
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        ZonedDateTime currentTime = zonedDateTimeProvider.get();
        synchronized (registrations) {
            return Mono.just(Optional.ofNullable(registrations.get(accountId))
                .map(accountRegistrations -> accountRegistrations.get(recipientId))
                .filter(registrationEnd -> !isExpired(currentTime, registrationEnd))
                .isPresent());
        }
    }

    private boolean isExpired(ZonedDateTime currentTime, Optional<ZonedDateTime> registrationEnd) {
        return registrationEnd.map(end -> !currentTime.isBefore(end)).orElse(false);
    }

    @VisibleForTesting
    int registrationCount(AccountId accountId) {
        synchronized (registrations) {
            return Optional.ofNullable(registrations.get(accountId))
                .map(Map::size)
                .orElse(0);
        }
    }

    @Override
    public Mono<Void> flush(AccountId accountId) {
        synchronized (registrations) {
            registrations.remove(accountId);
        }
        return Mono.empty();
    }
}
