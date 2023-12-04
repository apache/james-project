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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.james.backends.postgres.utils.PostgresExecutor;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.postgres.PostgresMailboxId;
import org.jooq.impl.DSL;
import org.jooq.impl.DefaultDataType;
import org.jooq.postgres.extensions.types.Hstore;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class PostgresMailboxAnnotationDAO {
    private static final char SQL_WILDCARD_CHAR = '%';
    private static final String ANNOTATION_KEY_FIELD_NAME = "annotation_key";
    private static final String ANNOTATION_VALUE_FIELD_NAME = "annotation_value";
    private static final String EMPTY_ANNOTATION_VALUE = null;

    private final PostgresExecutor postgresExecutor;

    public PostgresMailboxAnnotationDAO(PostgresExecutor postgresExecutor) {
        this.postgresExecutor = postgresExecutor;
    }

    public Flux<MailboxAnnotation> getAllAnnotations(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.selectFrom(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .singleOrEmpty()
            .map(record -> record.get(ANNOTATIONS, LinkedHashMap.class))
            .flatMapIterable(this::hstoreToAnnotations);
    }

    public Flux<MailboxAnnotation> getAnnotationsByKeys(PostgresMailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.select(DSL.function("slice",
                        DefaultDataType.getDefaultDataType("hstore"),
                        ANNOTATIONS,
                        DSL.array(keys.stream().map(mailboxAnnotationKey -> DSL.val(mailboxAnnotationKey.asString())).collect(Collectors.toUnmodifiableList()))))
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .singleOrEmpty()
            .map(record -> record.get(0, LinkedHashMap.class))
            .flatMapIterable(this::hstoreToAnnotations);
    }

    public Mono<Boolean> exist(PostgresMailboxId mailboxId, MailboxAnnotationKey key) {
        return postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.select(DSL.field(" exist(" + ANNOTATIONS.getName() + ",?)", key.asString()))
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .singleOrEmpty()
            .map(record -> record.get(0, Boolean.class))
            .defaultIfEmpty(false);
    }

    public Flux<MailboxAnnotation> getAnnotationsByKeyLike(PostgresMailboxId mailboxId, MailboxAnnotationKey key) {
        return postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.selectFrom(
                    dslContext.select(DSL.field("(each(annotations)).key").as(ANNOTATION_KEY_FIELD_NAME),
                            DSL.field("(each(annotations)).value").as(ANNOTATION_VALUE_FIELD_NAME))
                        .from(TABLE_NAME)
                        .where(MAILBOX_ID.eq(mailboxId.asUuid())).asTable())
                    .where(DSL.field(ANNOTATION_KEY_FIELD_NAME).like(key.asString() + SQL_WILDCARD_CHAR))))
            .map(record -> MailboxAnnotation.newInstance(new MailboxAnnotationKey(record.get(ANNOTATION_KEY_FIELD_NAME, String.class)),
                record.get(ANNOTATION_VALUE_FIELD_NAME, String.class)));
    }

    public Mono<Void> insertAnnotation(PostgresMailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());

        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.insertInto(TABLE_NAME, MAILBOX_ID, ANNOTATIONS)
                .values(mailboxId.asUuid(), annotationAsHstore(mailboxAnnotation))
                .onConflict(MAILBOX_ID)
                .doUpdate()
                .set(DSL.field(ANNOTATIONS.getName() + "[?]",
                    mailboxAnnotation.getKey().asString()),
                    mailboxAnnotation.getValue().orElse(EMPTY_ANNOTATION_VALUE))));
    }

    public Mono<Void> deleteAnnotation(PostgresMailboxId mailboxId, MailboxAnnotationKey key) {
        return postgresExecutor.executeVoid(dslContext ->
            Mono.from(dslContext.update(TABLE_NAME)
                .set(DSL.field(ANNOTATIONS.getName()),
                    (Object) DSL.function("delete",
                        DefaultDataType.getDefaultDataType("hstore"),
                        ANNOTATIONS,
                        DSL.val(key.asString())))
                .where(MAILBOX_ID.eq(mailboxId.asUuid()))));
    }

    public Mono<Integer> countAnnotations(PostgresMailboxId mailboxId) {
        return postgresExecutor.executeRows(dslContext ->
                Flux.from(dslContext.select(DSL.field("array_length(akeys(" + ANNOTATIONS.getName() + "), 1)"))
                    .from(TABLE_NAME)
                    .where(MAILBOX_ID.eq(mailboxId.asUuid()))))
            .singleOrEmpty()
            .flatMap(record -> Mono.justOrEmpty(record.get(0, Integer.class)))
            .defaultIfEmpty(0);
    }

    private List<MailboxAnnotation> hstoreToAnnotations(LinkedHashMap<String, String> hstore) {
        return hstore.entrySet()
            .stream()
            .map(entry -> MailboxAnnotation.newInstance(new MailboxAnnotationKey(entry.getKey()), entry.getValue()))
            .collect(Collectors.toList());
    }

    private Hstore annotationAsHstore(MailboxAnnotation mailboxAnnotation) {
        return Hstore.hstore(ImmutableMap.of(mailboxAnnotation.getKey().asString(), mailboxAnnotation.getValue().get()));
    }
}
