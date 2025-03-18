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

package org.apache.james.droplists.postgres;

import static org.apache.james.backends.postgres.utils.PostgresExecutor.DEFAULT_INJECT;
import static org.apache.james.droplists.api.DeniedEntityType.DOMAIN;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.DENIED_ENTITY;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.DENIED_ENTITY_TYPE;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.DROPLIST_ID;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.OWNER;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.OWNER_SCOPE;
import static org.apache.james.droplists.postgres.PostgresDropListDataDefinition.PostgresDropListsTable.TABLE_NAME;

import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.mail.internet.AddressException;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.core.Domain;
import org.apache.james.core.MailAddress;
import org.apache.james.droplists.api.DropList;
import org.apache.james.droplists.api.DropListEntry;
import org.apache.james.droplists.api.OwnerScope;
import org.jooq.Record;

import com.google.common.base.Preconditions;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresDropList implements DropList {
    private final PostgresExecutor postgresExecutor;

    @Inject
    public PostgresDropList(@Named(DEFAULT_INJECT) PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    @Override
    public Mono<Void> add(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        String specifiedOwner = entry.getOwnerScope().equals(OwnerScope.GLOBAL) ? "" : entry.getOwner();
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, DROPLIST_ID, OWNER_SCOPE, OWNER, DENIED_ENTITY_TYPE, DENIED_ENTITY)
                .values(UUID.randomUUID(),
                    entry.getOwnerScope().name(),
                    specifiedOwner,
                    entry.getDeniedEntityType().name(),
                    entry.getDeniedEntity())
            )
        );
    }

    @Override
    public Mono<Void> remove(DropListEntry entry) {
        Preconditions.checkArgument(entry != null);
        return postgresExecutor.executeVoid(dsl -> Mono.from(dsl.deleteFrom(TABLE_NAME)
            .where(OWNER_SCOPE.eq(entry.getOwnerScope().name()))
            .and(OWNER.eq(entry.getOwner()))
            .and(DENIED_ENTITY.eq(entry.getDeniedEntity()))));
    }

    @Override
    public Flux<DropListEntry> list(OwnerScope ownerScope, String owner) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        return postgresExecutor.executeRows(dsl -> Flux.from(dsl.selectFrom(TABLE_NAME)
                .where(OWNER_SCOPE.eq(ownerScope.name()))
                .and(OWNER.eq(owner))))
            .map(PostgresDropList::mapRecordToDropListEntry);
    }

    @Override
    public Mono<Status> query(OwnerScope ownerScope, String owner, MailAddress sender) {
        Preconditions.checkArgument(ownerScope != null);
        Preconditions.checkArgument(owner != null);
        Preconditions.checkArgument(sender != null);
        String specifiedOwner = ownerScope.equals(OwnerScope.GLOBAL) ? "" : owner;
        return postgresExecutor.executeExists(dsl -> dsl.selectOne().from(TABLE_NAME)
                .where(OWNER_SCOPE.eq(ownerScope.name()))
                .and(OWNER.eq(specifiedOwner))
                .and(DENIED_ENTITY.in(List.of(sender.asString(), sender.getDomain().asString()))))
            .map(isExist -> Boolean.TRUE.equals(isExist) ? DropList.Status.BLOCKED : DropList.Status.ALLOWED);
    }

    private static DropListEntry mapRecordToDropListEntry(Record dropListRecord) {
        String deniedEntity = dropListRecord.get(DENIED_ENTITY);
        String deniedEntityType = dropListRecord.get(DENIED_ENTITY_TYPE);
        OwnerScope ownerScope = OwnerScope.valueOf(dropListRecord.get(OWNER_SCOPE));
        try {
            DropListEntry.Builder builder = DropListEntry.builder();
            switch (ownerScope) {
                case USER -> builder.userOwner(new MailAddress(dropListRecord.get(OWNER)));
                case DOMAIN -> builder.domainOwner(Domain.of(dropListRecord.get(OWNER)));
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
