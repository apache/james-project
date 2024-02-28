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

package org.apache.james.vacation.cassandra;

import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import javax.inject.Inject;

import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.RecipientId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.primitives.Ints;

import reactor.core.publisher.Mono;

public class CassandraNotificationRegistry implements NotificationRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(CassandraNotificationRegistry.class);

    private final ZonedDateTimeProvider zonedDateTimeProvider;
    private final CassandraNotificationRegistryDAO cassandraNotificationRegistryDAO;

    @Inject
    public CassandraNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider, CassandraNotificationRegistryDAO cassandraNotificationRegistryDAO) {
        this.zonedDateTimeProvider = zonedDateTimeProvider;
        this.cassandraNotificationRegistryDAO = cassandraNotificationRegistryDAO;
    }

    @Override
    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        Optional<Integer> waitDelay = evaluateWaitDelay(expiryDate);
        if (isValid(waitDelay)) {
            return cassandraNotificationRegistryDAO.register(accountId, recipientId, waitDelay);
        } else {
            LOGGER.warn("Invalid wait delay for {} {} : {}", accountId, recipientId, waitDelay);
            return Mono.empty();
        }
    }

    private Optional<Integer> evaluateWaitDelay(Optional<ZonedDateTime> expiryDate) {
        return expiryDate.map(expiry -> zonedDateTimeProvider.get().until(expiry, ChronoUnit.SECONDS))
            .flatMap(longValue -> {
                if (longValue >= Integer.MAX_VALUE || longValue <= Integer.MIN_VALUE) {
                    return Optional.empty();
                }
                return Optional.of(Ints.checkedCast(longValue));
            });
    }

    @Override
    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        return cassandraNotificationRegistryDAO.isRegistered(accountId, recipientId);
    }

    @Override
    public Mono<Void> flush(AccountId accountId) {
        return cassandraNotificationRegistryDAO.flush(accountId);
    }

    private boolean isValid(Optional<Integer> waitDelay) {
        return waitDelay.isEmpty() || waitDelay.get() >= 0;
    }
}
