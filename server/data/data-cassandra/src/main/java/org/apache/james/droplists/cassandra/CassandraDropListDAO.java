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
import static org.apache.james.droplists.api.DeniedEntityType.DOMAIN;
import static org.apache.james.droplists.cassandra.tables.CassandraDropListTable.DENIED_ENTITY;
import static org.apache.james.droplists.cassandra.tables.CassandraDropListTable.DENIED_ENTITY_TYPE;
import static org.apache.james.droplists.cassandra.tables.CassandraDropListTable.OWNER;
import static org.apache.james.droplists.cassandra.tables.CassandraDropListTable.OWNER_SCOPE;
import static org.apache.james.droplists.cassandra.tables.CassandraDropListTable.TABLE_NAME;

import java.util.List;

import jakarta.inject.Inject;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraDropListDAO {

    private final CassandraAsyncExecutor executor;
    private final PreparedStatement addDropListStatement;
    private final PreparedStatement removeDropListStatement;
    private final PreparedStatement getDropListStatement;
    private final PreparedStatement queryDropListStatement;
    private final PreparedStatement queryGlobalDropListStatement;

    @Inject
    public CassandraDropListDAO(CqlSession session) {
        this.executor = new CassandraAsyncExecutor(session);

        addDropListStatement = session.prepare(insertInto(TABLE_NAME)
            .value(OWNER_SCOPE, bindMarker(OWNER_SCOPE))
            .value(OWNER, bindMarker(OWNER))
            .value(DENIED_ENTITY, bindMarker(DENIED_ENTITY))
            .value(DENIED_ENTITY_TYPE, bindMarker(DENIED_ENTITY_TYPE))
            .ifNotExists()
            .build());

        removeDropListStatement = session.prepare(deleteFrom(TABLE_NAME)
            .where(column(OWNER_SCOPE).isEqualTo(bindMarker(OWNER_SCOPE)),
                column(OWNER).isEqualTo(bindMarker(OWNER)),
                column(DENIED_ENTITY).isEqualTo(bindMarker(DENIED_ENTITY)))
            .ifExists()
            .build());

        getDropListStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(OWNER_SCOPE).isEqualTo(bindMarker(OWNER_SCOPE)),
                column(OWNER).isEqualTo(bindMarker(OWNER)))
            .allowFiltering()
            .build());

        queryDropListStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(OWNER_SCOPE).isEqualTo(bindMarker(OWNER_SCOPE)),
                column(OWNER).isEqualTo(bindMarker(OWNER)),
                column(DENIED_ENTITY).in(bindMarker(DENIED_ENTITY)))
            .build());

        queryGlobalDropListStatement = session.prepare(selectFrom(TABLE_NAME)
            .all()
            .where(column(OWNER_SCOPE).isEqualTo(bindMarker(OWNER_SCOPE)),
                column(DENIED_ENTITY).in(bindMarker(DENIED_ENTITY)))
            .allowFiltering()
            .build());
    }

    public Mono<Void> addDropList(DropListEntry dropListEntry) {
        return executor.executeVoid(
            addDropListStatement.bind()
                .setString(OWNER_SCOPE, dropListEntry.getOwnerScope().name())
                .setString(OWNER, dropListEntry.getOwner())
                .setString(DENIED_ENTITY, dropListEntry.getDeniedEntity())
                .setString(DENIED_ENTITY_TYPE, dropListEntry.getDeniedEntityType().name()));
    }

    public Mono<Void> removeDropList(DropListEntry dropListEntry) {
        return executor.executeVoid(
            removeDropListStatement.bind()
                .setString(OWNER_SCOPE, dropListEntry.getOwnerScope().name())
                .setString(OWNER, dropListEntry.getOwner())
                .setString(DENIED_ENTITY, dropListEntry.getDeniedEntity()));
    }

    public Mono<DropList.Status> queryDropList(OwnerScope ownerScope, String owner, MailAddress sender) {
        if (ownerScope.equals(OwnerScope.GLOBAL)) {
            return executor.executeReturnExists(
                    queryGlobalDropListStatement.bind()
                        .setString(OWNER_SCOPE, ownerScope.name())
                        .setList(DENIED_ENTITY, List.of(sender.asString(), sender.getDomain().asString()), String.class))
                .map(isExist -> Boolean.TRUE.equals(isExist) ? DropList.Status.BLOCKED : DropList.Status.ALLOWED);
        } else {
            return executor.executeReturnExists(
                    queryDropListStatement.bind()
                        .setString(OWNER_SCOPE, ownerScope.name())
                        .setString(OWNER, owner)
                        .setList(DENIED_ENTITY, List.of(sender.asString(), sender.getDomain().asString()), String.class))
                .map(isExist -> Boolean.TRUE.equals(isExist) ? DropList.Status.BLOCKED : DropList.Status.ALLOWED);
        }
    }

    public Flux<DropListEntry> getDropList(OwnerScope ownerScope, String owner) {
        return executor.executeRows(getDropListStatement.bind()
                .setString(OWNER_SCOPE, ownerScope.name())
                .setString(OWNER, owner))
            .map(row -> mapRowToDropListEntry(ownerScope, row));
    }

    private static DropListEntry mapRowToDropListEntry(OwnerScope ownerScope, Row row) {
        String deniedEntity = row.getString(DENIED_ENTITY);
        String deniedEntityType = row.getString(DENIED_ENTITY_TYPE);
        try {
            DropListEntry.Builder builder = DropListEntry.builder();
            switch (ownerScope) {
                case USER -> builder.userOwner(new MailAddress(row.getString(OWNER)));
                case DOMAIN -> builder.domainOwner(Domain.of(row.getString(OWNER)));
                case GLOBAL -> builder.forAll();
            }
            if (DOMAIN.name().equals(deniedEntityType)) {
                builder.denyDomain(Domain.of(deniedEntity));
            } else {
                builder.denyAddress(new MailAddress(deniedEntity));
            }
            return builder.build();
        } catch (AddressException e) {
            throw new IllegalArgumentException("Entity could not be parsed as a MailAddress", e);
        }
    }
}