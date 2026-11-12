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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.james.core.MailAddress;
import org.apache.james.util.concurrency.ConcurrentTestRunner;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.NotificationRegistryContract;
import org.apache.james.vacation.api.RecipientId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MemoryNotificationRegistryTest implements NotificationRegistryContract {
    static final int THREAD_COUNT = 10;
    static final int OPERATION_COUNT = 100;

    MemoryNotificationRegistry notificationRegistry;
    RecipientId recipientId;
    AtomicLong tickerNanos;

    @BeforeEach
    void setup() throws Exception {
        tickerNanos = new AtomicLong();
        notificationRegistry = new MemoryNotificationRegistry(zonedDateTimeProvider, tickerNanos::get, 100);
        recipientId = RecipientId.fromMailAddress(new MailAddress("benwa@apache.org"));
    }

    @Override
    public NotificationRegistry notificationRegistry() {
        return notificationRegistry;
    }

    @Override
    public RecipientId recipientId() {
        return recipientId;
    }

    @Test
    void expiredRegistrationsShouldBeEvicted() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("first@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("second@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("third@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_8_SECONDS)).block();

        tickerNanos.addAndGet(Duration.ofSeconds(6).toNanos());

        assertThat(notificationRegistry.registrationCount(ACCOUNT_ID)).isEqualTo(1);
    }

    @Test
    void registrationsWithoutEndDateShouldNotBeEvictedOverTime() {
        notificationRegistry.register(ACCOUNT_ID, recipientId, Optional.empty()).block();

        tickerNanos.addAndGet(Duration.ofDays(365 * 100).toNanos());

        assertThat(notificationRegistry.registrationCount(ACCOUNT_ID)).isEqualTo(1);
    }

    @Test
    void registrationsShouldBeBounded() throws Exception {
        for (int index = 0; index < 1000; index++) {
            notificationRegistry.register(ACCOUNT_ID, sender(0, index), Optional.empty()).block();
        }

        assertThat(notificationRegistry.registrationCount(ACCOUNT_ID)).isLessThanOrEqualTo(100);
    }

    @Test
    void concurrentRegistrationsShouldAllBeKept() throws Exception {
        MemoryNotificationRegistry unboundedRegistry = new MemoryNotificationRegistry(zonedDateTimeProvider);
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        ConcurrentTestRunner.builder()
            .reactorOperation((thread, step) -> unboundedRegistry.register(ACCOUNT_ID, sender(thread, step), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)))
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        assertThat(unboundedRegistry.registrationCount(ACCOUNT_ID)).isEqualTo(THREAD_COUNT * OPERATION_COUNT);
        for (int thread = 0; thread < THREAD_COUNT; thread++) {
            for (int step = 0; step < OPERATION_COUNT; step++) {
                assertThat(unboundedRegistry.isRegistered(ACCOUNT_ID, sender(thread, step)).block()).isTrue();
            }
        }
    }

    @Test
    void concurrentRegisterLookupAndFlushShouldNotFail() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);

        ConcurrentTestRunner.builder()
            .randomlyDistributedReactorOperations(
                (thread, step) -> notificationRegistry.register(ACCOUNT_ID, sender(thread, step), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)),
                (thread, step) -> notificationRegistry.isRegistered(ACCOUNT_ID, sender(thread, step)).then(),
                (thread, step) -> notificationRegistry.flush(ACCOUNT_ID))
            .threadCount(THREAD_COUNT)
            .operationCount(OPERATION_COUNT)
            .runSuccessfullyWithin(Duration.ofMinutes(1));

        notificationRegistry.flush(ACCOUNT_ID).block();
        assertThat(notificationRegistry.registrationCount(ACCOUNT_ID)).isZero();
    }

    private RecipientId sender(int thread, int step) throws Exception {
        return RecipientId.fromMailAddress(new MailAddress("sender-" + thread + "-" + step + "@domain.tld"));
    }
}
