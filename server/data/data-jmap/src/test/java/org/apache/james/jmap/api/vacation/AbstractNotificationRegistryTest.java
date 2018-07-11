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

package org.apache.james.jmap.api.vacation;

import static com.jayway.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.james.core.MailAddress;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.junit.Before;
import org.junit.Test;

public abstract class AbstractNotificationRegistryTest {

    public static final ZonedDateTime ZONED_DATE_TIME = ZonedDateTime.parse("2016-04-03T02:01:01+07:00[Asia/Vientiane]");
    public static final ZonedDateTime ZONED_DATE_TIME_PLUS_4_SECONDS = ZonedDateTime.parse("2016-04-03T02:01:05+07:00[Asia/Vientiane]");
    public static final ZonedDateTime ZONED_DATE_TIME_PLUS_8_SECONDS = ZonedDateTime.parse("2016-04-03T02:01:09+07:00[Asia/Vientiane]");
    public static final AccountId ACCOUNT_ID = AccountId.fromString("id");

    private NotificationRegistry notificationRegistry;
    private ZonedDateTimeProvider zonedDateTimeProvider;
    private RecipientId recipientId;

    protected abstract NotificationRegistry createNotificationRegistry(ZonedDateTimeProvider zonedDateTimeProvider);

    @Before
    public void setUp() throws Exception {
        zonedDateTimeProvider = mock(ZonedDateTimeProvider.class);
        notificationRegistry = createNotificationRegistry(zonedDateTimeProvider);
        recipientId = RecipientId.fromMailAddress(new MailAddress("benwa@apache.org"));
    }

    @Test
    public void isRegisterShouldReturnFalseByDefault() {
        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isFalse();
    }

    @Test
    public void registerShouldWork() {
        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.empty()).join();

        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isTrue();
    }

    @Test
    public void registerShouldWorkWithExpiracyDate() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).join();

        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isTrue();
    }

    @Test
    public void registerShouldExpireAfterExpiracyDate() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).join();

        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_PLUS_8_SECONDS);

        await().atMost(20, TimeUnit.SECONDS).until(() -> !notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join());
    }

    @Test
    public void flushShouldWork() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.empty()).join();

        notificationRegistry.flush(ACCOUNT_ID).join();

        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isFalse();
    }

    @Test
    public void registerShouldNotPersistWhenExpiryDateIsPast() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_PLUS_4_SECONDS);

        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(ZONED_DATE_TIME)).join();

        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isFalse();
    }

    @Test
    public void registerShouldNotPersistWhenExpiryDateIsPresent() {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.of(ZONED_DATE_TIME)).join();

        assertThat(notificationRegistry.isRegistered(ACCOUNT_ID, recipientId).join()).isTrue();
    }
}
