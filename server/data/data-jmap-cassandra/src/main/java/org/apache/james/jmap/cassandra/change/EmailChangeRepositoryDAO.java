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

package org.apache.james.jmap.cassandra.change;

import static com.datastax.driver.core.querybuilder.QueryBuilder.asc;
import static com.datastax.driver.core.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.driver.core.querybuilder.QueryBuilder.desc;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.ACCOUNT_ID;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.CREATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.DATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.DESTROYED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.IS_DELEGATED;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.STATE;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.TABLE_NAME;
import static org.apache.james.jmap.cassandra.change.tables.CassandraEmailChangeTable.UPDATED;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.init.CassandraTypesProvider;
import org.apache.james.backends.cassandra.init.CassandraZonedDateTimeModule;
import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.jmap.api.change.EmailChange;
import org.apache.james.jmap.api.change.State;
import org.apache.james.jmap.api.model.AccountId;
import org.apache.james.mailbox.cassandra.ids.CassandraMessageId;
import org.apache.james.mailbox.model.MessageId;

import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.UserType;
import com.github.steveash.guavate.Guavate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class EmailChangeRepositoryDAO {
    private final CassandraAsyncExecutor executor;
    private final UserType zonedDateTimeUserType;
    private final PreparedStatement insertStatement;
    private final PreparedStatement selectAllStatement;
    private final PreparedStatement selectFromStatement;
    private final PreparedStatement selectLatestStatement;
    private final PreparedStatement selectLatestNotDelegatedStatement;

    @Inject
    public EmailChangeRepositoryDAO(Session session, CassandraTypesProvider cassandraTypesProvider) {
        executor = new CassandraAsyncExecutor(session);
        zonedDateTimeUserType = cassandraTypesProvider.getDefinedUserType(CassandraZonedDateTimeModule.ZONED_DATE_TIME);

        insertStatement = session.prepare(insertInto(TABLE_NAME)
            .value(ACCOUNT_ID, bindMarker(ACCOUNT_ID))
            .value(STATE, bindMarker(STATE))
            .value(DATE, bindMarker(DATE))
            .value(IS_DELEGATED, bindMarker(IS_DELEGATED))
            .value(CREATED, bindMarker(CREATED))
            .value(UPDATED, bindMarker(UPDATED))
            .value(DESTROYED, bindMarker(DESTROYED)));

        selectAllStatement = session.prepare(select().from(TABLE_NAME)
            .where(eq(ACCOUNT_ID, bindMarker(ACCOUNT_ID)))
            .orderBy(asc(STATE)));

        selectFromStatement = session.prepare(select().from(TABLE_NAME)
            .where(eq(ACCOUNT_ID, bindMarker(ACCOUNT_ID)))
            .and(gte(STATE, bindMarker(STATE)))
            .orderBy(asc(STATE)));

        selectLatestStatement = session.prepare(select(STATE)
            .from(TABLE_NAME)
            .where(eq(ACCOUNT_ID, bindMarker(ACCOUNT_ID)))
            .orderBy(desc(STATE))
            .limit(1));

        selectLatestNotDelegatedStatement = session.prepare(select(STATE)
            .from(TABLE_NAME)
            .where(eq(ACCOUNT_ID, bindMarker(ACCOUNT_ID)))
            .and(eq(IS_DELEGATED, false))
            .orderBy(desc(STATE))
            .limit(1)
            .allowFiltering());
    }

    Mono<Void> insert(EmailChange change) {
        return executor.executeVoid(insertStatement.bind()
            .setString(ACCOUNT_ID, change.getAccountId().getIdentifier())
            .setUUID(STATE, change.getState().getValue())
            .setBool(IS_DELEGATED, change.isDelegated())
            .setSet(CREATED, toUuidSet(change.getCreated()), UUID.class)
            .setSet(UPDATED, toUuidSet(change.getUpdated()), UUID.class)
            .setSet(DESTROYED, toUuidSet(change.getDestroyed()), UUID.class)
            .setUDTValue(DATE, CassandraZonedDateTimeModule.toUDT(zonedDateTimeUserType, change.getDate())));
    }

    private ImmutableSet<UUID> toUuidSet(List<MessageId> idSet) {
        return idSet.stream()
            .filter(CassandraMessageId.class::isInstance)
            .map(CassandraMessageId.class::cast)
            .map(CassandraMessageId::get)
            .collect(Guavate.toImmutableSet());
    }

    Flux<EmailChange> getAllChanges(AccountId accountId) {
        return executor.executeRows(selectAllStatement.bind()
            .setString(ACCOUNT_ID, accountId.getIdentifier()))
            .map(this::readRow);
    }

    Flux<EmailChange> getChangesSince(AccountId accountId, State state) {
        return executor.executeRows(selectFromStatement.bind()
            .setString(ACCOUNT_ID, accountId.getIdentifier())
            .setUUID(STATE, state.getValue()))
            .map(this::readRow);
    }

    Mono<State> latestState(AccountId accountId) {
        return executor.executeSingleRow(selectLatestStatement.bind()
            .setString(ACCOUNT_ID, accountId.getIdentifier()))
            .map(row -> State.of(row.getUUID(STATE)));
    }

    Mono<State> latestStateNotDelegated(AccountId accountId) {
        return executor.executeSingleRow(selectLatestNotDelegatedStatement.bind()
            .setString(ACCOUNT_ID, accountId.getIdentifier()))
            .map(row -> State.of(row.getUUID(STATE)));
    }

    private EmailChange readRow(Row row) {
        return EmailChange.builder()
            .accountId(AccountId.fromString(row.getString(ACCOUNT_ID)))
            .state(State.of(row.getUUID(STATE)))
            .date(CassandraZonedDateTimeModule.fromUDT(row.getUDTValue(DATE)))
            .isDelegated(row.getBool(IS_DELEGATED))
            .created(toIdSet(row.getSet(CREATED, UUID.class)))
            .updated(toIdSet(row.getSet(UPDATED, UUID.class)))
            .destroyed(toIdSet(row.getSet(DESTROYED, UUID.class)))
            .build();
    }

    private ImmutableList<MessageId> toIdSet(Set<UUID> uuidSet) {
        return uuidSet.stream()
            .map(CassandraMessageId.Factory::of)
            .collect(Guavate.toImmutableList());
    }
}
