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

package org.apache.james.mailbox.postgres.mail.dao;

import static org.apache.james.mailbox.postgres.PostgresMailboxAnnotationModule.PostgresMailboxAnnotationTable.ANNOTATIONS;
import static org.apache.james.mailbox.postgres.PostgresMailboxAnnotationModule.PostgresMailboxAnnotationTable.MAILBOX_ID;
import static org.apache.james.mailbox.postgres.PostgresMailboxAnnotationModule.PostgresMailboxAnnotationTable.TABLE_NAME;

import java.util.List;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxAnnotationDAO {
    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxAnnotationDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<MailboxAnnotation> getAllAnnotations(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext ->
            Flux.from(dslContext.selectFrom(TABLE_NAME)
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .singleOrEmpty()
            .map(record -> record.get(ANNOTATIONS))
            .flatMapIterable(this::hstoreToAnnotations);
    }

    private List<MailboxAnnotation> hstoreToAnnotations(Hstore hstore) {
        return hstore.data()
            .entrySet()
            .stream()
            .map(entry -> MailboxAnnotation.newInstance(new MailboxAnnotationKey(entry.getKey()), entry.getValue()))
            .collect(Collectors.toList());
    }

    public Mono<Void> insertAnnotation(PostgresMailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());

        // TODO: likely wrong... might need to check if row exists with mailboxId and then insert if not, update if is
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, MAILBOX_ID, ANNOTATIONS)
                .values(mailboxId.asUuid(), annotationAsHstore(mailboxAnnotation))));
    }

    private Hstore annotationAsHstore(MailboxAnnotation mailboxAnnotation) {
        return Hstore.hstore(ImmutableMap.of(mailboxAnnotation.getKey().asString(), mailboxAnnotation.getValue().get()));
    }
}
