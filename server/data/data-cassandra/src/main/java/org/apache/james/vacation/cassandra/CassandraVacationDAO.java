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


import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.literal;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.util.ValuePatch;
import org.apache.james.vacation.api.AccountId;
import org.apache.james.vacation.api.Vacation;
import org.apache.james.vacation.api.VacationPatch;
import org.apache.james.vacation.cassandra.tables.CassandraVacationTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.core.type.UserDefinedType;
import com.datastax.oss.driver.api.querybuilder.insert.RegularInsert;
import com.google.common.collect.ImmutableList;

import reactor.core.publisher.Mono;

public class CassandraVacationDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement readStatement;
    private final UserDefinedType zonedDateTimeUserType;
    private final BiFunction<VacationPatch, RegularInsert, RegularInsert> insertGeneratorPipeline;

    @Inject
    public CassandraVacationDAO(CqlSession session, CassandraTypesProvider cassandraTypesProvider) {
        this.zonedDateTimeUserType = cassandraTypesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME);
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.readStatement = session.prepare(selectFrom(CassandraVacationTable.TABLE_NAME)
            .all()
            .whereColumn(CassandraVacationTable.ACCOUNT_ID).isEqualTo(bindMarker(CassandraVacationTable.ACCOUNT_ID))
            .build());

        insertGeneratorPipeline = ImmutableList.of(
                applyPatchForField(CassandraVacationTable.SUBJECT, VacationPatch::getSubject),
                applyPatchForField(CassandraVacationTable.HTML, VacationPatch::getHtmlBody),
                applyPatchForField(CassandraVacationTable.TEXT, VacationPatch::getTextBody),
                applyPatchForField(CassandraVacationTable.IS_ENABLED, VacationPatch::getIsEnabled),
                applyPatchForFieldZonedDateTime(CassandraVacationTable.FROM_DATE, VacationPatch::getFromDate),
                applyPatchForFieldZonedDateTime(CassandraVacationTable.TO_DATE, VacationPatch::getToDate))
            .stream()
            .reduce((vacation, insert) -> insert,
                (a, b) -> (vacation, insert) -> b.apply(vacation, a.apply(vacation, insert)));
    }

    public Mono<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        return cassandraAsyncExecutor.executeVoid(
            createSpecificUpdate(vacationPatch,
                insertInto(CassandraVacationTable.TABLE_NAME)
                    .value(CassandraVacationTable.ACCOUNT_ID, literal(accountId.getIdentifier())))
                .build());
    }

    public Mono<Optional<Vacation>> retrieveVacation(AccountId accountId) {
        return cassandraAsyncExecutor.executeSingleRowOptional(readStatement.bind()
                .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier()))
            .map(optional -> optional.map(row -> Vacation.builder()
                .enabled(row.getBoolean(CassandraVacationTable.IS_ENABLED))
                .fromDate(retrieveDate(row, CassandraVacationTable.FROM_DATE))
                .toDate(retrieveDate(row, CassandraVacationTable.TO_DATE))
                .subject(Optional.ofNullable(row.getString(CassandraVacationTable.SUBJECT)))
                .textBody(Optional.ofNullable(row.getString(CassandraVacationTable.TEXT)))
                .htmlBody(Optional.ofNullable(row.getString(CassandraVacationTable.HTML)))
                .build()));
    }

    private Optional<ZonedDateTime> retrieveDate(Row row, String dateField) {
        return CassandraZonedDateTimeModule.fromUDTOptional(row.getUdtValue(dateField));
    }

    private RegularInsert createSpecificUpdate(VacationPatch vacationPatch, RegularInsert baseInsert) {
        return insertGeneratorPipeline.apply(vacationPatch, baseInsert);
    }

    public <T> BiFunction<VacationPatch, RegularInsert, RegularInsert> applyPatchForField(String field, Function<VacationPatch, ValuePatch<T>> getter) {
        return (vacation, insert) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyPatchForField(field, optionalValue, insert))
                .orElse(insert);
    }

    public BiFunction<VacationPatch, RegularInsert, RegularInsert> applyPatchForFieldZonedDateTime(String field, Function<VacationPatch, ValuePatch<ZonedDateTime>> getter) {
        return (vacation, insert) ->
            getter.apply(vacation)
                .mapNotKeptToOptional(optionalValue -> applyPatchForField(field, CassandraZonedDateTimeModule.toUDT(zonedDateTimeUserType, optionalValue), insert))
                .orElse(insert);
    }

    private <T> RegularInsert applyPatchForField(String field, Optional<T> value, RegularInsert insert) {
        return insert.value(field, literal(value.orElse(null)));
    }
}
