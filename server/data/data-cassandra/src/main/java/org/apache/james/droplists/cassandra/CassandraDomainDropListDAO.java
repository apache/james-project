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
package org.apache.james.droplists.cassandra;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;
import static org.apache.james.droplists.cassandra.tables.CassandraDomainDropListTable.DENIED_ENTITY;
import static org.apache.james.droplists.cassandra.tables.CassandraDomainDropListTable.DENIED_ENTITY_TYPE;
import static org.apache.james.droplists.cassandra.tables.CassandraDomainDropListTable.OWNER;
import static org.apache.james.droplists.cassandra.tables.CassandraDomainDropListTable.TABLE_NAME;

import java.util.List;
import java.util.Objects;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DeniedEntityType;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.cassandra.tables.CassandraUserDropListTable;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDomainDropListDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement addDropListStatement;
    private final PreparedStatement removeDropListStatement;
    private final PreparedStatement getDropListStatement;
    private final PreparedStatement queryDropListStatement;

    @Inject
    public CassandraDomainDropListDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        addDropListStatement = session.prepare(insertInto(TABLE_NAME)
            .value(OWNER, bindMarker(OWNER))
            .value(DENIED_ENTITY, bindMarker(DENIED_ENTITY))
            .value(DENIED_ENTITY_TYPE, bindMarker(DENIED_ENTITY_TYPE))
            .ifNotExists()
            .build());

        removeDropListStatement = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(OWNER).isEqualTo(bindMarker(OWNER)),
                column(DENIED_ENTITY).isEqualTo(bindMarker(DENIED_ENTITY)))
            .ifExists()
            .build());

        getDropListStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .whereColumn(OWNER).isEqualTo(bindMarker(OWNER))
            .allowFiltering()
            .build());

        queryDropListStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(OWNER).isEqualTo(bindMarker(OWNER)),
                column(DENIED_ENTITY).in(bindMarker(DENIED_ENTITY)))
            .build());
    }

    public Mono<Void> addDropList(DropListEntry dropListEntry) {
        return executor.executeVoid(
            addDropListStatement.bind()
                .setString(OWNER, dropListEntry.getOwner())
                .setString(DENIED_ENTITY, dropListEntry.getDeniedEntity())
                .setString(DENIED_ENTITY_TYPE, dropListEntry.getDeniedEntityType().toString()));
    }

    public Mono<Void> removeDropList(DropListEntry dropListEntry) {
        return executor.executeVoid(
            removeDropListStatement.bind()
                .setString(OWNER, dropListEntry.getOwner())
                .setString(DENIED_ENTITY, dropListEntry.getDeniedEntity()));
    }

    public Mono<DropList.Status> queryDropList(String owner, MailAddress sender) {
        return executor.executeReturnExists(
                queryDropListStatement.bind()
                    .setString(CassandraUserDropListTable.OWNER, owner)
                    .setList(CassandraUserDropListTable.DENIED_ENTITY, List.of(sender.asString(), sender.getDomain().asString()), String.class))
            .map(isExist -> Boolean.TRUE.equals(isExist) ? DropList.Status.BLOCKED : DropList.Status.ALLOWED);
    }

    public Flux<DropListEntry> getDropList(String owner) {
        return executor.executeRows(getDropListStatement.bind()
                .setString(OWNER, owner))
            .map(CassandraDomainDropListDAO::mapRowToDropListEntry);
    }

    private static DropListEntry mapRowToDropListEntry(Row row) {
        DropListEntry.Builder builder = DropListEntry.builder()
            .domainOwner(Domain.of(row.getString(OWNER)));
        if (Objects.equals(row.getString(DENIED_ENTITY_TYPE), DeniedEntityType.DOMAIN.name())) {
            builder.denyDomain(Domain.of(row.getString(DENIED_ENTITY)));
        } else {
            try {
                builder.denyAddress(new MailAddress(row.getString(DENIED_ENTITY)));
            } catch (AddressException e) {
                throw new IllegalArgumentException(row.getString(DENIED_ENTITY) + " could not be parsed", e);
            }
        }
        return builder.build();
    }
}