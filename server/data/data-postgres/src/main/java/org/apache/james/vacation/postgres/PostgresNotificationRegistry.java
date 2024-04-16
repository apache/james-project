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

package org.apache.james.vacation.postgres;

import java.time.ZonedDateTime;
import java.util.Optional;

import jakarta.inject.Inject;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Username;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import reactor.core.publisher.Mono;

public class PostgresNotificationRegistry implements NotificationRegistry {
    private static final Logger LOGGER = LoggerFactory.getLogger(PostgresNotificationRegistry.class);

    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final PostgresExecutor.Factory executorFactory;

    @Inject
    public PostgresNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider,
                                        PostgresExecutor.Factory executorFactory) {
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.executorFactory = executorFactory;
    }

    @Override
    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        if (isValid(expiryDate)) {
            return notificationRegistryDAO(accountId).register(accountId, recipientId, expiryDate);
        } else {
            LOGGER.warn("Invalid vacation notification expiry date for {} {} : {}", accountId, recipientId, expiryDate);
            return Mono.empty();
        }
    }

    @Override
    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        return notificationRegistryDAO(accountId).isRegistered(accountId, recipientId);
    }

    @Override
    public Mono<Void> flush(AccountId accountId) {
        return notificationRegistryDAO(accountId).flush(accountId);
    }

    private boolean isValid(Optional<ZonedDateTime> expiryDate) {
        return expiryDate.isEmpty() || expiryDate.get().isAfter(zonedDateTimeProvider.get());
    }

    private PostgresNotificationRegistryDAO notificationRegistryDAO(AccountId accountId) {
        return new PostgresNotificationRegistryDAO(executorFactory.create(Username.of(accountId.getIdentifier()).getDomainPart()),
            zonedDateTimeProvider);
    }
}
