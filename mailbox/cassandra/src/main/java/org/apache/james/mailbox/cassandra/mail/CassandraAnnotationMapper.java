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

import static com.datastax.driver.core.querybuilder.QueryBuilder.delete;
import static com.datastax.driver.core.querybuilder.QueryBuilder.eq;
import static com.datastax.driver.core.querybuilder.QueryBuilder.gte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.in;
import static com.datastax.driver.core.querybuilder.QueryBuilder.insertInto;
import static com.datastax.driver.core.querybuilder.QueryBuilder.lte;
import static com.datastax.driver.core.querybuilder.QueryBuilder.select;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.inject.Inject;

import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.ids.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraAnnotationTable;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.model.MailboxId;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;
import com.github.steveash.guavate.Guavate;
import com.google.common.base.Ascii;
import com.google.common.base.Preconditions;

public class CassandraAnnotationMapper extends NonTransactionalMapper implements AnnotationMapper {

    private final Session session;
    private final CassandraUtils cassandraUtils;

    @Inject
    public CassandraAnnotationMapper(Session session, CassandraUtils cassandraUtils) {
        this.session = session;
        this.cassandraUtils = cassandraUtils;
    }

    @Override
    public List<MailboxAnnotation> getAllAnnotations(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        return cassandraUtils.convertToStream(session.execute(getStoredAnnotationsQuery(cassandraId)))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeys(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        return cassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryForKeys(cassandraId, keys)))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        return keys.stream()
            .flatMap(annotation -> getAnnotationsByKeyWithOneDepth(cassandraId, annotation))
            .collect(Guavate.toImmutableList());
    }

    @Override
    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(MailboxId mailboxId, Set<MailboxAnnotationKey> keys) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        return keys.stream()
            .flatMap(annotation -> getAnnotationsByKeyWithAllDepth(cassandraId, annotation))
            .collect(Guavate.toImmutableList());
    }

    @Override
    public void deleteAnnotation(MailboxId mailboxId, MailboxAnnotationKey key) {
        session.execute(delete().from(CassandraAnnotationTable.TABLE_NAME)
            .where(eq(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid()))
            .and(eq(CassandraAnnotationTable.KEY, key.asString())));
    }

    @Override
    public void insertAnnotation(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());
        session.execute(insertInto(CassandraAnnotationTable.TABLE_NAME)
            .value(CassandraAnnotationTable.MAILBOX_ID, ((CassandraId) mailboxId).asUuid())
            .value(CassandraAnnotationTable.KEY, mailboxAnnotation.getKey().asString())
            .value(CassandraAnnotationTable.VALUE, mailboxAnnotation.getValue().get()));
    }

    @Override
    public boolean exist(MailboxId mailboxId, MailboxAnnotation mailboxAnnotation) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        Optional<Row> row = Optional.ofNullable(
            session.execute(
                getStoredAnnotationsQueryByKey(cassandraId,
                    mailboxAnnotation.getKey().asString()))
                .one());
        return row.isPresent();
    }

    @Override
    public int countAnnotations(MailboxId mailboxId) {
        CassandraId cassandraId = (CassandraId)mailboxId;
        return session.execute(getStoredAnnotationsQuery(cassandraId)).getAvailableWithoutFetching();
    }

    private MailboxAnnotation toAnnotation(Row row) {
        return MailboxAnnotation.newInstance(new MailboxAnnotationKey(row.getString(CassandraAnnotationTable.KEY)),
            row.getString(CassandraAnnotationTable.VALUE));
    }

    private Select.Where getStoredAnnotationsQuery(CassandraId mailboxId) {
        return select(CassandraAnnotationTable.SELECT_FIELDS)
            .from(CassandraAnnotationTable.TABLE_NAME)
            .where(eq(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid()));
    }

    private Select.Where getStoredAnnotationsQueryForKeys(CassandraId mailboxId, Set<MailboxAnnotationKey> keys) {
        return getStoredAnnotationsQuery(mailboxId).and(in(CassandraAnnotationTable.KEY, keys.stream()
            .map(MailboxAnnotationKey::asString)
            .collect(Guavate.toImmutableList())));
    }

    private Select.Where getStoredAnnotationsQueryLikeKey(CassandraId mailboxId, String key) {
        return getStoredAnnotationsQuery(mailboxId)
            .and(gte(CassandraAnnotationTable.KEY, key))
            .and(lte(CassandraAnnotationTable.KEY, buildNextKey(key)));
    }

    private Select.Where getStoredAnnotationsQueryByKey(CassandraId mailboxId, String key) {
        return getStoredAnnotationsQuery(mailboxId)
            .and(eq(CassandraAnnotationTable.KEY, key));
    }

    private String buildNextKey(String key) {
        return key + MailboxAnnotationKey.SLASH_CHARACTER + Ascii.MAX;
    }

    private Stream<MailboxAnnotation> getAnnotationsByKeyWithAllDepth(CassandraId mailboxId, MailboxAnnotationKey key) {
        return cassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryLikeKey(mailboxId, key.asString())))
            .map(this::toAnnotation)
            .filter(annotation -> key.isAncestorOrIsEqual(annotation.getKey()));
    }

    private Stream<MailboxAnnotation> getAnnotationsByKeyWithOneDepth(CassandraId mailboxId, MailboxAnnotationKey key) {
        return cassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryLikeKey(mailboxId, key.asString())))
            .map(this::toAnnotation)
            .filter(annotation -> key.isParentOrIsEqual(annotation.getKey()));
    }
}
