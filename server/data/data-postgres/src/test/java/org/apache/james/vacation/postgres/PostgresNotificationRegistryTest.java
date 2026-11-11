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

import static org.apache.james.vacation.postgres.PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.TABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.apache.james.backends.postgres.PostgresDataDefinition;
import org.apache.james.backends.postgres.PostgresExtension;
import org.apache.james.core.MailAddress;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.NotificationRegistry;
import org.apache.james.vacation.api.NotificationRegistryContract;
import org.apache.james.vacation.api.RecipientId;
import org.jooq.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import reactor.core.publisher.Mono;

class PostgresNotificationRegistryTest implements NotificationRegistryContract {
    static final AccountId OTHER_ACCOUNT_ID = AccountId.fromString("other");
    static final Field<String> ACCOUNT_ID_COLUMN = PostgresVacationDataDefinition.PostgresVacationNotificationRegistryTable.ACCOUNT_ID;

    @RegisterExtension
    static PostgresExtension postgresExtension = PostgresExtension.withoutRowLevelSecurity(PostgresDataDefinition.aggregateModules(PostgresVacationDataDefinition.MODULE));

    NotificationRegistry notificationRegistry;
    RecipientId recipientId;

    @BeforeEach
    public void setUp() throws Exception {
        notificationRegistry = new PostgresNotificationRegistry(zonedDateTimeProvider, postgresExtension.getExecutorFactory());
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
    void registerShouldDropTheExpiredEntriesOfTheAccount() throws Exception {
        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME);
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("first@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("second@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();
        notificationRegistry.register(OTHER_ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("first@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_4_SECONDS)).block();

        when(zonedDateTimeProvider.get()).thenReturn(ZONED_DATE_TIME_PLUS_8_SECONDS);
        notificationRegistry.register(ACCOUNT_ID, RecipientId.fromMailAddress(new MailAddress("third@domain.tld")), Optional.of(ZONED_DATE_TIME_PLUS_8_SECONDS.plusDays(1))).block();

        assertThat(registrationCount(ACCOUNT_ID)).isEqualTo(1);
        assertThat(registrationCount(OTHER_ACCOUNT_ID)).isEqualTo(1);
    }

    private int registrationCount(AccountId accountId) {
        return postgresExtension.getDefaultPostgresExecutor()
            .executeCount(dsl -> Mono.from(dsl.selectCount()
                .from(TABLE_NAME)
                .where(ACCOUNT_ID_COLUMN.eq(accountId.getIdentifier()))))
            .block();
    }
}
