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

package org.apache.james.mailbox.cassandra.mail;

import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.bindMarker;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.deleteFrom;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.insertInto;
import static com.datastax.oss.driver.api.querybuilder.QueryBuilder.selectFrom;
import static com.datastax.oss.driver.api.querybuilder.relation.Relation.column;

import java.util.List;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraAsyncExecutor;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraAnnotationTable;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

import com.datastax.oss.driver.api.core.CqlSession;
import com.datastax.oss.driver.api.core.cql.PreparedStatement;
import com.datastax.oss.driver.api.core.cql.Row;
import com.datastax.oss.driver.api.querybuilder.select.Select;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public class CassandraAnnotationMapper extends NonTransactionalMapper implements AnnotationMapper {

    private final CassandraAsyncExecutor asyncExecutor;
    private final PreparedStatement delete;
    private final PreparedStatement insert;
    private final PreparedStatement getStoredAnnotationsQuery;
    private final PreparedStatement countStoredAnnotationsQuery;
    private final PreparedStatement getStoredAnnotationsQueryForKeys;
    private final PreparedStatement getStoredAnnotationsQueryLikeKey;
    private final PreparedStatement getStoredAnnotationsQueryByKey;

    @Inject
    public CassandraAnnotationMapper(CqlSession session) {
        this.asyncExecutor = new CassandraAsyncExecutor(session);
        this.delete = session.prepare(deleteFrom(CassandraAnnotationTable.TABLE_NAME)
            .where(column(CassandraAnnotationTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraAnnotationTable.MAILBOX_ID)),
                column(CassandraAnnotationTable.KEY).isEqualTo(bindMarker(CassandraAnnotationTable.KEY)))
            .build());

        this.insert = session.prepare(insertInto(CassandraAnnotationTable.TABLE_NAME)
            .value(CassandraAnnotationTable.MAILBOX_ID, bindMarker(CassandraAnnotationTable.MAILBOX_ID))
            .value(CassandraAnnotationTable.KEY, bindMarker(CassandraAnnotationTable.KEY))
            .value(CassandraAnnotationTable.VALUE, bindMarker(CassandraAnnotationTable.VALUE))
            .build());

        this.getStoredAnnotationsQuery = session.prepare(getStoredAnnotationsQuery().build());

        this.countStoredAnnotationsQuery = session.prepare(selectFrom(CassandraAnnotationTable.TABLE_NAME)
            .countAll()
            .where(column(CassandraAnnotationTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraAnnotationTable.MAILBOX_ID)))
            .build());

        this.getStoredAnnotationsQueryForKeys =
            session.prepare(getStoredAnnotationsQuery()
                .where(column(CassandraAnnotationTable.KEY).in(bindMarker(CassandraAnnotationTable.KEY)))
                .build());

        this.getStoredAnnotationsQueryLikeKey =
            session.prepare(getStoredAnnotationsQuery()
                .where(column(CassandraAnnotationTable.KEY)
                        .isGreaterThanOrEqualTo(bindMarker(CassandraAnnotationTable.GREATER_BIND_KEY)),
                    column(CassandraAnnotationTable.KEY)
                        .isLessThanOrEqualTo(bindMarker(CassandraAnnotationTable.LESSER_BIND_KEY)))
                .build());

        this.getStoredAnnotationsQueryByKey =
            session.prepare(getStoredAnnotationsQuery()
                .where(column(CassandraAnnotationTable.KEY)
                    .isEqualTo(bindMarker(CassandraAnnotationTable.KEY)))
                .build());
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId) {
        return getAllAnnotationsReactive(mailboxId)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAllAnnotationsReactive(MailboxId mailboxId) {
        return asyncExecutor.executeRows(getStoredAnnotationsQuery.bind()
                .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid()))
            .map(this::toAnnotation);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysReactive(mailboxId, keys)
            .collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return asyncExecutor.executeRows(getStoredAnnotationsQueryForKeys.bind()
                .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid())
                .setList(CassandraAnnotationTable.KEY, keys.stream()
                    .map(MailboxAnnotationKey::asString)
                    .collect(ImmutableList.toImmutableList()), String.class))
            .map(this::toAnnotation);
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysWithOneDepthReactive(mailboxId, keys).collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithOneDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(keys)
            .flatMap(annotation -> getAnnotationsByKeyWithOneDepth((CassandraId) mailboxId, annotation));
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getAnnotationsByKeysWithAllDepthReactive(mailboxId, keys).collectList()
            .block();
    }

    @Override
    public Flux<MailboxAnnotation> getAnnotationsByKeysWithAllDepthReactive(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        return Flux.fromIterable(keys)
            .flatMap(annotation -> getAnnotationsByKeyWithAllDepth((CassandraId) mailboxId, annotation));
    }

    @Override
    public void deleteAnnotation(MailboxId mailboxId, MailboxAnnotationKey key) {
        deleteAnnotationReactive(mailboxId, key).block();
    }

    @Override
    public Mono<Void> deleteAnnotationReactive(MailboxId mailboxId, MailboxAnnotationKey key) {
        return asyncExecutor.executeVoid(delete.bind()
            .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid())
            .setString(CassandraAnnotationTable.KEY, key.asString()));
    }

    @Override
    public void insertAnnotation(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        insertAnnotationReactive(mailboxId, mailboxAnnotation).block();
    }

    @Override
    public Mono<Void> insertAnnotationReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());
        return asyncExecutor.executeVoid(insert.bind()
            .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid())
            .setString(CassandraAnnotationTable.KEY, mailboxAnnotation.getKey().asString())
            .setString(CassandraAnnotationTable.VALUE, mailboxAnnotation.getValue().get()));
    }

    @Override
    public boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return existReactive(mailboxId, mailboxAnnotation).block();
    }

    @Override
    public Mono<Boolean> existReactive(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        return asyncExecutor.executeReturnExists(getStoredAnnotationsQueryByKey.bind()
            .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid())
            .setString(CassandraAnnotationTable.KEY, mailboxAnnotation.getKey().asString()));
    }

    @Override
    public int countAnnotations(MailboxId mailboxId) {
        return countAnnotationsReactive(mailboxId).block();
    }

    public Mono<Integer> countAnnotationsReactive(MailboxId mailboxId) {
        return asyncExecutor.executeSingleRow(countStoredAnnotationsQuery.bind()
                .setUuid(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid()))
            .map(row -> Ints.checkedCast(row.getLong(0)));
    }

    private MailboxAnnotation toAnnotation(Row row) {
        return MailboxAnnotation.newInstance(new MailboxAnnotationKey(row.getString(CassandraAnnotationTable.KEY)),
            row.getString(CassandraAnnotationTable.VALUE));
    }

    private Select getStoredAnnotationsQuery() {
        return selectFrom(CassandraAnnotationTable.TABLE_NAME)
            .columns(CassandraAnnotationTable.SELECT_FIELDS)
            .where(column(CassandraAnnotationTable.MAILBOX_ID).isEqualTo(bindMarker(CassandraAnnotationTable.MAILBOX_ID)));
    }

    private String buildNextKey(String key) {
        return key + MailboxAnnotationKey.SLASH_CHARACTER + Ascii.MAX;
    }

    private Flux<MailboxAnnotation> getAnnotationsByKeyWithAllDepth(CassandraId mailboxId, MailboxAnnotationKey key) {
        return asyncExecutor.executeRows(getStoredAnnotationsQueryLikeKey.bind()
                .setUuid(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid())
                .setString(CassandraAnnotationTable.GREATER_BIND_KEY, key.asString())
                .setString(CassandraAnnotationTable.LESSER_BIND_KEY, buildNextKey(key.asString())))
            .map(this::toAnnotation)
            .filter(annotation -> key.isAncestorOrIsEqual(annotation.getKey()));
    }

    private Flux<MailboxAnnotation> getAnnotationsByKeyWithOneDepth(CassandraId mailboxId, MailboxAnnotationKey key) {
        return asyncExecutor.executeRows(getStoredAnnotationsQueryLikeKey.bind()
                .setUuid(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid())
                .setString(CassandraAnnotationTable.GREATER_BIND_KEY, key.asString())
                .setString(CassandraAnnotationTable.LESSER_BIND_KEY, buildNextKey(key.asString())))
            .map(this::toAnnotation)
            .filter(annotation -> key.isParentOrIsEqual(annotation.getKey()));
    }
}
