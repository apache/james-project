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
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.github.steveash.guavate.Guavate;
import com.google.common.base.Ascii;
import org.apache.james.backends.cassandra.utils.CassandraUtils;
import org.apache.james.mailbox.cassandra.CassandraId;
import org.apache.james.mailbox.cassandra.table.CassandraAnnotationTable;
import org.apache.james.mailbox.model.MailboxAnnotation;
import org.apache.james.mailbox.model.MailboxAnnotationKey;
import org.apache.james.mailbox.store.mail.AnnotationMapper;
import org.apache.james.mailbox.store.transaction.NonTransactionalMapper;

import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.querybuilder.Select;
import com.google.common.base.Preconditions;

public class CassandraAnnotationMapper extends NonTransactionalMapper implements AnnotationMapper {

    private final CassandraId mailboxId;
    private final Session session;

    public CassandraAnnotationMapper(CassandraId mailboxId, Session session) {
        this.mailboxId = mailboxId;
        this.session = session;
    }

    public List<MailboxAnnotation> getAllAnnotations() {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQuery()))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    public List<MailboxAnnotation> getAnnotationsByKeys(Set<MailboxAnnotationKey> keys) {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryForKeys(keys)))
            .map(this::toAnnotation)
            .collect(Collectors.toList());
    }

    public List<MailboxAnnotation> getAnnotationsByKeysWithOneDepth(Set<MailboxAnnotationKey> keys) {
        return keys.stream()
            .flatMap(this::getAnnotationsByKeyWithOneDepth)
            .collect(Guavate.toImmutableList());
    }

    public List<MailboxAnnotation> getAnnotationsByKeysWithAllDepth(Set<MailboxAnnotationKey> keys) {
        return keys.stream()
            .flatMap(this::getAnnotationsByKeyWithAllDepth)
            .collect(Guavate.toImmutableList());
    }

    public void deleteAnnotation(MailboxAnnotationKey key) {
        session.execute(delete().from(CassandraAnnotationTable.TABLE_NAME)
            .where(eq(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid()))
            .and(eq(CassandraAnnotationTable.KEY, key.asString())));
    }

    public void insertAnnotation(MailboxAnnotation mailboxAnnotation) {
        Preconditions.checkArgument(!mailboxAnnotation.isNil());
        session.execute(insertInto(CassandraAnnotationTable.TABLE_NAME)
            .value(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid())
            .value(CassandraAnnotationTable.KEY, mailboxAnnotation.getKey().asString())
            .value(CassandraAnnotationTable.VALUE, mailboxAnnotation.getValue().get()));
    }

    private MailboxAnnotation toAnnotation(Row row) {
        return MailboxAnnotation.newInstance(new MailboxAnnotationKey(row.getString(CassandraAnnotationTable.KEY)),
            row.getString(CassandraAnnotationTable.VALUE));
    }

    private Select.Where getStoredAnnotationsQuery() {
        return select(CassandraAnnotationTable.SELECT_FIELDS)
            .from(CassandraAnnotationTable.TABLE_NAME)
            .where(eq(CassandraAnnotationTable.MAILBOX_ID, mailboxId.asUuid()));
    }

    private Select.Where getStoredAnnotationsQueryForKeys(Set<MailboxAnnotationKey> keys) {
        return getStoredAnnotationsQuery().and(in(CassandraAnnotationTable.KEY, keys.stream()
            .map(MailboxAnnotationKey::asString)
            .collect(Guavate.toImmutableList())));
    }

    private Select.Where getStoredAnnotationsQueryLikeKey(String key) {
        return getStoredAnnotationsQuery()
            .and(gte(CassandraAnnotationTable.KEY, key))
            .and(lte(CassandraAnnotationTable.KEY, buildNextKey(key)));
    }
    
    private String buildNextKey(String key) {
        return key + MailboxAnnotationKey.SLASH_CHARACTER + Ascii.MAX;
    }

    private Stream<MailboxAnnotation> getAnnotationsByKeyWithAllDepth(MailboxAnnotationKey key) {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryLikeKey(key.asString())))
            .map(this::toAnnotation);
    }

    private Stream<MailboxAnnotation> getAnnotationsByKeyWithOneDepth(MailboxAnnotationKey key) {
        return CassandraUtils.convertToStream(session.execute(getStoredAnnotationsQueryLikeKey(key.asString())))
            .map(this::toAnnotation)
            .filter(annotation -> isChild(key, annotation));
    }

    private boolean isChild(MailboxAnnotationKey key, MailboxAnnotation annotation) {
        return annotation.getKey().countComponents() <= key.countComponents() + 1;
    }

    @Override
    public void endRequest() {
    }
}
