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

package org.apache.james.vacation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.jupiter.api.Test;

public interface NotificationRegistryContract {
    ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.parse("2016-04-03T02:01:01+07:00[Asia/Vientiane]");
    ZonedDateTime ZONED_DATE_TIME_PLUS_4_SECONDS = ZonedDateTime.parse("2016-04-03T02:01:05+07:00[Asia/Vientiane]");
    ZonedDateTime ZONED_DATE_TIME_PLUS_8_SECONDS = ZonedDateTime.parse("2016-04-03T02:01:09+07:00[Asia/Vientiane]");
    AccountId ACCOUNT_ID = AccountId.fromString("id");

    ZonedDateTimeProvider zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
    
    NotificationRegistry notificationRegistry();
    RecipientId recipientId();

    @Test
    default void isRegisterShouldReturnFalseByDefault() {
        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isFalse();
    }

    @Test
    default void registerShouldWork() {
        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.empty()).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isTrue();
    }

    @Test
    default void registerShouldWorkWithExpiracyDate() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isTrue();
    }

    @Test
    default void registerShouldExpireAfterExpiracyDate() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();

        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_PLUS_8_SECONDS);

        await().atMost(20, TimeUnit.SECONDS).until(() -> !notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block());
    }

    @Test
    default void flushShouldWork() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.empty()).block();

        notificationRegistry().flush(ACCOUNT_ID).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isFalse();
    }

    @Test
    default void registerShouldNotPersistWhenExpiryDateIsPast() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_PLUS_4_SECONDS);

        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.of(ZONED_DATE_TIME)).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isFalse();
    }

    @Test
    default void registerShouldNotPersistWhenExpiryDateIsPresent() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.of(ZONED_DATE_TIME)).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isTrue();
    }

    @Test
    default void isRegisteredShouldIgnoreCase() {
        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.empty()).block();

        AccountId upperCaseAccount = AccountId.fromString(ACCOUNT_ID.getIdentifier().toUpperCase());
        assertThat(notificationRegistry().isRegistered(upperCaseAccount, recipientId()).block()).isTrue();
    }

    @Test
    default void registerShouldIgnoreCase() {
        AccountId upperCaseAccount = AccountId.fromString(ACCOUNT_ID.getIdentifier().toUpperCase());
        notificationRegistry().register(upperCaseAccount, recipientId(), Optional.empty()).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isTrue();
    }

    @Test
    default void flushShouldIgnoreCase() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry().register(ACCOUNT_ID, recipientId(), Optional.empty()).block();

        AccountId upperCaseAccount = AccountId.fromString(ACCOUNT_ID.getIdentifier().toUpperCase());
        notificationRegistry().flush(upperCaseAccount).block();

        assertThat(notificationRegistry().isRegistered(ACCOUNT_ID, recipientId()).block()).isFalse();
    }
}
