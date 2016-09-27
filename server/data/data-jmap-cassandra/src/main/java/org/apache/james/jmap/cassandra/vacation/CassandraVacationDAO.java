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

package org.apache.james.jmap.cassandra.vacation;

import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.backends.cassandra.utils.ZonedDateTimeRepresentation;
import org.apache.james.jmap.api.vacation.AccountId;
import org.apache.james.jmap.api.vacation.Vacation;
import org.apache.james.jmap.api.vacation.VacationPatch;
import org.apache.james.jmap.cassandra.vacation.tables.CassandraVacationTable;
import org.apache.james.util.FunctionGenerator;
import org.apache.james.util.ValuePatch;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UDTValue;
import com.datastax.driver.core.UserType;
import com.datastax.driver.core.querybuilder.Insert;
import com.google.common.collect.ImmutableList;

public class CassandraVacationDAO {

    private final CassandraAsyncExecutor cassandraAsyncExecutor;
    private final PreparedStatement readStatement;
    private final UserType zonedDateTimeUserType;
    private final FunctionGenerator<VacationPatch, Insert> insertGeneratorPipeline;

    @Inject
    public CassandraVacationDAO(Session session, CassandraTypesProvider cassandraTypesProvider) {
        this.zonedDateTimeUserType = cassandraTypesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME);
        this.cassandraAsyncExecutor = new CassandraAsyncExecutor(session);

        this.readStatement = session.prepare(select()
            .from(CassandraVacationTable.TABLE_NAME)
            .where(eq(CassandraVacationTable.ACCOUNT_ID,
                bindMarker(CassandraVacationTable.ACCOUNT_ID))));

        insertGeneratorPipeline = ImmutableList.<FunctionGenerator<VacationPatch, Insert>>of(
            patch -> applyPatchForField(CassandraVacationTable.SUBJECT, patch.getSubject()),
            patch -> applyPatchForField(CassandraVacationTable.HTML, patch.getHtmlBody()),
            patch -> applyPatchForField(CassandraVacationTable.TEXT, patch.getTextBody()),
            patch -> applyPatchForField(CassandraVacationTable.IS_ENABLED, patch.getIsEnabled()),
            patch -> applyPatchForFieldZonedDateTime(CassandraVacationTable.FROM_DATE, patch.getFromDate()),
            patch -> applyPatchForFieldZonedDateTime(CassandraVacationTable.TO_DATE, patch.getToDate()))
            .stream()
            .reduce(FunctionGenerator::composeGeneratedFunctions)
            .get();
    }

    public CompletableFuture<Void> modifyVacation(AccountId accountId, VacationPatch vacationPatch) {
        return cassandraAsyncExecutor.executeVoid(
            createSpecificUpdate(vacationPatch,
                insertInto(CassandraVacationTable.TABLE_NAME)
                    .value(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier())));
    }

    public CompletableFuture<Optional<Vacation>> retrieveVacation(AccountId accountId) {
        return cassandraAsyncExecutor.executeSingleRow(readStatement.bind()
            .setString(CassandraVacationTable.ACCOUNT_ID, accountId.getIdentifier()))
            .thenApply(optional -> optional.map(row -> Vacation.builder()
                .enabled(row.getBool(CassandraVacationTable.IS_ENABLED))
                .fromDate(retrieveDate(row, CassandraVacationTable.FROM_DATE))
                .toDate(retrieveDate(row, CassandraVacationTable.TO_DATE))
                .subject(Optional.ofNullable(row.getString(CassandraVacationTable.SUBJECT)))
                .textBody(Optional.ofNullable(row.getString(CassandraVacationTable.TEXT)))
                .htmlBody(Optional.ofNullable(row.getString(CassandraVacationTable.HTML)))
                .build()));
    }

    private Optional<ZonedDateTime> retrieveDate(Row row, String dateField) {
        return Optional.ofNullable(row.getUDTValue(dateField))
            .map(udtValue -> ZonedDateTimeRepresentation.fromDate(
                udtValue.getDate(CassandraZonedDateTimeModule.DATE),
                udtValue.getString(CassandraZonedDateTimeModule.TIME_ZONE))
                .getZonedDateTime());
    }

    private Insert createSpecificUpdate(VacationPatch vacationPatch, Insert baseInsert) {
        return insertGeneratorPipeline
            .apply(vacationPatch)
            .apply(baseInsert);
    }

    public <T> Function<Insert, Insert> applyPatchForField(String field, ValuePatch<T> valuePatch) {
        return valuePatch.mapNotKeptToOptional(optionalValue -> applyPatchForField(field, optionalValue))
            .orElse(Function.identity());
    }

    public Function<Insert, Insert> applyPatchForFieldZonedDateTime(String field, ValuePatch<ZonedDateTime> valuePatch) {
        return valuePatch.mapNotKeptToOptional(optionalValue -> applyPatchForField(field, convertToUDTOptional(optionalValue)))
            .orElse(Function.identity());
    }

    private <T> Function<Insert, Insert> applyPatchForField(String field, Optional<T> value) {
        return insert -> insert.value(field, value.orElse(null));
    }

    private Optional<UDTValue> convertToUDTOptional(Optional<ZonedDateTime> zonedDateTimeOptional) {
        return zonedDateTimeOptional.map(ZonedDateTimeRepresentation::fromZonedDateTime)
            .map(representation -> zonedDateTimeUserType.newValue()
                .setDate(CassandraZonedDateTimeModule.DATE, representation.getDate())
                .setString(CassandraZonedDateTimeModule.TIME_ZONE, representation.getSerializedZoneId()));
    }
}
