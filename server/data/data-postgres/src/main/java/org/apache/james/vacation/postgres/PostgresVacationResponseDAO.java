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

import static org.apache.james.backends.postgres.PostgresCommons.LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.ACCOUNT_ID;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.FROM_DATE;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.HTML;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.IS_ENABLED;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.SUBJECT;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.TABLE_NAME;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.TEXT;
import static org.apache.james.vacation.postgres.PostgresVacationModule.PostgresVacationResponseTable.TO_DATE;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.stream.Stream;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.util.ValuePatch;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertOnDuplicateSetMoreStep;
import org.jooq.InsertOnDuplicateSetStep;
import org.jooq.InsertSetMoreStep;
import org.jooq.Record;

import reactor.core.publisher.Mono;

public class PostgresVacationResponseDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresVacationResponseDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        return postgresExecutor.executeVoid(dsl -> {
            if (vacationPatch.isIdentity()) {
                return Mono.from(insertVacationQuery(accountId, vacationPatch, dsl)
                    .onConflictDoNothing());
            } else {
                return Mono.from(withUpdateOnConflict(vacationPatch, insertVacationQuery(accountId, vacationPatch, dsl)));
            }
        });
    }

    private InsertSetMoreStep<Record> insertVacationQuery(AccountId accountId, VacationPatch vacationPatch, DSLContext dsl) {
        InsertSetMoreStep<Record> baseInsert = dsl.insertInto(TABLE_NAME)
            .set(ACCOUNT_ID, accountId.getIdentifier());

        return Stream.of(
                applyInsertForField(IS_ENABLED, VacationPatch::getIsEnabled),
                applyInsertForField(SUBJECT, VacationPatch::getSubject),
                applyInsertForField(HTML, VacationPatch::getHtmlBody),
                applyInsertForField(TEXT, VacationPatch::getTextBody),
                applyInsertForFieldZonedDateTime(FROM_DATE, VacationPatch::getFromDate),
                applyInsertForFieldZonedDateTime(TO_DATE, VacationPatch::getToDate))
            .reduce((vacation, insert) -> insert,
                (a, b) -> (vacation, insert) -> b.apply(vacation, a.apply(vacation, insert)))
            .apply(vacationPatch, baseInsert);
    }

    private InsertOnDuplicateSetMoreStep<Record> withUpdateOnConflict(VacationPatch vacationPatch, InsertSetMoreStep<Record> insertVacation) {
        InsertOnDuplicateSetStep<Record> baseUpdateIfConflict = insertVacation.onConflict(ACCOUNT_ID)
            .doUpdate();

        return (InsertOnDuplicateSetMoreStep<Record>) Stream.of(
                applyUpdateOnConflictForField(IS_ENABLED, VacationPatch::getIsEnabled),
                applyUpdateOnConflictForField(SUBJECT, VacationPatch::getSubject),
                applyUpdateOnConflictForField(HTML, VacationPatch::getHtmlBody),
                applyUpdateOnConflictForField(TEXT, VacationPatch::getTextBody),
                applyUpdateOnConflictForFieldZonedDateTime(FROM_DATE, VacationPatch::getFromDate),
                applyUpdateOnConflictForFieldZonedDateTime(TO_DATE, VacationPatch::getToDate))
            .reduce((vacation, updateOnConflict) -> updateOnConflict,
                (a, b) -> (vacation, updateOnConflict) -> b.apply(vacation, a.apply(vacation, updateOnConflict)))
            .apply(vacationPatch, baseUpdateIfConflict);
    }

    private <F, V> BiFunction<VacationPatch, InsertSetMoreStep<Record>, InsertSetMoreStep<Record>> applyInsertForField(Field<F> field, Function<VacationPatch, ValuePatch<V>> getter) {
        return (vacation, insert) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyInsertForField(field, optionalValue, insert))
                .orElse(insert);
    }

    private <F> BiFunction<VacationPatch, InsertSetMoreStep<Record>, InsertSetMoreStep<Record>> applyInsertForFieldZonedDateTime(Field<F> field, Function<VacationPatch, ValuePatch<ZonedDateTime>> getter) {
        return (vacation, insert) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyInsertForField(field,
                    optionalValue.map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()),
                    insert))
                .orElse(insert);
    }

    private <T> InsertSetMoreStep<Record> applyInsertForField(Field field, Optional<T> value, InsertSetMoreStep<Record> insert) {
        return insert.set(field, value.orElse(null));
    }

    private <F, V> BiFunction<VacationPatch, InsertOnDuplicateSetStep<Record>, InsertOnDuplicateSetStep<Record>> applyUpdateOnConflictForField(Field<F> field, Function<VacationPatch, ValuePatch<V>> getter) {
        return (vacation, update) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyUpdateOnConflictForField(field, optionalValue, update))
                .orElse(update);
    }

    private <F> BiFunction<VacationPatch, InsertOnDuplicateSetStep<Record>, InsertOnDuplicateSetStep<Record>> applyUpdateOnConflictForFieldZonedDateTime(Field<F> field, Function<VacationPatch, ValuePatch<ZonedDateTime>> getter) {
        return (vacation, update) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyUpdateOnConflictForField(field,
                    optionalValue.map(zonedDateTime -> zonedDateTime.withZoneSameInstant(ZoneId.of("UTC")).toLocalDateTime()),
                    update))
                .orElse(update);
    }

    private <T> InsertOnDuplicateSetStep<Record> applyUpdateOnConflictForField(Field field, Optional<T> value, InsertOnDuplicateSetStep<Record> updateOnConflict) {
        return updateOnConflict.set(field, value.orElse(null));
    }

    public Mono<Optional<Vacation>> retrieveVacation(AccountId accountId) {
        return postgresExecutor.executeSingleRowOptional(dsl -> dsl.selectFrom(TABLE_NAME)
                .where(ACCOUNT_ID.eq(accountId.getIdentifier())))
            .map(recordOptional -> recordOptional.map(record -> Vacation.builder()
                .enabled(record.get(IS_ENABLED))
                .fromDate(Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(FROM_DATE, LocalDateTime.class))))
                .toDate(Optional.ofNullable(LOCAL_DATE_TIME_ZONED_DATE_TIME_FUNCTION.apply(record.get(TO_DATE, LocalDateTime.class))))
                .subject(Optional.ofNullable(record.get(SUBJECT)))
                .textBody(Optional.ofNullable(record.get(TEXT)))
                .htmlBody(Optional.ofNullable(record.get(HTML)))
                .build()));
    }
}