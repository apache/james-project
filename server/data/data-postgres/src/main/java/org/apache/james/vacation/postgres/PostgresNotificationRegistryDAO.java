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

import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationNotificationRegistryTable.ACCOUNT_ID;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationNotificationRegistryTable.EXPIRY_DATE;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationNotificationRegistryTable.RECIPIENT_ID;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationNotificationRegistryTable.TABLE_NAME;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Optional;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.util.date.ZonedDateTimeProvider;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.RecipientId;

import reactor.core.publisher.Mono;

public class PostgresNotificationRegistryDAO {
    private final PostgresExecutor postgresExecutor;
    private final ZonedDateTimeProvider zonedDateTimeProvider;

    public PostgresNotificationRegistryDAO(PostgresExecutor postgresExecutor,
                                           ZonedDateTimeProvider zonedDateTimeProvider) {
        this.postgresExecutor = postgresExecutor;
        this.zonedDateTimeProvider = zonedDateTimeProvider;
    }

    public Mono<Void> register(AccountId accountId, RecipientId recipientId, Optional<ZonedDateTime> expiryDate) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.insertInto(TABLE_NAME)
            .set(ACCOUNT_ID, accountId.getIdentifier())
            .set(RECIPIENT_ID, recipientId.getAsString())
            .set(EXPIRY_DATE, expiryDate.map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime())
                .orElse(null))));
    }

    public Mono<Boolean> isRegistered(AccountId accountId, RecipientId recipientId) {
        LocalDateTime currentUTCTime = zonedDateTimeProvider.get().withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();

        return postgresExecutor.executeRow(dsl -> Mono.from(dsl.select(ACCOUNT_ID)
            .from(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()),
                RECIPIENT_ID.eq(recipientId.getAsString()),
                EXPIRY_DATE.ge(currentUTCTime).or(EXPIRY_DATE.isNull()))))
            .hasElement();
    }

    public Mono<Void> flush(AccountId accountId) {
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(ACCOUNT_ID.eq(accountId.getIdentifier()))));
    }
}
