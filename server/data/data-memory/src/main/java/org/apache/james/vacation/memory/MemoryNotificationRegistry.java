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

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.Ticker;
import com.google.common.annotations.VisibleForTesting;

import reactor.core.publisher.Mono;

public class MemoryNotificationRegistry implements NotificationRegistry {
    // Evicting a live registration only means one more vacation reply to that sender
    private static final long MAX_REGISTRATIONS = 100_000;
    private static final Duration MAX_TIME_TO_LIVE = Duration.ofNanos(Long.MAX_VALUE);

    private record Registration(AccountId accountId, RecipientId recipientId) {
    }

    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final Cache<Registration, Optional<ZonedDateTime>> registrations;

    @Inject
    public MemoryNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider) {
        this(zonedDateTimeProvider, Ticker.systemTicker(), MAX_REGISTRATIONS);
    }

    @VisibleForTesting
    MemoryNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider, Ticker ticker, long maxRegistrations) {
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.registrations = Caffeine.newBuilder()
            .ticker(ticker)
            .maximumSize(maxRegistrations)
            .expireAfter(Expiry.writing((Registration registration, Optional<ZonedDateTime> registrationEnd) -> timeToLive(registrationEnd)))
            .build();
    }

    private Duration timeToLive(Optional<ZonedDateTime> registrationEnd) {
        return registrationEnd
            .map(end -> Duration.between(zonedDateTimeProvider.get(), end))
            .map(duration -> duration.isNegative() ? Duration.ZERO : duration)
            .filter(duration -> duration.compareTo(MAX_TIME_TO_LIVE) < 0)
            .orElse(MAX_TIME_TO_LIVE);
    }

    @Override
    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        registrations.put(new Registration(accountId, recipientId), expiryDate);
        return Mono.empty();
    }

    @Override
    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        return Mono.just(Optional.ofNullable(registrations.getIfPresent(new Registration(accountId, recipientId)))
            .filter(registrationEnd -> !isExpired(zonedDateTimeProvider.get(), registrationEnd))
            .isPresent());
    }

    private boolean isExpired(ZonedDateTime currentTime, Optional<ZonedDateTime> registrationEnd) {
        return registrationEnd.map(end -> !currentTime.isBefore(end)).orElse(false);
    }

    @VisibleForTesting
    long registrationCount(AccountId accountId) {
        registrations.cleanUp();
        return registrations.asMap().keySet().stream()
            .filter(registration -> registration.accountId().equals(accountId))
            .count();
    }

    @Override
    public Mono<Void> flush(AccountId accountId) {
        registrations.asMap().keySet().removeIf(registration -> registration.accountId().equals(accountId));
        return Mono.empty();
    }
}
